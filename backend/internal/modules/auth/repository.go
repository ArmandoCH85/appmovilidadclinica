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
	// GetUserByDocument carga el usuario activo por numero de documento.
	// Devuelve apperror.NotFoundError si no existe.
	GetUserByDocument(ctx context.Context, documentNumber string) (User, error)
}

// authRepository es la implementacion concreta con database/sql.
type authRepository struct {
	db *sql.DB
}

// NewRepository construye el repositorio. Requiere el pool compartido.
func NewRepository(db *sql.DB) AuthRepository {
	return &authRepository{db: db}
}

// GetUserByDocument busca un usuario activo por document_number. active=1
// filtra empleados dados de baja sin costar logica extra en Go.
func (r *authRepository) GetUserByDocument(ctx context.Context, documentNumber string) (User, error) {
	const q = `
        SELECT id, employee_code, document_number, password_hash,
               full_name, role, department, phone, active
          FROM users
         WHERE document_number = ?
           AND active = 1
         LIMIT 1`

	var u User
	var department, phone sql.NullString
	err := r.db.QueryRowContext(ctx, q, documentNumber).Scan(
		&u.ID, &u.EmployeeCode, &u.DocumentNumber, &u.PasswordHash,
		&u.FullName, &u.Role, &department, &phone, &u.Active,
	)
	if err != nil {
		if nfErr := dberr.NotFound(err, "usuario", documentNumber); nfErr != err {
			return User{}, nfErr
		}
		return User{}, fmt.Errorf("buscando usuario por documento: %w", err)
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
