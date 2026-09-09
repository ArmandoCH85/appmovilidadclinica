# Spec — Botón "Mis reservas" en TopAppBar de TripSearchScreen

**Fecha**: 2026-09-09
**Estado**: Implementado (commits `d08a79f`, `41a303d`). Pendiente verificación visual del usuario en el emulador corriendo.
**Alcance**: Cambio visual/iconográfico mínimo. Cero impacto en lógica, navegación, ViewModel o build.

## Goal

Hacer que el botón "Mis reservas" en la TopAppBar de `TripSearchScreen` comunique mejor la acción que ejecuta (ver la lista de reservas de viaje propias) mediante un ícono Material semánticamente correcto, sin alterar el layout ni la lógica de la pantalla.

## Background / Contexto

Hoy el botón usa `Icons.Default.ConfirmationNumber` (ícono de ticket de lotería / número de confirmación). Es ambiguo: no comunica "reservas de viaje", podría confundirse con un ticket de soporte, un código de confirmación o una entrada de evento.

Restricciones del proyecto (de `desarrollo_pasajero.md`):

- Idioma español formal "usted" en todos los textos visibles (sección 10.3). El `contentDescription` ya cumple.
- Strings hardcodeados en Composables (deuda técnica documentada, NO se aborda acá).
- Material 3 + Jetpack Compose. `material-icons-extended` ya está en el version catalog.
- Patrón de la app: TopAppBar con dos `IconButton` (reservas + logout) separados por `VerticalDivider`. El logout usa `Icons.AutoMirrored.Filled.Logout` (semánticamente claro) — el botón de reservas queda como el eslabón débil.

## Approach

Cambiar el ícono `ConfirmationNumber` por `BookOnline`, que es el ícono canónico de Material para acciones de reserva en general (literalmente "book online"). Mantener todo lo demás intacto: `IconButton`, `contentDescription`, posición, tamaño, color heredado del theme.

### Por qué `BookOnline` y no otra opción

| Ícono alternativo | Veredicto |
|---|---|
| `Icons.Default.BookOnline` | **Recomendado.** Semánticamente exacto para "gestionar reservas". Distintivo, no se confunde con calendario o recibo. |
| `Icons.AutoMirrored.Filled.EventNote` | Cubre "lista de reservas" pero se confunde con un calendario de eventos. |
| `Icons.Default.Bookmark` / `BookmarkBorder` | Genérico, ambiguo entre "guardar favorito" y "reserva". |
| `Icons.Default.Receipt` | Más "comprobante/pago" que "reservas". |
| `Icons.Default.DirectionsBus` | Comunica "viaje" pero no "mis reservas". Ya usado en `TripResultCard` para el vehículo — reutilizar generaría confusión. |

`BookOnline` gana porque la acción que se ejecuta al tocar es VER/GESTIONAR las reservas (no un calendario, no un recibo, no un marcador).

## Specific Changes

**Único archivo tocado**: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/tripsearch/TripSearchScreen.kt`

### Cambio 1 — Import (línea 20)

Antes:
```kotlin
import androidx.compose.material.icons.filled.ConfirmationNumber
```

Después:
```kotlin
import androidx.compose.material.icons.filled.BookOnline
```

### Cambio 2 — Ícono en TopAppBar actions (línea 84)

Antes:
```kotlin
IconButton(onClick = onOpenReservations) {
    Icon(Icons.Default.ConfirmationNumber, contentDescription = "Mis reservas")
}
```

Después:
```kotlin
IconButton(onClick = onOpenReservations) {
    Icon(Icons.Default.BookOnline, contentDescription = "Mis reservas")
}
```

### Lo que NO cambia (scope discipline)

- `IconButton` wrapper, tamaño, posición
- `contentDescription = "Mis reservas"` (español formal intacto)
- `VerticalDivider` entre reservas y logout (línea 86-90)
- Color/tint (sigue heredando del theme — Material 3 `TopAppBar` aplica el tint correcto)
- `MyReservationsScreen.kt`, `NavGraph.kt`, `Screen.kt`, `TripSearchViewModel.kt`: cero cambios
- No se agrega badge, ni texto visible, ni lógica de conteo (descartado por el usuario: la opción más KISS es solo cambiar el ícono)

## Out of Scope (explícito)

- Reestructurar la TopAppBar a un `NavigationBar`/`BottomBar`
- Agregar contador de reservas activas vía `BadgedBox`
- Agregar texto visible al botón (queda como `IconButton`)
- Externalizar strings a `strings.xml` (deuda técnica existente, no parte de este pedido)
- Cambiar el ícono de logout
- Agregar tests automatizados (per sección 12 del doc: tests solo cuando hay lógica nueva no trivial — acá no hay lógica nueva)

## Acceptance Criteria

1. El botón en `TripSearchScreen` muestra el ícono `BookOnline` (un libro/cuaderno con un check/ticket, según render de Material).
2. El `contentDescription` sigue siendo "Mis reservas" (verificable con TalkBack).
3. Tocar el botón sigue navegando a `Screen.MyReservations` (sin cambios de comportamiento).
4. La posición, tamaño y relación con el `VerticalDivider` y el botón de logout son idénticos al estado actual.
5. La app compila sin warnings nuevos (revisar tras el cambio).
6. No se introducen dependencias nuevas.

## Risks / Notes

- **Riesgo de regresión visual**: ninguno esperado. `BookOnline` está en `material-icons-extended` (ya disponible en el version catalog). Si por alguna razón no estuviera disponible en runtime, el build fallaría en compilación — no hay riesgo silencioso.
- **Subjetividad de ícono**: si el usuario final lo lee distinto, el `contentDescription` ("Mis reservas") es el que comanda el significado para accesibilidad. El ícono es pista visual secundaria.
- **Memo de sesión**: el package real del APK es `com.sitech.clinica.empleados`, no `com.appmovilidadclinica.passenger` (desactualización documentada de `desarrollo_pasajero.md`, no afectada por este cambio).