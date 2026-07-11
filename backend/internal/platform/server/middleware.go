// Package server contiene el cableado HTTP de plataforma: middleware
// chain, rate limiter y CORS. Pertenece a la capa `platform` (no a un módulo
// de dominio) porque es infraestructura transversal, no lógica de negocio.
package server

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

// rateWindow define cada ventana deslizante de 60s del rate limiter por IP.
// 60 req por minuto es suficiente para un MVP con <50 usuarios reales.
const (
	rateLimit  = 60
	rateWindow = 60 * time.Second
)

// rateEntry es el contador por IP. resetAt marca cuándo vuelve a 0.
// sync.Map[string]*rateEntry en lugar de un map+mutex: el patrón escribe
// concurrente por IP y lectura sin bloqueo en el path caliente.
type rateEntry struct {
	count   int64
	resetAt time.Time
}

// rateLimiter mantiene el estado global de contadores por IP. Una sola
// instancia por proceso basta (RateLimit la cierra sobre la variable).
var rateLimiter sync.Map

// SetupMiddleware aplica la cadena estándar de chi en orden: RequestID →
// RealIP → logger slog → Recoverer → CleanPath → JSON content-type. El orden
// importa: RequestID debe envolver a todo para que el logger pueda anotar el
// id en cualquier etapa, incluyendo panics que atrapa Recoverer.
func SetupMiddleware(r chi.Router) {
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(requestLogger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.CleanPath)
	r.Use(jsonContentType)
}

// requestLogger registra cada solicitud con slog + RequestID de contexto.
// slog (estándar desde Go 1.21) reemplaza el logger de chi para integrarse
// con el resto del backend. Captura método, ruta, status y duración.
func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)

		reqID := middleware.GetReqID(r.Context())
		slog.Info("peticion http",
			"req_id", reqID,
			"metodo", r.Method,
			"ruta", r.URL.Path,
			"status", ww.Status(),
			"bytes", ww.BytesWritten(),
			"duracion_ms", time.Since(start).Milliseconds(),
		)
	})
}

// jsonContentType ajusta Content-Type: application/json para todas las
// respuestas. El API sólo habla JSON; este setter evita que cada handler lo
// repita. Se aplica ANTES de escribir el body para que persista.
func jsonContentType(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		next.ServeHTTP(w, r)
	})
}

// RateLimit es un rate limiter por IP basado en sync.Map. ~10 líneas de
// lógica real: incrementa contador, comprueba resetAt y rechaza con 429 si
// excede 60/min. Sin bibliotecas externas (httprate estaba en el plan
// original, el ponytail-audit la eliminó).
//
// El cleanup es perezoso: en cada request se borran las entradas cuya
// resetAt ya venció. Esto mantiene el map acotado sin un goroutine extra.
func RateLimit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip := clientIP(r)
		now := time.Now()

		// GC perezoso: recorre sync.Map borrando vencidos. Costo O(n) por
		// request es aceptable porque n son pocos cientos de IPs activas.
		rateLimiter.Range(func(key, value any) bool {
			if entry, ok := value.(*rateEntry); ok && now.After(entry.resetAt) {
				rateLimiter.Delete(key)
			}
			return true
		})

		entry, _ := rateLimiter.LoadOrStore(ip, &rateEntry{count: 0, resetAt: now.Add(rateWindow)})
		e := entry.(*rateEntry)

		// Si la ventana venció entre el LoadOrStore y ahora, la reiniciamos.
		// CompareAndSwap no es necesario: un desborde puntual no rompe nada.
		if now.After(e.resetAt) {
			e.count = 0
			e.resetAt = now.Add(rateWindow)
		}
		e.count++

		if e.count > rateLimit {
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("Retry-After", "60")
			w.WriteHeader(http.StatusTooManyRequests)
			_ = json.NewEncoder(w).Encode(map[string]any{
				"error": map[string]any{
					"code":    http.StatusTooManyRequests,
					"message": "limite de peticiones excedido; reintente en 60 segundos",
				},
			})
			return
		}

		next.ServeHTTP(w, r)
	})
}

// clientIP extrae la IP del cliente respetando X-Forwarded-For cuando RealIP
// ya la reescribió en r.RemoteAddr. Como middleware.RealIP corre ANTES en la
// cadena, r.RemoteAddr confluye a la IP real; aquí sólo la partimos por el
// último ":" por si llegara "ip:port".
func clientIP(r *http.Request) string {
	addr := r.RemoteAddr
	if idx := strings.LastIndex(addr, ":"); idx > -1 {
		return addr[:idx]
	}
	return addr
}

// CORS permite orígenes cruzados para el MVP. El frontend (app móvil / web
// admin) corre separado; un allow-all es suficiente mientras no haya
// cookies de sesión (el backend usa JWT Authorization header).
//
// El preflight OPTIONS responde 204 sin body, implementación típica.
func CORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
