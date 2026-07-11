package server

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/jwtauth/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/admin"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/auth"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/booking"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/driver"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/modules/trips"
)

// RouterDeps agrupa las dependencias que NewRouter cablea: los handlers de
// los seis modulos, el firmador/verificador JWT y el logger. Centralizarlas
// en un struct evita una firma con muchos parametros y deja el cableado
// explicito en main.go.
type RouterDeps struct {
	AuthHandler    *auth.AuthHandler
	TripsHandler   *trips.TripsHandler
	BookingHandler *booking.BookingHandler
	DriverHandler  *driver.DriverHandler
	AdminHandler   *admin.AdminHandler
	TokenAuth      *jwtauth.JWTAuth
	Logger         *slog.Logger
}

// NewRouter construye el router raiz del API. Encadena platform middleware,
// rate limit, CORS y verificacion JWT, y monta los seis modulos bajo /api.
//
// Sobre la autenticacion: jwtauth.Verifier se aplica a todo /api para que el
// JWT (si llega) se decodifique y quede en el contexto. Luego un
// Authenticator custom permite rutas publicas (POST /api/auth/login) y exige
// token valido al resto. El guard de rol (ADMIN/DRIVER/WORKER) vive en cada
// servicio, no aqui: el router solo garantiza identidad, no permisos.
func NewRouter(deps RouterDeps) http.Handler {
	log := deps.Logger
	if log == nil {
		log = slog.Default()
	}

	r := chi.NewRouter()

	// Cadena de platform middleware (RequestID, RealIP, logger slog,
	// Recoverer, CleanPath, Content-Type JSON).
	SetupMiddleware(r)
	r.Use(RateLimit)
	r.Use(CORS)
	r.Use(jwtauth.Verifier(deps.TokenAuth))
	r.Use(authenticator(deps.TokenAuth, log))

	r.Route("/api", func(r chi.Router) {
		// Healthcheck para balanceadores y docker. No toca la BD.
		r.Get("/health", healthHandler)

		// auth: login es publico (lo deja pasar el authenticator), /me
		// requiere JWT valido.
		r.Route("/auth", func(r chi.Router) {
			deps.AuthHandler.RegisterRoutes(r)
		})

		// trips y booking registran rutas con prefijo propio (/trips,
		// /reservations); se montan directo en /api.
		deps.TripsHandler.RegisterRoutes(r)
		deps.BookingHandler.RegisterRoutes(r)

		// driver y admin usan r.Route("/driver|/admin", ...) internamente.
		deps.DriverHandler.RegisterRoutes(r)
		deps.AdminHandler.RegisterRoutes(r)
	})

	return r
}

// healthHandler responde GET /api/health con {"status":"ok"}. Ligero y sin
// dependencias para servir de probe de liveness/readiness.
func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// authenticator es el middleware custom que decide si una peticion necesita
// JWT valido. jwtauth.Authenticator estandar rechaza todo lo sin token; aqui
// necesitamos una excepcion para POST /api/auth/login (y para el healthcheck).
//
// Logica:
//   - Si la ruta es publica (POST /api/auth/login, GET /api/health) -> next.
//   - Si el JWT esta presente y valido -> next (claims ya en contexto).
//   - Si no -> 401 con body JSON de error estandar.
//
// Se basa en jwtauth.FromContext que devuelve err != nil cuando el Verifier
// no encontro/valido el token.
func authenticator(tokenAuth *jwtauth.JWTAuth, log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if isPublicRoute(r) {
				next.ServeHTTP(w, r)
				return
			}

			_, _, err := jwtauth.FromContext(r.Context())
			if err != nil {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusUnauthorized)
				_ = json.NewEncoder(w).Encode(map[string]any{
					"error": map[string]any{
						"code":    http.StatusUnauthorized,
						"message": "token JWT requerido o invalido",
					},
				})
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// isPublicRoute indica si la ruta+metodo actual no requiere autenticacion.
// Solo POST /api/auth/login y GET /api/health son publicos en el MVP.
func isPublicRoute(r *http.Request) bool {
	path := r.URL.Path
	if path == "/api/health" {
		return true
	}
	if path == "/api/auth/login" && r.Method == http.MethodPost {
		return true
	}
	return false
}
