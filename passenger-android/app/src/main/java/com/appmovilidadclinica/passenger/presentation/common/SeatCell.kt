package com.appmovilidadclinica.passenger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.shared.domain.model.TripSeat

/**
 * Celda de asiento reutilizable (selección de asiento y extensión de viaje).
 * `enabled` sale de `seat.isSelectable`: los ocupados/bloqueados no son
 * clickeables.
 */
@Composable
fun SeatCell(
    seat: TripSeat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        seat.isSelectable -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val stateDescription = when {
        selected -> "seleccionado"
        seat.isSelectable -> "disponible"
        else -> "ocupado"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = seat.isSelectable, onClick = onClick)
            .semantics { contentDescription = "Asiento ${seat.seatLabel}, $stateDescription" },
        contentAlignment = Alignment.Center,
    ) {
        Text(seat.seatLabel, color = textColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp),
            )
        } else if (!seat.isSelectable) {
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
    }
}
