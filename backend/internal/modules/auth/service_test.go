package auth

import (
	"context"
	"errors"
	"testing"

	"github.com/go-chi/jwtauth/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/crypto/bcrypt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/apperror"
)

// mockAuthRepo Cumple AuthRepository para tests. Mock a mano, sin mockery.
type mockAuthRepo struct {
	user User
	err  error
}

func (m *mockAuthRepo) GetUserByDocument(_ context.Context, _ string) (User, error) {
	return m.user, m.err
}

// mustHash genera un bcrypt hash valido para un password conocido.
func mustHash(t *testing.T, password string) string {
	t.Helper()
	b, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.MinCost)
	require.NoError(t, err)
	return string(b)
}

// testSecret es la clave HS256 usada en todos los tests de Login.
const testSecret = "test-secret-very-long-key-for-hs256-algorithm"

// decodeClaims parsea el token emitido y devuelve el mapa de claims para
// verificar su contenido. Usa el mismo jwtauth que el middleware de produccion.
func decodeClaims(t *testing.T, token string) map[string]any {
	t.Helper()
	ja := jwtauth.New("HS256", []byte(testSecret), nil)
	decoded, err := ja.Decode(token)
	require.NoError(t, err, "el token emitido por Login debe decodificarse con el secreto")
	ctx := jwtauth.NewContext(context.Background(), decoded, nil)
	_, claims, err := jwtauth.FromContext(ctx)
	require.NoError(t, err)
	return claims
}

func TestLogin_ValidCredentials_ReturnsToken(t *testing.T) {
	repo := &mockAuthRepo{
		user: User{
			ID:             int64(42),
			EmployeeCode:   "EMP001",
			DocumentNumber: "12345678",
			PasswordHash:   mustHash(t, "s3cret"),
			FullName:       "Juan Perez",
			Role:           "WORKER",
			Active:         true,
		},
	}
	svc := NewService(repo, testSecret)

	token, user, err := svc.Login(context.Background(), "12345678", "s3cret")
	require.NoError(t, err)
	assert.NotEmpty(t, token, "el token JWT no puede ser vacio")
	assert.Equal(t, int64(42), user.ID)

	claims := decodeClaims(t, token)
	assert.Equal(t, "WORKER", claims["role"], "el claim role debe viajar en el JWT")
	assert.NotNil(t, claims["exp"], "el claim exp debe estar presente")
}

func TestLogin_WrongPassword_ReturnsUnauthorized(t *testing.T) {
	repo := &mockAuthRepo{
		user: User{
			ID:             int64(42),
			DocumentNumber: "12345678",
			PasswordHash:   mustHash(t, "correcto"),
			Role:           "WORKER",
			Active:         true,
		},
	}
	svc := NewService(repo, testSecret)

	_, _, err := svc.Login(context.Background(), "12345678", "incorrecto")
	require.Error(t, err)
	var ue apperror.UnauthorizedError
	require.True(t, errors.As(err, &ue), "password incorrecto debe mapear a UnauthorizedError")
}

func TestLogin_UserNotFound_ReturnsUnauthorized(t *testing.T) {
	// El servicio traduce NotFound del repositorio a Unauthorized para no
	// filtrar si el usuario existe vs password incorrecto (mejor practica).
	repo := &mockAuthRepo{err: apperror.NotFoundError{Entity: "usuario", ID: "999"}}
	svc := NewService(repo, testSecret)

	_, _, err := svc.Login(context.Background(), "999", "x")
	require.Error(t, err)
	var ue apperror.UnauthorizedError
	require.True(t, errors.As(err, &ue), "usuario inexistente debe mapear a Unauthorized")
}

func TestLogin_InactiveUser_ReturnsUnauthorized(t *testing.T) {
	// El repositorio real filtra active=1: un usuario inactivo se reporta como
	// NotFound y el servicio lo traduce a Unauthorized (mismo flujo que el
	// caso de usuario inexistente).
	repo := &mockAuthRepo{err: apperror.NotFoundError{Entity: "usuario", ID: "888"}}
	svc := NewService(repo, testSecret)

	_, _, err := svc.Login(context.Background(), "888", "x")
	require.Error(t, err)
	var ue apperror.UnauthorizedError
	require.True(t, errors.As(err, &ue), "usuario inactivo debe mapear a Unauthorized")
}
