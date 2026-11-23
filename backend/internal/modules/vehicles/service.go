// Package vehicles expone las posiciones GPS de los vehÃƒÆ’Ã‚Â­culos de la flota.
//
// Las credenciales de Traccar (MS Global GPS) viven SOLO acÃƒÆ’Ã‚Â¡, en variables de
// entorno del servidor: las apps nunca las ven ni las piden al usuario.
package vehicles

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math"
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

	// staleAfter marca una posiciÃƒÆ’Ã‚Â³n como "ÃƒÆ’Ã‚Âºltima ubicaciÃƒÆ’Ã‚Â³n conocida".
	staleAfter = 5 * time.Minute

	// knotsToKmh: Traccar entrega `speed` en nudos.
	knotsToKmh = 1.852

	// cacheTTL evita golpear Traccar en cada request de cada pasajero.
	// cacheTTL evita golpear Traccar en cada request de cada pasajero.
	defaultCacheTTL = 15 * time.Second

	// trailWindow es cuÃƒÆ’Ã‚Â¡ntas horas de recorrido se muestran en el mapa.
	trailWindow = 2 * time.Hour

	// maxTrailPoints acota la polilínea para no mandar cientos de puntos.
	maxTrailPoints = 400

	// maxSpeedMps descarta saltos imposibles del GPS (45 m/s ≈ 162 km/h): son
	// fixes basura que hacían que la línea se fuera "por fuera" de la calle.
	maxSpeedMps = 45.0

	// trailSimplifyMeters es la tolerancia de Douglas–Peucker: quita puntos
	// redundantes pero respeta la forma real de la ruta.
	trailSimplifyMeters = 8.0
)

// DeviceIDsDefault es el mapeo placa ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ id de dispositivo en Traccar,
// verificado contra GET /api/devices (CJI-579 = 147; la app y el backend usan
// "C0C-965" con cero y Traccar lo llama "COC-965" con letra O: es el mismo
// vehÃƒÆ’Ã‚Â­culo y por eso se resuelve por id, nunca por nombre).
var DeviceIDsDefault = map[string]int64{
	"CJI-579": 147,
	"C0C-965": 142,
}

// Config es la configuraciÃƒÆ’Ã‚Â³n del proxy, leÃƒÆ’Ã‚Â­da del entorno.
type Config struct {
	BaseURL   string
	Username  string
	Password  string
	DeviceIDs map[string]int64
	CacheTTL  time.Duration
}

// LoadConfigFromEnv lee TRACCAR_BASE_URL, TRACCAR_USER, TRACCAR_PASSWORD,
// TRACCAR_DEVICE_IDS ("CJI-579=147,C0C-965=142") y TRACCAR_CACHE_TTL.
// Sin usuario/contraseÃƒÆ’Ã‚Â±a el servicio queda deshabilitado (503 en el endpoint),
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
			slog.Warn("TRACCAR_DEVICE_IDS invÃƒÆ’Ã‚Â¡lido, se usa el mapeo por defecto", "error", err)
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
			return nil, fmt.Errorf("par invÃƒÆ’Ã‚Â¡lido %q (se espera PLACA=ID)", pair)
		}
		id, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
		if err != nil || id <= 0 {
			return nil, fmt.Errorf("id invÃƒÆ’Ã‚Â¡lido en %q", pair)
		}
		out[strings.ToUpper(strings.TrimSpace(parts[0]))] = id
	}
	if len(out) == 0 {
		return nil, errors.New("no se indicÃƒÆ’Ã‚Â³ ningÃƒÆ’Ã‚Âºn vehÃƒÆ’Ã‚Â­culo")
	}
	return out, nil
}

// VehiclePosition es lo que consume la app: datos mÃƒÆ’Ã‚Â­nimos y sin credenciales.
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
	// Trail es el recorrido de las ÃƒÆ’Ã‚Âºltimas horas ya muestreado: sirve para
	// dibujar en el mapa el camino que hizo el bus (no solo el punto actual).
	Trail []TrailPoint `json:"trail"`
}

// TrailPoint es un punto del recorrido para la polilÃƒÆ’Ã‚Â­nea del mapa.
type TrailPoint struct {
	Latitude  float64   `json:"latitude"`
	Longitude float64   `json:"longitude"`
	FixTime   time.Time `json:"fix_time"`
}

// Service consulta Traccar y arma las posiciones, con caché corto.
type Service struct {
	cfg    Config
	client *http.Client
	now    func() time.Time

	// visiblePlates devuelve las placas que el usuario PUEDE ver y desde cuándo
	// se puede mostrar el recorrido (la hora en que el conductor INICIÓ el
	// viaje). Antes de eso no se muestra nada: el bus está en el paradero y eso
	// no debe exponerse.
	visiblePlates func(ctx context.Context, userID int64) (map[string]time.Time, error)

	mu        sync.Mutex
	cached    []VehiclePosition
	cachedAt  time.Time
	cacheOK   bool
}

