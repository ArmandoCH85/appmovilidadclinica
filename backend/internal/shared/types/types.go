// Package types define structs transversales mínimos compartidos entre
// módulos del backend.
//
// El ponytail-audit cortó PageResult[T], GeoPoint y ValidationErrorResponse:
// ninguno tenía consumidor real en el MVP. Lo que queda es lo que los
// handlers de listado necesitan: paginación de entrada y derived SQL.
package types

// PaginationParams normaliza page/page_size que llegan por query string.
// El receptor corrige los valores inválidos (cero o negativo) con defaults.
type PaginationParams struct {
	Page     int
	PageSize int
}

// Defaults aplicados por Parse cuando el cliente no envía valores válidos.
const (
	DefaultPage     = 1
	DefaultPageSize = 20
	// MaxPageSize acota el límite superior para no permitir scans enormes.
	MaxPageSize = 100
)

// Normalize aplica los defaults y el límite superior. Llamar antes de usar
// Offset/Limit: un handler que reciba page=0 ó page_size=0 recibe aquí los
// defaults en lugar de escribir lógica condicional repetida.
func (p *PaginationParams) Normalize() {
	if p.Page < 1 {
		p.Page = DefaultPage
	}
	if p.PageSize < 1 {
		p.PageSize = DefaultPageSize
	}
	if p.PageSize > MaxPageSize {
		p.PageSize = MaxPageSize
	}
}

// Offset devuelve el OFFSET de SQL (Page-1)*PageSize. El receptor ya debe
// estar normalizado; por seguridad se aplica floor en 0.
func (p PaginationParams) Offset() int {
	o := (p.Page - 1) * p.PageSize
	if o < 0 {
		return 0
	}
	return o
}

// Limit devuelve el LIMIT de SQL. Se pasa directo al repositorio; coincide
// con PageSize una vez normalizado.
func (p PaginationParams) Limit() int {
	return p.PageSize
}
