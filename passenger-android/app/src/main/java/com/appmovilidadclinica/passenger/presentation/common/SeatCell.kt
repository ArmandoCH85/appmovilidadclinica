package com.appmovilidadclinica.passenger.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.appmovilidadclinica.passenger.domain.model.TripSeat

/**
 * Celda de asiento reutilizable (selección de asiento y extensión de viaje).
 * Los asientos no disponibles no son clickeables.
 */
@Composable
fun SeatCell(seat: TripSeat, selected: Boolean, onClick: () -> Unit) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        seat.isSelectable -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(enabled = seat.isSelectable, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(seat.seatLabel, color = textColor)
    }
}
