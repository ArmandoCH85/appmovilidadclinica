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

// Un fix basura (salto de ~1 km en 10 s) no debe arrastrar la línea fuera de
// la calle: se descarta y el recorrido sigue derecho.
func TestBuildTrail_DescartaSaltosImposiblesDelGPS(t *testing.T) {
	base := time.Date(2026, 10, 12, 20, 0, 0, 0, time.UTC)
	puntos := []traccarPosition{
		{DeviceID: 147, Latitude: -12.1000, Longitude: -77.0300, FixTime: base.Format(time.RFC3339), Valid: boolPtr(true)},
		{DeviceID: 147, Latitude: -12.1005, Longitude: -77.0305, FixTime: base.Add(10 * time.Second).Format(time.RFC3339), Valid: boolPtr(true)},
		// outlier: ~1.1 km al este en 10 s (imposible)
		{DeviceID: 147, Latitude: -12.1005, Longitude: -77.0200, FixTime: base.Add(20 * time.Second).Format(time.RFC3339), Valid: boolPtr(true)},
		{DeviceID: 147, Latitude: -12.1010, Longitude: -77.0310, FixTime: base.Add(30 * time.Second).Format(time.RFC3339), Valid: boolPtr(true)},
	}

	got := buildTrail(puntos, 100)
	for _, p := range got {
		if p.Longitude > -77.0250 {
			t.Fatalf("el outlier se coló en el recorrido: %+v", p)
		}
	}
	if len(got) < 2 {
		t.Fatalf("recorrido demasiado corto: %+v", got)
	}
}

// Un tramo recto con muchos puntos intermedios se simplifica sin perder los
// extremos (la línea no se corta ni cambia de forma).
func TestSimplifyDouglasPeucker_ConservaExtremosYCurvas(t *testing.T) {
	base := time.Date(2026, 10, 12, 20, 0, 0, 0, time.UTC)
	var puntos []trailRaw
	// Recta de 10 puntos colineales.
	for i := 0; i < 10; i++ {
		puntos = append(puntos, trailRaw{
			lat: -12.10,
			lon: -77.03 + float64(i)*0.001,
			at:  base.Add(time.Duration(i) * time.Minute),
		})
	}
	// Una curva marcada al final.
	puntos = append(puntos,
		trailRaw{lat: -12.105, lon: -77.021, at: base.Add(11 * time.Minute)},
		trailRaw{lat: -12.110, lon: -77.020, at: base.Add(12 * time.Minute)},
	)

	got := simplifyDouglasPeucker(puntos, 8.0/111320.0)
	if len(got) >= len(puntos) {
		t.Errorf("no simplificó: %d de %d", len(got), len(puntos))
	}
	if got[0] != puntos[0] || got[len(got)-1] != puntos[len(puntos)-1] {
		t.Errorf("perdió los extremos: %+v", got)
	}
	if len(got) < 3 {
		t.Errorf("se perdió la curva del final: %+v", got)
	}
}
