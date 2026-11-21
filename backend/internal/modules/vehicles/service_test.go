package vehicles

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestParseDeviceIDs(t *testing.T) {
	got, err := ParseDeviceIDs("CJI-579=147, c0c-965 = 142 ")
	if err != nil {
		t.Fatalf("error inesperado: %v", err)
	}
	if got["CJI-579"] != 147 {
		t.Errorf("CJI-579 = %d, se esperaba 147", got["CJI-579"])
	}
	if got["C0C-965"] != 142 {
		t.Errorf("C0C-965 = %d, se esperaba 142 (normaliza minúsculas)", got["C0C-965"])
	}

	for _, raw := range []string{"", "CJI-579", "CJI-579=abc", "CJI-579=0"} {
		if _, err := ParseDeviceIDs(raw); err == nil {
			t.Errorf("ParseDeviceIDs(%q) debería fallar", raw)
		}
	}
}

func TestBuildPositions_MapeaPorIDYNoPorNombre(t *testing.T) {
	now := time.Date(2026, 10, 12, 22, 0, 0, 0, time.UTC)
	devices := []traccarDevice{
		{ID: 147, Name: "CJI-579", Status: "online"},
		// En Traccar figura con letra O; la app usa cero.
		{ID: 142, Name: "COC-965", Status: "offline"},
	}
	positions := []traccarPosition{
		{DeviceID: 147, Latitude: -12.1116, Longitude: -77.0000, FixTime: "2026-10-12T21:58:00Z", Speed: floatPtr(10)},
		{DeviceID: 142, Latitude: -12.0732, Longitude: -77.0349, FixTime: "2026-10-12T19:00:00Z"},
		{DeviceID: 999, Latitude: 1, Longitude: 1, FixTime: "2026-10-12T21:59:00Z"}, // no trackeado
	}

	got := buildPositions(DeviceIDsDefault, devices, positions, now)

	if len(got) != 2 {
		t.Fatalf("se esperaban 2 vehículos, se obtuvieron %d", len(got))
	}
	if got[0].Plate != "C0C-965" || got[1].Plate != "CJI-579" {
		t.Fatalf("orden/placas inesperadas: %+v", got)
	}

	c0c := got[0]
	if c0c.Latitude == nil || *c0c.Latitude != -12.0732 {
		t.Errorf("C0C-965 sin coordenadas correctas: %+v", c0c)
	}
	if c0c.Online == nil || *c0c.Online {
		t.Errorf("C0C-965 debería figurar offline")
	}
	if !c0c.IsStale {
		t.Errorf("C0C-965 tiene fix de 3 h: debería ser stale")
	}

	cji := got[1]
	if cji.SpeedKmh == nil {
		t.Fatalf("CJI-579 sin velocidad")
	}
	// 10 nudos = 18.52 km/h
	if diff := *cji.SpeedKmh - 18.52; diff > 0.01 || diff < -0.01 {
		t.Errorf("velocidad = %.2f, se esperaba 18.52", *cji.SpeedKmh)
	}
	if cji.IsStale {
		t.Errorf("CJI-579 con fix de 2 min no debería ser stale")
	}
	if cji.Online == nil || !*cji.Online {
		t.Errorf("CJI-579 debería figurar online")
	}
}

func TestBuildPositions_SinPosicionNoInventaCoordenadas(t *testing.T) {
	now := time.Date(2026, 10, 12, 22, 0, 0, 0, time.UTC)
	devices := []traccarDevice{{ID: 147, Name: "CJI-579", Status: "online"}}

	got := buildPositions(map[string]int64{"CJI-579": 147}, devices, nil, now)

	if len(got) != 1 {
		t.Fatalf("se esperaba 1 vehículo")
	}
	if got[0].HasPosition || got[0].Latitude != nil || got[0].Longitude != nil {
		t.Errorf("no debe inventar coordenadas: %+v", got[0])
	}
	if !got[0].IsStale {
		t.Errorf("sin posición debe marcarse como stale")
	}
}

func TestBuildPositions_DescartaCeroCeroYFueraDeRango(t *testing.T) {
	now := time.Date(2026, 10, 12, 22, 0, 0, 0, time.UTC)
	devices := []traccarDevice{{ID: 147, Status: "online"}}

	casos := []traccarPosition{
		{DeviceID: 147, Latitude: 0, Longitude: 0, FixTime: "2026-10-12T21:59:00Z"},
		{DeviceID: 147, Latitude: -999, Longitude: -77, FixTime: "2026-10-12T21:59:00Z"},
	}
	for _, p := range casos {
		got := buildPositions(map[string]int64{"CJI-579": 147}, devices, []traccarPosition{p}, now)
		if got[0].HasPosition || got[0].Latitude != nil {
			t.Errorf("coordenada inválida aceptada: %+v", p)
		}
	}
}

func TestService_SinCredencialesNoConsulta(t *testing.T) {
	svc := NewService(Config{BaseURL: DefaultBaseURL, DeviceIDs: DeviceIDsDefault}, nil)
	if _, err := svc.Latest(context.Background()); err != ErrNotConfigured {
		t.Fatalf("se esperaba ErrNotConfigured, se obtuvo %v", err)
	}
}

func TestService_ConsultaTraccarYUsaCache(t *testing.T) {
	llamadas := 0
	traccar := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		llamadas++
		if user, pass, ok := r.BasicAuth(); !ok || user != "u" || pass != "p" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/api/devices":
			_, _ = w.Write([]byte(`[{"id":147,"name":"CJI-579","status":"online"}]`))
		case "/api/positions":
			_, _ = w.Write([]byte(`[{"deviceId":147,"latitude":-12.1,"longitude":-77.0,"fixTime":"2026-10-12T21:59:00Z","valid":true,"speed":5}]`))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer traccar.Close()

	svc := NewService(Config{
		BaseURL:   traccar.URL + "/api/",
		Username:  "u",
		Password:  "p",
		DeviceIDs: map[string]int64{"CJI-579": 147},
		CacheTTL:  time.Minute,
	}, traccar.Client())
	fixed := time.Date(2026, 10, 12, 22, 0, 0, 0, time.UTC)
	svc.now = func() time.Time { return fixed }

	got, err := svc.Latest(context.Background())
	if err != nil {
		t.Fatalf("error inesperado: %v", err)
	}
	if len(got) != 1 || !got[0].HasPosition {
		t.Fatalf("posición inesperada: %+v", got)
	}
	if llamadas != 2 {
		t.Fatalf("se esperaban 2 llamadas a Traccar (devices+positions), hubo %d", llamadas)
	}

	// Segunda consulta: cacheada, sin llamadas nuevas.
	if _, err := svc.Latest(context.Background()); err != nil {
		t.Fatalf("error inesperado: %v", err)
	}
	if llamadas != 2 {
		t.Errorf("la caché no funcionó: hubo %d llamadas", llamadas)
	}
}

func TestService_PropagaErrorDeTraccar(t *testing.T) {
	traccar := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer traccar.Close()

	svc := NewService(Config{
		BaseURL:   traccar.URL + "/api/",
		Username:  "u",
		Password:  "p",
		DeviceIDs: DeviceIDsDefault,
		CacheTTL:  0,
	}, traccar.Client())

	if _, err := svc.Latest(context.Background()); err == nil {
		t.Fatal("se esperaba error cuando Traccar responde 401")
	}
}

func floatPtr(v float64) *float64 { return &v }
