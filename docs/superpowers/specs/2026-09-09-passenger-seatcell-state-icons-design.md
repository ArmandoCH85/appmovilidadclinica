# Spec — Iconos de estado en SeatCell (semáforo de asientos)

**Fecha**: 2026-09-09
**Estado**: Aprobado por el usuario (diseño) — pendiente revisión del spec escrito
**Alcance**: Mejora visual/de accesibilidad. Cero impacto en lógica de selección, ViewModel, build dependencies o navegación.

## Goal

Agregar un canal visual secundario (ícono + refuerzo de borde) a los 3 estados de un `SeatCell` (Disponible / Seleccionado / Ocupado), de modo que el estado sea distinguible sin depender solo del color. Cumple WCAG 1.4.1 (Use of Color) sin romper la paleta emerald del proyecto.

## Background / Contexto

Hoy el `SeatCell` usa solo color para distinguir estados:
- Disponible → `primaryContainer` (Emerald200, verde claro)
- Seleccionado → `primary` (Emerald700, verde oscuro)
- Ocupado → `surfaceVariant` (gris)

El estado **Seleccionado** ya tiene ícono Check en top-end. El estado **Ocupado** no tiene ícono. **Disponible** tampoco.

Problemas concretos (verificado contra el código actual de `SeatSelectionScreen.kt:177-227`):

1. ~8% de usuarios masculinos con daltonismo rojo-verde confunden los2 tonos de verde (Disponible vs Seleccionado). El `contentDescription` semántico cubre el canal textual, pero falta el canal de forma.
2. "Ocupado en gris" es atípico — la convención universal es rojo, candado, X, o tachado.
3. Para un usuario nuevo mirando el grid sin leer la leyenda, los2 verdes se sienten equivalentes.

Restricciones del proyecto (de `desarrollo_pasajero.md`):

- Idioma español formal "usted" en todos los textos visibles (sección 10.3). **No se modifica ningún texto** en este cambio.
- Material 3 + Jetpack Compose. `material-icons-extended` ya está en el version catalog (verificado en brainstorming previo).
- Tema emerald: no se introduce un 3er color (rojo) — se mantiene la coherencia visual con el panel admin.
- `contentDescription` semántica preservada (TalkBack): "Asiento X, seleccionado/disponible/ocupado" sigue intacto. Los íconos visuales son decorativos (`contentDescription = null`), redundantes con el canal textual existente.

## Approach

Agregar ícono candado a Ocupado, reforzar el borde del Seleccionado de 2dp a 3dp, e incluir los íconos en la leyenda. Disponible queda sin ícono (suficiente con el paralelismo de los otros 2 estados).

### Por qué Lock (candado) para Ocupado

- Decisión del usuario sobre alternativas: Close (X), Block (prohibido), Lock (candado), tachado.
- Lock es semánticamente "reservado por otro / ya tiene dueño" — más fuerte que "ocupado" en el contexto de reserva de asientos.
- Material Icons: `Icons.Filled.Lock` está en `material-icons-extended` (ya disponible, verificado).
- Mismo tamaño y posición que el check del Seleccionado (14dp, top-end, padding 2dp) → consistencia visual.

### Por qué borde 3dp (no más fuerte)

- En una celda de 72dp, 2dp se nota poco cuando hay muchos asientos juntos.
- 3dp es el umbral donde un borde se siente "fuerte" sin saturar.
- 4dp+ empieza a competir con el tamaño del label del asiento.

### Por qué leyenda con íconos

- Refuerza el paralelismo entre lo que el usuario ve en la leyenda y lo que ve en el grid.
- Sin íconos en la leyenda, los íconos en las celdas podrían parecer "decoración aleatoria".

## Specific Changes

**Único archivo tocado**: `passenger-android/app/src/main/java/com/appmovilidadclinica/passenger/presentation/seatselection/SeatSelectionScreen.kt`

### Cambio 1 — Imports nuevos (Lock + ImageVector)

Agregar al bloque de imports:

```kotlin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.vector.ImageVector
```

El primero es el ícono para Ocupado (en `material-icons-extended`, ya disponible). El segundo es el tipo del parámetro `icon` que gana `LegendItem` (ver Cambio 5). Ambos imports no existen hoy en el archivo — confirmé con grep que solo está importado `androidx.compose.ui.graphics.Color`.

### Cambio 2 — SeatCell: agregar ícono candado al estado Ocupado

En `SeatCell`, dentro del `Box` (línea ~196-226), después del bloque `if (selected) { ... }`, agregar:

```kotlin
if (!seat.isSelectable) {
    Icon(
        Icons.Filled.Lock,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(2.dp)
            .size(14.dp),
    )
}
```

Tint: `onSurfaceVariant` (gris oscuro) sobre fondo `surfaceVariant` (gris claro) — contraste suficiente para WCAG AA en celdas 72dp.

### Cambio 3 — SeatCell: reforzar borde del Seleccionado

En la línea ~202, cambiar:

```kotlin
Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
```

a:

```kotlin
Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
```

### Cambio 4 — SeatLegend: agregar íconos a las LegendItems

En `SeatLegend` (línea ~229-239), cambiar las llamadas:

```kotlin
LegendItem(color = MaterialTheme.colorScheme.primaryContainer, label = "Disponible")
LegendItem(color = MaterialTheme.colorScheme.primary,         label = "Seleccionado")
LegendItem(color = MaterialTheme.colorScheme.surfaceVariant,  label = "Ocupado")
```

