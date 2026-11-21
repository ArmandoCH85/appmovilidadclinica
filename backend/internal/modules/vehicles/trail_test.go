package vehicles

import (
	"testing"
	"time"
)

func TestBuildTrail_DescartaRepetidosYMuestrea(t *testing.T) {
	var puntos []traccarPosition
	base := time.Date(2026, 10, 12, 20, 0, 0, 0, time.UTC)
	for i := 0; i < 100; i++ {
		puntos = append(puntos, traccarPosition{
			DeviceID:  147,
			Latitude:  -12.0 - float64(i)/1000,
			Longitude: -77.0,
			FixTime:   base.Add(time.Duration(i) * time.Minute).Format(time.RFC3339),
			Valid:     boolPtr(true),
		})
	}
	// 50 puntos repetidos (bus estacionado) que no deben aparecer.
	for i := 0; i < 50; i++ {
		puntos = append(puntos, traccarPosition{
			DeviceID:  147,
			Latitude:  -12.1,
			Longitude: -77.0,
			FixTime:   base.Add(time.Duration(200+i) * time.Minute).Format(time.RFC3339),
		})
	}

	got := buildTrail(puntos, 20)
	if len(got) > 21 {
		t.Fatalf("no se muestreo: %d puntos", len(got))
	}
	if len(got) < 2 {
		t.Fatalf("muy pocos puntos: %+v", got)
	}
	// El ultimo punto del recorrido es donde esta el bus ahora.
	last := got[len(got)-1]
	if last.Latitude != -12.1 {
		t.Errorf("el ultimo punto deberia ser el actual, fue %+v", last)
	}

	// Sin puntos validos -> nil (no se inventa recorrido).
	if buildTrail(nil, 10) != nil {
		t.Error("sin puntos deberia devolver nil")
	}
}

func TestBuildTrail_DescartaCoordenadasInvalidas(t *testing.T) {
	got := buildTrail([]traccarPosition{
		{DeviceID: 147, Latitude: 0, Longitude: 0, FixTime: "2026-10-12T20:00:00Z"},
		{DeviceID: 147, Latitude: -999, Longitude: -77, FixTime: "2026-10-12T20:01:00Z"},
	}, 10)
	if got != nil {
		t.Errorf("coordenadas invalidas deberian descartarse: %+v", got)
	}
}

func boolPtr(v bool) *bool { return &v }
