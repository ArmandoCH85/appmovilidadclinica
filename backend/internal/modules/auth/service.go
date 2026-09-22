package auth

import (
	"context"
	"errors"
	"time"

	"github.com/go-chi/jwtauth/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// tokenTTL define la vigencia del JWT. 1 año: la sesion no debe vencer sola
// (los conductores usan la app todo el dia). La revocacion es inmediata via
// el guard de users.active (middleware activeUserGuard): suspender a un
// usuario corta su sesion al instante aunque el token siga vigente.
const tokenTTL = 365 * 24 * time.Hour

// AuthService define las operaciones de dominio del modulo.
type AuthService interface {
	// Login valida credenciales y devuelve un JWT firmado + el usuario.
	// El identifier puede ser DNI o username (resuelto por el repositorio).
	Login(ctx context.Context, identifier, password string) (string, User, error)

	// ChangePassword verifica la clave actual con bcrypt y la reemplaza por
	// la nueva (hasheada con bcrypt). El userID sale del JWT, nunca del body:
	// un usuario solo puede cambiar su propia clave.
	ChangePassword(ctx context.Context, userID int64, currentPassword, newPassword string) error
}

// authService es la implementacion concreta.
type authService struct {
	repo AuthRepository
	auth *jwtauth.JWTAuth
}

// NewService construye el servicio. secret es la clave HS256 del JWT;
// proviene de os.Getenv("JWT_SECRET") en main.go.
func NewService(repo AuthRepository, secret string) AuthService {
	return &authService{
		repo: repo,
		auth: jwtauth.New("HS256", []byte(secret), nil),
	}
}

// Login orquesta la verificacion de bcrypt y la emision del JWT HS256.
// Flujo:
//  1. Carga el usuario por document_number o username (repositorio decide).
//  2. Compara el hash con bcrypt.CompareHashAndPassword.
//  3. Emite un JWT con claims {user_id, role, full_name, employee_code,
//     exp, iat} para que /me no toque la BD.
//
// Ante cualquier fallo se devuelve UnauthorizedError con el mismo mensaje
// ("credenciales invalidas") para no filtrar si el usuario no existe vs
// password incorrecto (mejor practica de seguridad).
func (s *authService) Login(ctx context.Context, identifier, password string) (string, User, error) {
	user, err := s.repo.GetUserByIdentifier(ctx, identifier)
	if err != nil {
		var nf apperror.NotFoundError
		if errors.As(err, &nf) {
			return "", User{}, apperror.UnauthorizedError{Reason: "credenciales invalidas"}
		}
		return "", User{}, err
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(password)); err != nil {
		return "", User{}, apperror.UnauthorizedError{Reason: "credenciales invalidas"}
	}

	now := time.Now()
	claims := map[string]any{
		"user_id":       user.ID,
		"role":          user.Role,
		"full_name":     user.FullName,
		"employee_code": user.EmployeeCode,
		"iat":           now.Unix(),
		"exp":           now.Add(tokenTTL).Unix(),
	}
	_, tokenString, err := s.auth.Encode(claims)
	if err != nil {
		return "", User{}, apperror.InternalError{Err: err}
	}
	return tokenString, user, nil
}

// ChangePassword implementa POST /auth/change-password. Flujo:
//  1. Carga el usuario por id (del JWT).
//  2. Verifica la clave actual con bcrypt. Si falla, Unauthorized con mensaje
//     accionable (a diferencia de Login, aqui NO hay riesgo de enumerar
//     usuarios: el caller ya esta autenticado como ese userID).
//  3. Valida la nueva: minimo 8 caracteres y distinta a la actual.
//  4. Hashea con bcrypt.DefaultCost y persiste via el repositorio.
func (s *authService) ChangePassword(ctx context.Context, userID int64, currentPassword, newPassword string) error {
	if currentPassword == "" {
		return apperror.ValidationError{Field: "current_password", Reason: "requerido"}
	}
	if len(newPassword) < 8 {
		return apperror.ValidationError{Field: "new_password", Reason: "minimo 8 caracteres"}
	}
	if newPassword == currentPassword {
		return apperror.ValidationError{Field: "new_password", Reason: "debe ser distinta a la actual"}
	}

	user, err := s.repo.GetUserByID(ctx, userID)
	if err != nil {
		return err
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(currentPassword)); err != nil {
		return apperror.UnauthorizedError{Reason: "La clave actual es incorrecta."}
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return apperror.InternalError{Err: err}
	}
	if err := s.repo.UpdatePasswordHash(ctx, userID, string(hash)); err != nil {
		return apperror.InternalError{Err: err}
	}
	return nil
}
