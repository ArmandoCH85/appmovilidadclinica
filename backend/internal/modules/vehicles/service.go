// Package vehicles expone las posiciones GPS de los vehículos de la flota.
//
// Las credenciales de Traccar (MS Global GPS) viven SOLO acá, en variables de
// entorno del servidor: las apps nunca las ven ni las piden al usuario.
package vehicles

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	// DefaultBaseURL es la API de Traccar de MS Global GPS.
	DefaultBaseURL = "https://gps.msglobalgps.com/api/"

	// staleAfter marca una posición como "última ubicación conocida".
	staleAfter = 5 * time.Minute

	// knotsToKmh: Traccar entrega `speed` en nudos.
	knotsToKmh = 1.852

	// cacheTTL evita golpear Traccar en cada request de cada pasajero.
	defaultCacheTTL = 15 * time.Second
)

// DeviceIDsDefault es el mapeo placa → id de dispositivo en Traccar,
// verificado contra GET /api/devices (CJI-579 = 147; la app y el backend usan
// "C0C-965" con cero y Traccar lo llama "COC-965" con letra O: es el mismo
// vehículo y por eso se resuelve por id, nunca por nombre).
var DeviceIDsDefault = map[string]int64{
	"CJI-579": 147,
	"C0C-965": 142,
}

// Config es la configuración del proxy, leída del entorno.
type Config struct {
	BaseURL   string
	Username  string
	Password  string
	DeviceIDs map[string]int64
	CacheTTL  time.Duration
}

// LoadConfigFromEnv lee TRACCAR_BASE_URL, TRACCAR_USER, TRACCAR_PASSWORD,
// TRACCAR_DEVICE_IDS ("CJI-579=147,C0C-965=142") y TRACCAR_CACHE_TTL.
// Sin usuario/contraseña el servicio queda deshabilitado (503 en el endpoint),
// nunca con credenciales de ejemplo.
func LoadConfigFromEnv() Config {
	cfg := Config{
		BaseURL:   DefaultBaseURL,
		Username:  os.Getenv("TRACCAR_USER"),
		Password:  os.Getenv("TRACCAR_PASSWORD"),
		DeviceIDs: DeviceIDsDefault,
		CacheTTL:  defaultCacheTTL,
	}
	if v := os.Getenv("TRACCAR_BASE_URL"); v != "" {
		cfg.BaseURL = v
	}
	if v := os.Getenv("TRACCAR_DEVICE_IDS"); v != "" {
		if parsed, err := ParseDeviceIDs(v); err == nil {
			cfg.DeviceIDs = parsed
		} else {
			slog.Warn("TRACCAR_DEVICE_IDS inválido, se usa el mapeo por defecto", "error", err)
		}
	}
	if v := os.Getenv("TRACCAR_CACHE_TTL"); v != "" {
		if d, err := time.ParseDuration(v); err == nil && d >= 0 {
			cfg.CacheTTL = d
		}
	}
	return cfg
}

// ParseDeviceIDs parsea "CJI-579=147,C0C-965=142".
func ParseDeviceIDs(raw string) (map[string]int64, error) {
	out := map[string]int64{}
	for _, pair := range strings.Split(raw, ",") {
		pair = strings.TrimSpace(pair)
		if pair == "" {
			continue
		}
		parts := strings.SplitN(pair, "=", 2)
		if len(parts) != 2 {
			return nil, fmt.Errorf("par inválido %q (se espera PLACA=ID)", pair)
		}
		id, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
		if err != nil || id <= 0 {
			return nil, fmt.Errorf("id inválido en %q", pair)
		}
		out[strings.ToUpper(strings.TrimSpace(parts[0]))] = id
	}
	if len(out) == 0 {
		return nil, errors.New("no se indicó ningún vehículo")
	}
	return out, nil
}

// VehiclePosition es lo que consume la app: datos mínimos y sin credenciales.
type VehiclePosition struct {
	Plate       string     `json:"plate"`
	Latitude    *float64   `json:"latitude"`
	Longitude   *float64   `json:"longitude"`
	FixTime     *time.Time `json:"fix_time"`
	ServerTime  *time.Time `json:"server_time"`
	SpeedKmh    *float64   `json:"speed_kmh"`
	Course      *float64   `json:"course"`
	Online      *bool      `json:"online"`
	HasPosition bool       `json:"has_position"`
	IsStale     bool       `json:"is_stale"`
}

// Service consulta Traccar y arma las posiciones, con caché corto.
type Service struct {
	cfg    Config
	client *http.Client
	now    func() time.Time

	mu        sync.Mutex
	cached    []VehiclePosition
	cachedAt  time.Time
	cacheOK   bool
}

// NewService construye el servicio. `httpClient` nil usa uno con timeout.
func NewService(cfg Config, httpClient *http.Client) *Service {
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 12 * time.Second}
	}
	return &Service{cfg: cfg, client: httpClient, now: time.Now}
}

// ErrNotConfigured indica que faltan las credenciales del servidor GPS.
var ErrNotConfigured = errors.New("servicio de GPS no configurado")

// Latest devuelve la última posición conocida de cada vehículo configurado.
// Usa caché de cfg.CacheTTL para no golpear Traccar con cada request.
func (s *Service) Latest(ctx context.Context) ([]VehiclePosition, error) {
	if s.cfg.Username == "" || s.cfg.Password == "" {
		return nil, ErrNotConfigured
	}

	s.mu.Lock()
	if s.cacheOK && s.now().Sub(s.cachedAt) < s.cfg.CacheTTL {
		out := s.cached
		s.mu.Unlock()
		return out, nil
	}
	s.mu.Unlock()

	devices, err := s.fetchDevices(ctx)
	if err != nil {
		return nil, err
	}
	positions, err := s.fetchPositions(ctx)
	if err != nil {
		return nil, err
	}

	result := buildPositions(s.cfg.DeviceIDs, devices, positions, s.now())

	s.mu.Lock()
	s.cached = result
	s.cachedAt = s.now()
	s.cacheOK = true
	s.mu.Unlock()

	return result, nil
}

