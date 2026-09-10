// Package auth implementa el modulo de autenticacion: login JWT y endpoint
// /me. Sigue la arquitectura de 3 capas: repositorio (SQL) -> servicio
// (reglas de dominio: basta bcrypt + emision JWT) -> handler (HTTP fino).
package auth

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/ArmandoCH85/appmovilidadclinica/backend/internal/shared/dberr"
)

// User refleja la fila de users necesaria para autenticar y para /me. No
// incluye todos los campos de la tabla: solo los que login + /me consumen.
type User struct {
	ID             int64   `json:"id"`
	EmployeeCode   string  `json:"employee_code"`
	DocumentNumber string  `json:"document_number"`
	Username       *string `json:"username,omitempty"`
	PasswordHash   string  `json:"-"` // nunca se envia al cliente
	FullName       string  `json:"full_name"`
	Role           string  `json:"role"`
	Department     *string `json:"department,omitempty"`
	Phone          *string `json:"phone,omitempty"`
	Active         bool    `json:"active"`
}

// AuthRepository abstrae el acceso a users. La interfaz permite mockear a
// mano en tests sin mockery (decision del ponytail-audit).
type AuthRepository interface {
	// GetUserByIdentifier carga el usuario activo por DNI o username.
	// Devuelve apperror.NotFoundError si no existe.
	GetUserByIdentifier(ctx context.Context, identifier string) (User, error)

	// GetUserByID carga el usuario activo por su id. Devuelve
	// apperror.NotFoundError si no existe o esta inactivo.
	GetUserByID(ctx context.Context, id int64) (User, error)

	// UpdatePasswordHash reemplaza el hash bcrypt del usuario.
	UpdatePasswordHash(ctx context.Context, id int64, hash string) error
}

// authRepository es la implementacion concreta con database/sql.
type authRepository struct {
	db *sql.DB
}

// NewRepository construye el repositorio. Requiere el pool compartido.
func NewRepository(db *sql.DB) AuthRepository {
	return &authRepository{db: db}
}

// GetUserByIdentifier busca un usuario activo por DNI o por username. El
// ORDER BY prioriza el match por DNI (mas confiable) y luego cae al
// username. active=1 filtra empleados dados de baja sin logica extra en Go.
func (r *authRepository) GetUserByIdentifier(ctx context.Context, identifier string) (User, error) {
	const q = `
        SELECT id, employee_code, document_number, username, password_hash,
               full_name, role, department, phone, active
          FROM users
         WHERE (document_number = ? OR username = ?)
           AND active = 1
         ORDER BY (document_number = ?) DESC
         LIMIT 1`

	var u User
	var username, department, phone sql.NullString
	err := r.db.QueryRowContext(ctx, q, identifier, identifier, identifier).Scan(
		&u.ID, &u.EmployeeCode, &u.DocumentNumber, &username, &u.PasswordHash,
		&u.FullName, &u.Role, &department, &phone, &u.Active,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "usuario", identifier); nfErr != err {
			return User{}, nfErr
		}
		return User{}, fmt.Errorf("buscando usuario por identificador: %w", err)
	}
	if username.Valid {
		s := username.String
		u.Username = &s
	}
	if department.Valid {
		s := department.String
		u.Department = &s
	}
	if phone.Valid {
		s := phone.String
		u.Phone = &s
	}
	return u, nil
}

// GetUserByID busca un usuario activo por id. Filtra active=1 igual que
// GetUserByIdentifier: un usuario dado de baja se reporta como NotFound.
func (r *authRepository) GetUserByID(ctx context.Context, id int64) (User, error) {
	const q = `
        SELECT id, employee_code, document_number, username, password_hash,
               full_name, role, department, phone, active
          FROM users
         WHERE id = ?
           AND active = 1
         LIMIT 1`

	var u User
	var username, department, phone sql.NullString
	err := r.db.QueryRowContext(ctx, q, id).Scan(
		&u.ID, &u.EmployeeCode, &u.DocumentNumber, &username, &u.PasswordHash,
		&u.FullName, &u.Role, &department, &phone, &u.Active,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "usuario", id); nfErr != err {
			return User{}, nfErr
		}
		return User{}, fmt.Errorf("buscando usuario por id: %w", err)
	}
	if username.Valid {
		s := username.String
		u.Username = &s
	}
	if department.Valid {
		s := department.String
		u.Department = &s
	}
	if phone.Valid {
		s := phone.String
		u.Phone = &s
	}
	return u, nil
}

// UpdatePasswordHash reemplaza el hash bcrypt del usuario. El servicio ya
// trae el hash calculado; el repositorio solo persiste (chokepoint de hash
// en el servicio, mismo patron que admin.CreateUser/UpdateUser).
func (r *authRepository) UpdatePasswordHash(ctx context.Context, id int64, hash string) error {
	const q = `UPDATE users SET password_hash = ? WHERE id = ?`
	_, err := r.db.ExecContext(ctx, q, hash, id)
	if err != nil {
		return fmt.Errorf("actualizando hash de clave: %w", err)
	}
	return nil
}
