# Reporte de incidentes del pasajero — Design

Fecha: 2026-09-10. Estado: propuesto, pendiente de revisión.

## 1. Contexto

El conductor ya reporta incidencias (`POST /api/driver/trips/{id}/incidents`
→ tabla `trip_incidents`, tipos `BREAKDOWN | DELAY | ACCIDENT | OTHER`) y el
admin las lista/resuelve (`GET/PATCH /api/admin/incidents`). El endpoint del
conductor exige rol `DRIVER` (`requireDriver`, `driver/service.go:47`), por lo
que el pasajero (rol `WORKER`) no puede reusarlo. El cliente pide un flujo
aparte para que el pasajero reporte incidentes desde su app.

## 2. Objetivo

El pasajero, desde el menú de perfil de la app, reporta un incidente ligado a
una de sus reservas activas (tipo + descripción). El reporte cae en la misma
tabla `trip_incidents` con `reported_by_user_id` = pasajero, así el admin lo
ve y gestiona con su flujo actual sin cambios en el panel.

## 3. No-objetivos (v1)

- Evidencia fotográfica (cámara, subida, storage, visor en admin).
- Cambios en el panel admin y en el flujo del conductor.
- Categorías propias de pasajero: se reusa el enum existente
  (`BREAKDOWN | DELAY | ACCIDENT | OTHER`) para no migrar nada. `DELAY` cubre
  el caso más común ("el bus no pasó / llegó tarde"); el resto va en `OTHER`
  con el detalle en la descripción.
- Historial de "mis reportes" en la app.

## 4. Backend

Nuevo endpoint en el módulo `booking` (dueño de las reservas):

```
POST /api/reservations/{id}/incidents   (requiere JWT, rol WORKER)
Body: { "incident_type": "DELAY|...", "description": "..." }
→ 201 { "id": <n> }
```

Reglas del servicio (mismo estilo que `driver.ReportIncident`):

1. La reserva `{id}` debe existir y pertenecer al `user_id` del JWT; si no,
   404 (no filtrar existencia ajena: mismo mensaje exista o no).
2. Estado válido: `CONFIRMED` o `BOARDED`. `CANCELLED | NO_SHOW | COMPLETED`
   → 409 con mensaje accionable.
3. `incident_type` requerido, uno del enum; `description` requerida, mínimo
   10 caracteres, máximo 1000. Violaciones → 422 vía `validate`.
4. Inserta en `trip_incidents (trip_id, reported_by_user_id, incident_type,
   description)` con `trip_id` derivado de la reserva (el cliente nunca lo
   manda, igual que el driver ignora el `trip_id` del body).
5. Cualquier otro rol → 403. Sin JWT → 401 (automático por el authenticator).

Tests: éxito persiste con `trip_id` de la reserva y reporter = caller;
reserva ajena → 404; estado cancelada → 409; tipo inválido / descripción
corta → 422. Mock a mano, patrón de `auth/service_test.go`.

## 5. App pasajero

- **Entrada**: fila "Reportar incidente" en `ProfileMenu` (debajo de
  "Cambiar clave", con divisor), icono `Outlined.ReportProblem` en círculo.
- **Pantalla `ReportIncident`** (ruta tipada nueva `Screen.ReportIncident`,
  `onBack = popBackStack`):
  1. Selector de reserva activa: dropdown con las reservas `CONFIRMED |
     BOARDED` de `MyReservationsViewModel`, etiqueta
     `"<origen> → <destino> · <fecha>"`. Si no hay ninguna: texto
     "No tienes viajes activos para reportar." y botón deshabilitado.
  2. Campo tipo (dropdown con los 4 valores + etiquetas legibles).
  3. Campo descripción (multilínea, contador, helper "Mínimo 10 caracteres").
  4. Botón "Enviar reporte" píldora full-width (mismo estilo que
     "Guardar cambios" de Cambiar clave) + loading/spinner.
- **Capa data**: `ReportIncidentRequestDto(incident_type, description)` —
  el id de reserva viaja en el path, no en el body;
  `KtorReservationsApi.reportIncident(reservationId, body)` →
  `ReservationsRepository.reportIncident(...) : AppResult<Long>`.
- **Validaciones cliente** (inline en rojo, sin perder lo escrito):
  reserva requerida, tipo requerido, descripción 10–1000.
- **Éxito**: toast "Reporte enviado con éxito" + volver (mismo patrón que
  Cambiar clave: el toast sobrevive al pop).
- **Errores**: mensaje del backend tal cual (409 reserva no válida, 422);
  401 expira-sesión lo maneja el flujo global existente.

## 6. Flujo de datos

Perfil → tap "Reportar incidente" → nav a `ReportIncident` → elige reserva +
tipo + descripción → `ReservationsRepository.reportIncident` → `POST
/api/reservations/{id}/incidents` (Bearer automático) → 201 → toast + back.
El admin ve el reporte en `GET /api/admin/incidents` (distinguible por el
usuario reportante).

## 7. Testing

- Backend: unit tests del servicio (casos §4) + curl contra staging con token
  WORKER real (201 con reserva propia, 404 con ajena).
- App: verificación manual en emulador (sin reserva activa, validaciones,
  éxito, error 409 simulado si aplica). Sin tests UI automatizados (la app no
  tiene esa infraestructura).

## 8. Rollout

Desplegar el backend ANTES o junto con la app: la app contra un backend
viejo recibe 404 ("El recurso solicitado no existe"), igual que pasó con
change-password. Rama de trabajo actual: `feat/user-extra-fields`.
