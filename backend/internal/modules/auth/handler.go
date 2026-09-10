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

// loginRequest es el cuerpo de POST /login. Acepta DNI (document_number)
// o nombre de usuario (username) en el mismo campo "identifier" — el
// servicio decide cual usar segun el contenido (digitos -> DNI, otro ->
// username). Esto evita romper clientes viejos que mandan document_number.
type loginRequest struct {
	Identifier string
	Password   string
}

// UnmarshalJSON acepta tanto document_number (compat legacy) como
// username (camino nuevo) en el mismo slot del payload.
func (r *loginRequest) UnmarshalJSON(data []byte) error {
	var aux struct {
		DocumentNumber string `json:"document_number"`
		Username       string `json:"username"`
		Identifier     string `json:"identifier"`
		Password       string `json:"password"`
	}
	if err := json.Unmarshal(data, &aux); err != nil {
		return err
	}
	switch {
	case aux.Identifier != "":
		r.Identifier = aux.Identifier
	case aux.Username != "":
		r.Identifier = aux.Username
	case aux.DocumentNumber != "":
		r.Identifier = aux.DocumentNumber
	}
	r.Password = aux.Password
	return nil
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
	if req.Identifier == "" {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "identifier", Reason: "document_number o username requerido"})
		return
	}
	if req.Password == "" {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "password", Reason: "requerido"})
		return
	}

	token, user, err := h.svc.Login(r.Context(), req.Identifier, req.Password)
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

// changePasswordRequest es el cuerpo de POST /change-password. El userID
// sale del JWT (authctx), nunca del body: un usuario solo cambia su clave.
type changePasswordRequest struct {
	CurrentPassword string `json:"current_password"`
	NewPassword     string `json:"new_password"`
}

// ChangePassword maneja POST /change-password. Requiere JWT (lo garantiza
// el authenticator del router). Responde 204 sin body en exito; en error
// un JSON estandar via apperror.WriteJSONError (401 si la actual no
// coincide, 422 si la nueva no valida).
func (h *AuthHandler) ChangePassword(w http.ResponseWriter, r *http.Request) {
	userID, err := authctx.UserIDFromContext(r.Context())
	if err != nil {
		apperror.WriteJSONError(w, apperror.UnauthorizedError{Reason: "token sin claims"})
		return
	}
	var req changePasswordRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		apperror.WriteJSONError(w, apperror.ValidationError{Field: "body", Reason: "json invalido"})
		return
	}
	if err := h.svc.ChangePassword(r.Context(), userID, req.CurrentPassword, req.NewPassword); err != nil {
		apperror.WriteJSONError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// RegisterRoutes monta los endpoints del modulo auth. /me y /change-password
// requieren usuario autenticado; el router padre aplica jwtauth.Verifier+
// Authenticator sobre el grupo. /login es publico.
func (h *AuthHandler) RegisterRoutes(r chi.Router) {
	r.Post("/login", h.Login)
	r.Get("/me", h.Me)
	r.Post("/change-password", h.ChangePassword)
}