a:

```kotlin
LegendItem(color = MaterialTheme.colorScheme.primaryContainer, label = "Disponible",  icon = null)
LegendItem(color = MaterialTheme.colorScheme.primary,         label = "Seleccionado", icon = Icons.Filled.Check)
LegendItem(color = MaterialTheme.colorScheme.surfaceVariant,  label = "Ocupado",      icon = Icons.Filled.Lock)
```

### Cambio 5 — LegendItem: agregar parámetro `icon: ImageVector? = null`

En `LegendItem` (línea ~241-253), cambiar la firma y el body:

**Firma**:

```kotlin
private fun LegendItem(
    color: Color,
    label: String,
    icon: ImageVector? = null,
)
```

**Body**: dentro del `Box` que renderiza el cuadradito de color, agregar condicional:

```kotlin
Box(
    modifier = Modifier
        .size(12.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(color),
) {
    if (icon != null) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,  // o onSurfaceVariant — ver Risks
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(1.dp)
                .size(10.dp),
        )
    }
}
```

### Lo que NO cambia (scope discipline)

- Colores de los3 estados: siguen siendo `primaryContainer` / `primary` / `surfaceVariant`
- Tamaño de celda: 72dp
- Lógica de selección en `SeatSelectionViewModel.kt`: cero cambios
- `SeatSelectionScreen.kt`: no se toca la estructura del Scaffold ni del grid
- `NavGraph.kt`, `Screen.kt`, `TripSearchScreen.kt`, etc.: cero cambios
- Textos visibles (no se cambia ningún string): la sección 10.3 del doc del proyecto no se ve afectada
- `contentDescription` semántica del SeatCell: sigue siendo "Asiento X, seleccionado/disponible/ocupado" — el cambio es puramente visual

## Out of Scope (explícito)

- Cambiar la paleta emerald por una paleta de 3 colores distintos (rojo para ocupado)
- Cambiar los tamaños de celda o la estructura del grid (eso fue el commit previo `71c7e2e`)
- Tachado del label del asiento en Ocupado (alternativa considerada, descartada por el usuario)
- Cambiar el ícono Check del Seleccionado por otro (mantiene Check)
- Agregar animación de transición entre estados
- Tocar `SeatSelectionViewModel.kt` (lógica intacta)
- Tests automatizados (no aplica — cambio puramente visual; mismo argumento que commits previos)
- Mover leyenda o cambiar su layout (sigue siendo Row con SpaceEvenly)

## Acceptance Criteria

1. Un `SeatCell` con `seat.isSelectable = false` muestra el ícono candado en top-end (mismo tamaño/posición que el check: 14dp, padding 2dp). Verificable con `uiautomator dump` por bounds del ícono.
2. Un `SeatCell` con `selected = true` tiene un borde de 3dp alrededor del cuadrado redondeado (no 2dp como antes).
3. Un `SeatCell` con `isSelectable = true` y `selected = false` no muestra ningún ícono (sin cambio respecto al estado actual).
4. La `SeatLegend` muestra íconos Check (Seleccionado) y Lock (Ocupado) dentro de los cuadraditos de color respectivos (10dp, top-end, padding 1dp). Disponible no muestra ícono en su cuadradito.
5. El `contentDescription` semántica del SeatCell sigue intacto: "Asiento X, seleccionado/disponible/ocupado". Verificable con TalkBack o `uiautomator dump`.
6. La app compila sin warnings nuevos (inspección del output de Gradle).
7. No se introducen dependencias nuevas (`Icons.Filled.Lock` ya está en `material-icons-extended`).

## Risks / Notes

- **Riesgo visual**: el candado en celdas chicas (72dp) podría sentirse pesado si hay muchos asientos ocupados juntos. Mitigación: el ícono es pequeño (14dp) y está en top-end con padding 2dp — no compite con el label central. Verificar empíricamente con screenshot del emulador post-cambio.
- **Tint del ícono en leyenda**: el `Icon` dentro del `LegendItem` lleva `tint = MaterialTheme.colorScheme.onSurface`. Esto podría tener poco contraste con el fondo emerald200 del cuadradito de "Seleccionado" (verde claro con ícono en negro = OK contraste, pero podría ser más elegante usar `onPrimary` que es blanco). Si en la verificación visual empírica el contraste es insuficiente, ajustar a `onPrimary` o `onSurfaceVariant`. Documentar el ajuste si se hace.
- **Posible efecto colateral en cambios uncommited del usuario**: el archivo `SeatSelectionScreen.kt` tiene cambios uncommited (del usuario) además de mi commit previo `71c7e2e`. Mi fix anterior ya usó el patrón stash-and-isolate — este spec mantiene ese mismo patrón: aplicar cambios solo al archivo en estado HEAD, commitear solo mis cambios, restaurar stash del usuario. Ver `docs/superpowers/specs/2026-09-09-passenger-reservations-button-design.md` y commit `71c7e2e` para el patrón.
- **Build policy (AGENTS.md)**: el agente NO corre `./gradlew assembleDebug` automáticamente. La verificación visual se hace después de que el usuario buildee, instale y tome screenshot (o lo pida explícitamente, igual que ocurrió con el fix del botón).
- **Memo de sesión**: el package real del APK es `com.sitech.clinica.empleados`, no `com.appmovilidadclinica.passenger`. NO afecta este cambio (no se toca el manifest).