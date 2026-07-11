package auth

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/jwtauth/v5"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/authctx"
	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/validate"
)

// AuthHandler expone los endpoints HTTP del modulo auth.
type AuthHandler struct {
	svc AuthService
}

// NewHandler construye el handler con su servicio inyectado.
func NewHandler(svc AuthService) *AuthHandler {
	return &AuthHandler{svc: svc}
}

// loginRequest es el cuerpo de POST /login.
type loginRequest struct {
	DocumentNumber string `json:"document_number" validate:"required"`
	Password       string `json:"password" validate:"required"`
}

// loginResponse devuelve el token y el perfil del usuario autenticado.
type loginResponse struct {
	Token string `json:"token"`
	User  User   `json:"user"`
}

// Login maneja POST /login. Parsea JSON, valida, llama al servicio y
// responde 200 {token, user} o un error via apperror.WriteJSONError.
func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := validate.Default.Struct(req); err != nil {
		apperror.WriteJSONError(w, validate.ToAppError(err))
		return
	}

	token, user, err := h.svc.Login(r.Context(), req.DocumentNumber, req.Password)
	if err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(loginResponse{Token: token, User: user})
}

// meResponse es el perfil minimo derivado de los claims del JWT.
type meResponse struct {
	ID           int64  `json:"id"`
	EmployeeCode string `json:"employee_code"`
	FullName     string `json:"full_name"`
	Role         string `json:"role"`
}

// Me maneja GET /me. Lee los claims del contexto (puestos por jwtauth) y
// devuelve el perfil. No toca la BD: el JWT ya trae la identidad.
func (h *AuthHandler) Me(w http.ResponseWriter, r *http.Request) {
	_, claims, err := jwtauth.FromContext(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, apperror.UnauthorizedError{Reason: "token sin claims"})
		return
	}
	userID, _ := authctx.UserIDFromContext(r.Context())
	role, _ := authctx.RoleFromContext(r.Context())
	fullName := authctx.ClaimString(r.Context(), "full_name")
	employeeCode := authctx.ClaimString(r.Context(), "employee_code")
	_ = claims
	_ = json.NewEncoder(w).Encode(meResponse{
		ID:           userID,
		EmployeeCode: employeeCode,
		FullName:     fullName,
		Role:         role,
	})
}

// RegisterRoutes monta los endpoints del modulo auth. /me requiere usuario
// autenticado; el router padre (Phase 3) aplica jwtauth.Verifier+
// Authenticator sobre el grupo que monta /me. /login es publico.
func (h *AuthHandler) RegisterRoutes(r chi.Router) {
	r.Post("/login", h.Login)
	r.Get("/me", h.Me)
}
