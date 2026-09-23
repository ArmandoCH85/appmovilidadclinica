package com.appmovilidadclinica.driver.shared.ui.common

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Escala tipografica y dimensiones accesibles para la app del conductor.
 *
 * Contexto de uso: conductores de 60 a 70 años, frecuentemente con el
 * telefono a distancia de lectura y con el tamaño de fuente del sistema
 * aumentado. La escala por defecto de Material (bodyMedium = 14sp,
 * labelSmall = 11sp) es demasiado pequeña para ese caso de uso, asi que la
 * pantalla de detalle de viaje usa esta escala explicita:
 *
 *  - Texto principal: >= 18sp.
 *  - Titulos / encabezados: >= 24sp.
 *  - Texto secundario (nunca informacion critica): 16sp.
 *
 * Todas las medidas estan en `sp`, por lo que escalan con el tamaño de
 * fuente del sistema (Ajustes > Pantalla > Tamaño de fuente) sin cambios.
 *
 * Los estilos de Material siguen existiendo para el resto de pantallas;
 * esta escala es la referencia para toda la UI nueva o migrada.
 */
object DriverText {

    /** 30sp — numero o dato protagonista (hora de la accion principal). */
    val hero = TextStyle(
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 26sp — titulo de la tarjeta de "que hacer ahora". */
    val actionTitle = TextStyle(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 24sp — titulo de seccion / encabezado de pantalla. */
    val title = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 20sp — subtitulo, nombre de parada, dato relevante. */
    val subtitle = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 18sp — texto principal (parrafos, datos de la tarjeta). */
    val body = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
    )

    /** 18sp semibold — texto principal que debe resaltar sin cambiar tamaño. */
    val bodyStrong = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 20sp semibold — texto de los botones de accion (incluye la principal). */
    val button = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 16sp semibold — etiquetas cortas (chips de estado). */
    val chip = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 16sp — texto de apoyo. Nunca comunica informacion critica por si solo. */
    val supporting = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
}

/**
 * Dimensiones de accesibilidad usadas por la pantalla de detalle de viaje.
 *
 * `heightIn(min = ...)` en vez de `height(...)`: el boton crece cuando el
 * conductor aumenta el tamaño de fuente del sistema en lugar de recortar el
 * texto.
 */
object DriverDimens {

    /** Boton de la accion principal (barra inferior). Minimo 64dp. */
    val primaryButtonMinHeight = 64.dp

    /** Boton secundario. Minimo 56dp (por encima de los 48dp de Material). */
    val secondaryButtonMinHeight = 56.dp

    /** Separacion entre botones para evitar toques accidentales. */
    val buttonGap = 12.dp

    /** Padding horizontal/vertical del contenido de la pantalla. */
    val screenPadding = 16.dp

    /** Separacion entre tarjetas. */
    val cardGap = 12.dp

    /** Icono acompañado siempre de texto; tamaño grande para lectura. */
    val icon = 28.dp
}
