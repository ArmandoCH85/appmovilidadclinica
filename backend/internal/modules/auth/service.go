package auth

import (
	"context"
	"errors"
	"time"

	"github.com/go-chi/jwtauth/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// tokenTTL define la vigencia del JWT. Sin refresh token en el MVP: 24h.
const tokenTTL = 24 * time.Hour

// AuthService define las operaciones de dominio del modulo.
type AuthService interface {
	// Login valida credenciales y devuelve un JWT firmado + el usuario.
	Login(ctx context.Context, documentNumber, password string) (string, User, error)
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
//  1. Carga el usuario por document_number.
//  2. Compara el hash con bcrypt.CompareHashAndPassword.
//  3. Emite un JWT con claims {user_id, role, full_name, employee_code,
//     exp, iat} para que /me no toque la BD.
//
// Ante cualquier fallo se devuelve UnauthorizedError con el mismo mensaje
// ("credenciales invalidas") para no filtrar si el usuario no existe vs
// password incorrecto (mejor practica de seguridad).
func (s *authService) Login(ctx context.Context, documentNumber, password string) (string, User, error) {
	user, err := s.repo.GetUserByDocument(ctx, documentNumber)
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