// ------------------------------------------------------------------ Traccar

type traccarDevice struct {
	ID     int64  `json:"id"`
	Name   string `json:"name"`
	Status string `json:"status"`
}

type traccarPosition struct {
	ID         int64    `json:"id"`
	DeviceID   int64    `json:"deviceId"`
	Latitude   float64  `json:"latitude"`
	Longitude  float64  `json:"longitude"`
	FixTime    string   `json:"fixTime"`
	ServerTime string   `json:"serverTime"`
	Valid      *bool    `json:"valid"`
	Speed      *float64 `json:"speed"`
	Course     *float64 `json:"course"`
}

func (s *Service) fetchDevices(ctx context.Context) ([]traccarDevice, error) {
	var out []traccarDevice
	if err := s.getJSON(ctx, "devices", &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (s *Service) fetchPositions(ctx context.Context) ([]traccarPosition, error) {
	var out []traccarPosition
	if err := s.getJSON(ctx, "positions", &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (s *Service) getJSON(ctx context.Context, path string, target any) error {
	endpoint, err := url.JoinPath(s.cfg.BaseURL, path)
	if err != nil {
		return fmt.Errorf("armando URL de Traccar: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return fmt.Errorf("creando request a Traccar: %w", err)
	}
	req.SetBasicAuth(s.cfg.Username, s.cfg.Password)
	req.Header.Set("Accept", "application/json")

	resp, err := s.client.Do(req)
	if err != nil {
		return fmt.Errorf("consultando Traccar: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		// No se copia el body: puede traer detalles internos del GPS.
		return fmt.Errorf("Traccar respondió %d", resp.StatusCode)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return fmt.Errorf("leyendo respuesta de Traccar: %w", err)
	}
	if err := json.Unmarshal(body, target); err != nil {
		return fmt.Errorf("decodificando respuesta de Traccar: %w", err)
	}
	return nil
}

// buildPositions une devices + positions por id y aplica las reglas del
// dominio. Es una función pura para poder probarla sin red.
func buildPositions(
	tracked map[string]int64,
	devices []traccarDevice,
	positions []traccarPosition,
	now time.Time,
) []VehiclePosition {
	deviceByID := make(map[int64]traccarDevice, len(devices))
	for _, d := range devices {
		deviceByID[d.ID] = d
	}
	latestByDevice := make(map[int64]traccarPosition, len(positions))
	for _, p := range positions {
		current, ok := latestByDevice[p.DeviceID]
		if !ok || p.FixTime > current.FixTime {
			latestByDevice[p.DeviceID] = p
		}
	}

	plates := make([]string, 0, len(tracked))
	for plate := range tracked {
		plates = append(plates, plate)
	}
	// Orden estable para que la respuesta no cambie de orden entre llamadas.
	sortStrings(plates)

	out := make([]VehiclePosition, 0, len(plates))
	for _, plate := range plates {
		deviceID := tracked[plate]
		device, hasDevice := deviceByID[deviceID]
		position, hasPosition := latestByDevice[deviceID]

		item := VehiclePosition{Plate: plate}
		if hasDevice {
			online := strings.EqualFold(device.Status, "online")
			item.Online = &online
		}
		if !hasPosition {
			item.HasPosition = false
			item.IsStale = true
			out = append(out, item)
			continue
		}

		fix := parseTime(position.FixTime)
		server := parseTime(position.ServerTime)

		// Coordenadas válidas y con sentido (nunca 0,0 inventado).
		if validCoords(position.Latitude, position.Longitude) {
			item.Latitude = &position.Latitude
			item.Longitude = &position.Longitude
			item.HasPosition = true
		}
		item.FixTime = fix
		item.ServerTime = server

		if position.Speed != nil && !isNaN(*position.Speed) {
			kmh := *position.Speed * knotsToKmh
			item.SpeedKmh = &kmh
		}
		if position.Course != nil && *position.Course >= 0 && *position.Course <= 360 {
			item.Course = position.Course
		}

		validFix := position.Valid == nil || *position.Valid
		switch {
		case !item.HasPosition || !validFix:
			item.IsStale = true
		case fix == nil:
			item.IsStale = true
		default:
			item.IsStale = now.Sub(*fix) > staleAfter
		}

		out = append(out, item)
	}
	return out
}

func validCoords(lat, lon float64) bool {
	if isNaN(lat) || isNaN(lon) {
		return false
	}
	if lat < -90 || lat > 90 || lon < -180 || lon > 180 {
		return false
	}
	// (0,0) en Traccar suele ser "sin fix": no es una ubicación real.
	return !(lat == 0 && lon == 0)
}

func isNaN(v float64) bool { return v != v }

func parseTime(raw string) *time.Time {
	if raw == "" {
		return nil
	}
	for _, layout := range []string{time.RFC3339Nano, time.RFC3339, "2006-01-02T15:04:05.000Z0700"} {
		if t, err := time.Parse(layout, raw); err == nil {
			utc := t.UTC()
			return &utc
		}
	}
	return nil
}

func sortStrings(items []string) {
	for i := 1; i < len(items); i++ {
		for j := i; j > 0 && items[j] < items[j-1]; j-- {
			items[j], items[j-1] = items[j-1], items[j]
		}
	}
}
