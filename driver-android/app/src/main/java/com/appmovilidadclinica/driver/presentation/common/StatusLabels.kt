package com.appmovilidadclinica.driver.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.appmovilidadclinica.driver.domain.model.Direction
import com.appmovilidadclinica.driver.domain.model.IncidentType
import com.appmovilidadclinica.driver.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.domain.model.TripStatus
import com.appmovilidadclinica.driver.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.ui.theme.ReservationBoarded
import com.appmovilidadclinica.driver.ui.theme.ReservationCancelled
import com.appmovilidadclinica.driver.ui.theme.ReservationCompleted
import com.appmovilidadclinica.driver.ui.theme.ReservationConfirmed
import com.appmovilidadclinica.driver.ui.theme.ReservationNoShow
import com.appmovilidadclinica.driver.ui.theme.StatusBoarding
import com.appmovilidadclinica.driver.ui.theme.StatusCancelled
import com.appmovilidadclinica.driver.ui.theme.StatusCompleted
import com.appmovilidadclinica.driver.ui.theme.StatusDraft
import com.appmovilidadclinica.driver.ui.theme.StatusInProgress
import com.appmovilidadclinica.driver.ui.theme.StatusPublished
import com.appmovilidadclinica.driver.ui.theme.StopArrived
import com.appmovilidadclinica.driver.ui.theme.StopDeparted
import com.appmovilidadclinica.driver.ui.theme.StopPending
import com.appmovilidadclinica.driver.ui.theme.StopSkipped

fun TripStatus.label(): String = when (this) {
    TripStatus.DRAFT -> "Borrador"
    TripStatus.PUBLISHED -> "Publicado"
    TripStatus.BOARDING -> "Abordando"
    TripStatus.IN_PROGRESS -> "En curso"
    TripStatus.COMPLETED -> "Completado"
    TripStatus.CANCELLED -> "Cancelado"
}

fun TripStatus.color(): Color = when (this) {
    TripStatus.DRAFT -> StatusDraft
    TripStatus.PUBLISHED -> StatusPublished
    TripStatus.BOARDING -> StatusBoarding
    TripStatus.IN_PROGRESS -> StatusInProgress
    TripStatus.COMPLETED -> StatusCompleted
    TripStatus.CANCELLED -> StatusCancelled
}

fun Direction.label(): String = when (this) {
    Direction.IDA -> "Ida"
    Direction.VUELTA -> "Vuelta"
}

fun ReservationStatus.label(): String = when (this) {
    ReservationStatus.CONFIRMED -> "Confirmado"
    ReservationStatus.BOARDED -> "Abordado"
    ReservationStatus.NO_SHOW -> "No se presentó"
    ReservationStatus.COMPLETED -> "Completado"
    ReservationStatus.CANCELLED -> "Cancelado"
}

fun ReservationStatus.color(): Color = when (this) {
    ReservationStatus.CONFIRMED -> ReservationConfirmed
    ReservationStatus.BOARDED -> ReservationBoarded
    ReservationStatus.NO_SHOW -> ReservationNoShow
    ReservationStatus.COMPLETED -> ReservationCompleted
    ReservationStatus.CANCELLED -> ReservationCancelled
}

fun TripStopStatus.label(): String = when (this) {
    TripStopStatus.PENDING -> "Pendiente"
    TripStopStatus.ARRIVED -> "Llegó"
    TripStopStatus.DEPARTED -> "Partió"
    TripStopStatus.SKIPPED -> "Omitida"
}

fun TripStopStatus.color(): Color = when (this) {
    TripStopStatus.PENDING -> StopPending
    TripStopStatus.ARRIVED -> StopArrived
    TripStopStatus.DEPARTED -> StopDeparted
    TripStopStatus.SKIPPED -> StopSkipped
}

fun IncidentType.label(): String = when (this) {
    IncidentType.BREAKDOWN -> "Avería"
    IncidentType.DELAY -> "Retraso"
    IncidentType.ACCIDENT -> "Accidente"
    IncidentType.OTHER -> "Otro"
}

fun IncidentType.icon(): ImageVector = when (this) {
    IncidentType.BREAKDOWN -> Icons.Default.Build
    IncidentType.DELAY -> Icons.Default.Timer
    IncidentType.ACCIDENT -> Icons.Default.LocalHospital
    IncidentType.OTHER -> Icons.Default.Help
}