// WithVisiblePlates inyecta el filtro de seguridad por usuario: placa → hora de
// inicio del recorrido.
func (s *Service) WithVisiblePlates(fn func(ctx context.Context, userID int64) (map[string]time.Time, error)) *Service {
	s.visiblePlates = fn
	return s
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

// LatestForUser devuelve SOLO los vehículos que el usuario puede ver: los de
// sus reservas con el recorrido ya iniciado por el conductor.
//
// Regla de seguridad: si el conductor no inició el recorrido (viaje en estado
// IN_PROGRESS) no se devuelve ninguna posición, y sin filtro configurado se
// niega todo. La respuesta es un array vacío (no un error) para que la app
// muestre el aviso neutro al pasajero.
func (s *Service) LatestForUser(ctx context.Context, userID int64) ([]VehiclePosition, error) {
	// Primero el filtro de seguridad: si el pasajero no tiene un recorrido en
	// curso, se responde vacío sin siquiera consultar el GPS.
	if s.visiblePlates == nil {
		return []VehiclePosition{}, nil
	}
	plates, err := s.visiblePlates(ctx, userID)
	if err != nil {
		return nil, err
	}
	if len(plates) == 0 {
		return []VehiclePosition{}, nil
	}

	all, err := s.latestAll(ctx)
	if err != nil {
		return nil, err
	}
	allowed := make(map[string]time.Time, len(plates))
	for plate, startedAt := range plates {
		allowed[strings.ToUpper(strings.TrimSpace(plate))] = startedAt
	}
	out := make([]VehiclePosition, 0, len(all))
	for _, item := range all {
		startedAt, ok := allowed[strings.ToUpper(item.Plate)]
		if !ok {
			continue
		}
		// El recorrido arranca cuando el conductor tocó "iniciar": lo de antes
		// (paradero, cochera) NO se muestra.
		trail, err := s.fetchTrail(ctx, item.Plate, startedAt)
		if err != nil {
			slog.Warn("vehicles: no se pudo traer el recorrido", "plate", item.Plate, "error", err)
		} else {
			item.Trail = trail
		}
		out = append(out, item)
	}
	return out, nil
}

// latestAll es la consulta a Traccar (cacheada) sin filtrar por usuario.
func (s *Service) latestAll(ctx context.Context) ([]VehiclePosition, error) {
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

	// El recorrido se arma por usuario y desde la hora de inicio del viaje
	// (ver LatestForUser): antes de que el conductor arranque no se expone.

	s.mu.Lock()
	s.cached = result
	s.cachedAt = s.now()
	s.cacheOK = true
	s.mu.Unlock()

	return result, nil
}

// fetchTrail pide a Traccar las posiciones desde que el conductor inició el
// viaje (o desde el límite de trailWindow si el viaje viene de antes) y las
// reduce a una polilínea razonable.
func (s *Service) fetchTrail(ctx context.Context, plate string, startedAt time.Time) ([]TrailPoint, error) {
	deviceID, ok := s.cfg.DeviceIDs[strings.ToUpper(strings.TrimSpace(plate))]
	if !ok {
		return nil, nil
	}
	to := s.now().UTC()
	from := startedAt.UTC()
	if floor := to.Add(-trailWindow); from.Before(floor) {
		// Nunca más atrás que la ventana: el histórico viejo no interesa.
		from = floor
	}
	if !from.Before(to) {
		return nil, nil
	}
	var raw []traccarPosition
	query := url.Values{
		"deviceId": {fmt.Sprintf("%d", deviceID)},
		"from":     {from.Format(time.RFC3339)},
		"to":       {to.Format(time.RFC3339)},
	}
	if err := s.getJSON(ctx, "positions", query, &raw); err != nil {
		return nil, err
	}
	return buildTrail(raw, maxTrailPoints), nil
}

// buildTrail ordena, descarta repetidos (el bus estacionado manda cientos de
// puntos iguales) y muestrea hasta `max` puntos. FunciÃƒÆ’Ã‚Â³n pura para poder probar.
func buildTrail(points []traccarPosition, max int) []TrailPoint {
	var clean []trailRaw
	for _, p := range points {
		if !validCoords(p.Latitude, p.Longitude) {
			continue
		}
		if p.Valid != nil && !*p.Valid {
			continue
		}
		at := parseTime(p.FixTime)
		if at == nil {
			continue
		}
		// Se salta el punto si es (casi) idéntico al anterior: el bus parado
		// reporta la misma posición cada 10 s y no aporta a la línea.
		if len(clean) > 0 {
			prev := clean[len(clean)-1]
			if abs(prev.lat-p.Latitude) < 5e-5 && abs(prev.lon-p.Longitude) < 5e-5 {
				continue
			}
			// Punto imposible (salto de varios cientos de metros en segundos):
			// el GPS perdió fix o marcó basura. Si se deja, la línea se va
			// "por fuera" de la calle.
			seconds := at.Sub(prev.at).Seconds()
			if seconds > 1 {
				metros := haversineMeters(prev.lat, prev.lon, p.Latitude, p.Longitude)
				if metros/seconds > maxSpeedMps {
					continue
				}
			}
		}
		clean = append(clean, trailRaw{lat: p.Latitude, lon: p.Longitude, at: *at})
	}
	if len(clean) == 0 {
		return nil
	}

	// Simplificación Douglas–Peucker: conserva la forma real del recorrido y
	// elimina el zigzag del GPS, sin cortar camino como hacía el muestreo
	// uniforme (que unía puntos lejanos con una recta larga).
	epsDeg := trailSimplifyMeters / 111_320.0
	clean = simplifyDouglasPeucker(clean, epsDeg)

	// Tope de seguridad de puntos.
	step := 1
	if max > 0 && len(clean) > max {
		step = (len(clean) + max - 1) / max
	}
	out := make([]TrailPoint, 0, len(clean)/step+1)
	for i := 0; i < len(clean); i += step {
		out = append(out, TrailPoint{
			Latitude:  clean[i].lat,
			Longitude: clean[i].lon,
			FixTime:   clean[i].at,
		})
	}
	// El último punto siempre va: es donde está el bus ahora.
	last := clean[len(clean)-1]
	if out == nil || out[len(out)-1].Latitude != last.lat || out[len(out)-1].Longitude != last.lon {
		out = append(out, TrailPoint{Latitude: last.lat, Longitude: last.lon, FixTime: last.at})
	}
	return out
}

type trailRaw struct {
	lat float64
	lon float64
	at  time.Time
}

// simplifyDouglasPeucker reduce la polilínea manteniendo los puntos que
// definen la forma (curvas, esquinas) y descartando los intermedios.
func simplifyDouglasPeucker(points []trailRaw, epsilon float64) []trailRaw {
	if len(points) < 3 {
		return points
	}
	keep := make([]bool, len(points))
	keep[0] = true
	keep[len(points)-1] = true

	var recurse func(from, to int)
	recurse = func(from, to int) {
		if to-from < 2 {
			return
		}
		// Perpendicular más lejana al segmento from→to.
		maxDist := 0.0
		index := -1
		for i := from + 1; i < to; i++ {
			d := perpendicularDistance(points[i], points[from], points[to])
			if d > maxDist {
				maxDist = d
				index = i
			}
		}
		if maxDist > epsilon && index > 0 {
			keep[index] = true
			recurse(from, index)
			recurse(index, to)
		}
	}
	recurse(0, len(points)-1)

	out := make([]trailRaw, 0, len(points))
	for i, p := range points {
		if keep[i] {
			out = append(out, p)
		}
	}
	return out
}

// perpendicularDistance devuelve la distancia (en grados) del punto p a la
// recta a→b, aproximando con un plano local.
func perpendicularDistance(p, a, b trailRaw) float64 {
	dx := b.lon - a.lon
	dy := b.lat - a.lat
	if dx == 0 && dy == 0 {
		return math.Hypot(p.lon-a.lon, p.lat-a.lat)
	}
	t := ((p.lon-a.lon)*dx + (p.lat-a.lat)*dy) / (dx*dx + dy*dy)
	var cx, cy float64
	switch {
	case t < 0:
		cx, cy = a.lon, a.lat
	case t > 1:
		cx, cy = b.lon, b.lat
	default:
		cx, cy = a.lon+t*dx, a.lat+t*dy
	}
	return math.Hypot(p.lon-cx, p.lat-cy)
}

// haversineMeters es la distancia en metros entre dos coordenadas.
func haversineMeters(lat1, lon1, lat2, lon2 float64) float64 {
	const earthRadius = 6_371_000.0
	toRad := func(d float64) float64 { return d * math.Pi / 180 }
	dLat := toRad(lat2 - lat1)
	dLon := toRad(lon2 - lon1)
	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(toRad(lat1))*math.Cos(toRad(lat2))*math.Sin(dLon/2)*math.Sin(dLon/2)
	return 2 * earthRadius * math.Asin(math.Sqrt(a))
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
	if err := s.getJSON(ctx, "devices", nil, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (s *Service) fetchPositions(ctx context.Context) ([]traccarPosition, error) {
	var out []traccarPosition
	if err := s.getJSON(ctx, "positions", nil, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func (s *Service) getJSON(ctx context.Context, path string, query url.Values, target any) error {
	endpoint, err := url.JoinPath(s.cfg.BaseURL, path)
	if err != nil {
		return fmt.Errorf("armando URL de Traccar: %w", err)
	}
	parsed, err := url.Parse(endpoint)
	if err != nil {
		return fmt.Errorf("parseando URL de Traccar: %w", err)
	}
	if len(query) > 0 {
		parsed.RawQuery = query.Encode()
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, parsed.String(), nil)
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
		return fmt.Errorf("Traccar respondiÃƒÆ’Ã‚Â³ %d", resp.StatusCode)
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
// dominio. Es una funciÃƒÆ’Ã‚Â³n pura para poder probarla sin red.
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

		// Coordenadas vÃƒÆ’Ã‚Â¡lidas y con sentido (nunca 0,0 inventado).
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
	// (0,0) en Traccar suele ser "sin fix": no es una ubicaciÃƒÆ’Ã‚Â³n real.
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

// abs devuelve el valor absoluto (para comparar coordenadas casi iguales).
func abs(v float64) float64 {
	if v < 0 {
		return -v
	}
	return v
}