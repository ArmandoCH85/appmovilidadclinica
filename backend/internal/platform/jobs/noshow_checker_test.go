package jobs

import "testing"

// TestRunNoShowChecker_DeferredToIntegration documenta la intencion de testeo
// del marcador automatico de NO_SHOW y su postergacion a tests de integracion.
//
// RunNoShowChecker toma *sql.DB directamente (no una interfaz inyectable).
// Un test unitario del cuerpo SQL requeriria:
//
//	a) sqlmock (github.com/DATA-DOG/go-sqlmock): excluido por ponytail-audit
//	   (no se agregan deps de test mas alla de testify).
//	b) una MariaDB de test real: excluida en Phase 4 (solo tests unitarios).
//
// La logica de markNoShowTx replica el cuerpo de sp_mark_reservation_no_show
// sin la validacion del conductor (el job no tiene actor driver). Esa logica
// SQL se valida contra una BD real en la fase de integracion.
//
// Lo que SI cubren estos tests (cuando haya DB de integracion):
//   - Reserva CONFIRMED cuya tolerancia vencio -> estado NO_SHOW + segmentos
//     liberados + 2 eventos de bitacora (NO_SHOW, SEGMENTS_RELEASED).
//   - Reserva CONFIRMED dentro de tolerancia -> sin accion.
//   - Reserva BOARDED -> no seleccionada por el WHERE (solo CONFIRMED).
func TestRunNoShowChecker_DeferredToIntegration(t *testing.T) {
	t.Skip("diferido a tests de integracion: RunNoShowChecker requiere *sql.DB real (sin sqlmock por ponytail)")
}

// TestStartNoShowChecker_DeferredToIntegration: misma razon que el anterior;
// StartNoShowChecker lanza RunNoShowChecker en una goroutine y requiere ctx
// + *sql.DB. Sin DB inyectable, el test diferido valida el ciclo catch-up +
// ticker + ctx.Done().
func TestStartNoShowChecker_DeferredToIntegration(t *testing.T) {
	t.Skip("diferido a tests de integracion: StartNoShowChecker requiere *sql.DB real")
}
