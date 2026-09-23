package com.appmovilidadclinica.driver.shared.ui.screens.tripdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.platform.LocationService
import com.appmovilidadclinica.driver.shared.platform.NotificationService
import com.appmovilidadclinica.driver.shared.trip.TripLocationController
import com.appmovilidadclinica.driver.shared.ui.common.DriverDimens
import com.appmovilidadclinica.driver.shared.ui.common.DriverText
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Pantalla "Detalle del viaje" del conductor.
 *
 * Objetivo: que el conductor (60-70 años) entienda qué debe hacer en cada
 * momento, con UNA sola acción principal, botones de 64dp y texto de 18sp o
 * más.
 *
 * La pantalla es "tonta": todo el cálculo operativo vive en
 * [buildTripDetailModel] (puro y testeado) y en [TripDetailViewModel].
 *
 * Decisiones de accesibilidad:
 *  - La acción principal vive siempre en la barra inferior (misma posición,
 *    alcanzable con el pulgar, botón de 64dp que crece con el tamaño de
 *    fuente del sistema).
 *  - Los estados se comunican con texto + icono, nunca solo con color.
 *  - No hay gestos ni acciones ocultas: todo es un botón rotulado.
 *  - La barra superior es propia (no `TopAppBar`) porque el componente de
 *    Material fija 64dp de alto y recorta el título cuando el conductor
 *    aumenta el tamaño de fuente del sistema; esta versión crece con el
 *    contenido manteniendo el mismo estilo visual del resto de la app.
 *  - El aviso de la última acción no se auto-oculta (no hay toast de 2
 *    segundos): el conductor lo cierra cuando termina de leerlo.
 */
@Composable
fun TripDetailScreen(
    tripId: Long,
    initialTrip: DriverTrip? = null,
    driverRepository: DriverRepository = koinInject(),
    notificationService: NotificationService = koinInject(),
    locationService: LocationService = koinInject(),
    tripLocationController: TripLocationController = koinInject(),
    onBack: () -> Unit = {},
    onScanQr: (Long) -> Unit = {},
    onReportIncident: (Long) -> Unit = {},
    onOccupySeat: (Long) -> Unit = {},
) {
    val viewModel = remember(tripId, initialTrip, driverRepository) {
        TripDetailViewModel(tripId, initialTrip, driverRepository)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    val state by viewModel.uiState.collectAsState()
    val model = state.model
    val pending = state.pending

    var noShowTarget by remember { mutableStateOf<BoarderRowModel?>(null) }
    var overrideTarget by remember { mutableStateOf<PrimaryAction?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    // GPS tracking en primer plano mientras el viaje está en curso.
    LaunchedEffect(state.trip?.status, locationService.isAvailable()) {
        val shouldTrack = state.trip?.status == TripStatus.IN_PROGRESS &&
            locationService.isAvailable()
        if (shouldTrack) {
            Logger.withTag("GpsTracking").i { "start observing location" }
            locationService.observeLocation().collect { coords ->
                Logger.withTag("GpsTracking").i {
                    "(${coords.latitude}, ${coords.longitude}) ±${coords.accuracyMeters}m"
                }
            }
        }
    }

    // GPS en background via Foreground Service (Android). iOS no-op.
    LaunchedEffect(state.trip?.status) {
        when (state.trip?.status) {
            TripStatus.IN_PROGRESS -> tripLocationController.startTracking()
            TripStatus.COMPLETED, TripStatus.CANCELLED -> tripLocationController.stopTracking()
            else -> Unit
        }
    }

    // Notificación local al iniciar / finalizar (Android NotificationCompat).
    LaunchedEffect(state.trip?.status) {
        when (state.trip?.status) {
            TripStatus.IN_PROGRESS -> notificationService.notifyTripStarted(tripId.toString())
            TripStatus.COMPLETED -> notificationService.notifyTripStarted(tripId.toString())
            else -> Unit
        }
    }

    // Refresco periódico mientras el viaje está en curso y no hay una acción
    // en vuelo: el backend puede cambiar el manifiesto por su cuenta (por
    // ejemplo el marcador automático de NO_SHOW al vencer la tolerancia) y el
    // conductor necesita ver el estado real sin adivinar.
    LaunchedEffect(state.trip?.status, pending) {
        if (state.trip?.status != TripStatus.IN_PROGRESS) return@LaunchedEffect
        while (state.pending == null) {
            delay(PERIODIC_REFRESH_MILLIS)
            if (state.pending == null) viewModel.refreshNow()
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "Detalle del viaje",
                onBack = onBack,
                onRefresh = viewModel::refreshNow,
                refreshing = state.refreshing,
            )
        },
        bottomBar = {
            val primary = model.primary
            if (primary != null) {
                PrimaryActionBar(
                    primary = primary,
                    blockedReason = model.primaryBlockedReason,
                    overrideLabel = if (primary is PrimaryAction.FinishTrip) {
                        overrideLabelForFinish(model)
                    } else {
                        overrideLabelForDeparture(model)
                    },
                    pending = pending,
                    onPrimary = {
                        when (primary) {
                            is PrimaryAction.StartTrip -> viewModel.startTrip()
                            is PrimaryAction.ArriveAtStop -> viewModel.markArrival(primary.stopId)
                            is PrimaryAction.DepartFromStop -> viewModel.markDeparture(primary.stopId)
                            is PrimaryAction.FinishTrip -> viewModel.finishTrip()
                        }
                    },
                    onOverride = {
                        if (model.canOverrideDeparture) overrideTarget = primary
                    },
                    overrideVisible = model.canOverrideDeparture,
                )
            }
        },
    ) { padding ->
        when {
            state.loadErrorMessage != null -> {
                LoadErrorContent(
                    message = state.loadErrorMessage.orEmpty(),
                    modifier = Modifier.padding(padding),
                    onRetry = viewModel::load,
                )
            }

            state.loading && model.header == null -> {
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Cargando el viaje…", style = DriverText.body)
                }
            }

            else -> {
                // El aviso de la última acción queda FIJO arriba del listado:
                // es la confirmación de que la marca se registró (o el motivo
                // del error) y no debe quedar fuera de la vista al desplazar
                // el itinerario.
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    state.notice?.let { notice ->
                        NoticeCard(
                            notice = notice,
                            onDismiss = viewModel::dismissNotice,
                            modifier = Modifier.padding(
                                start = DriverDimens.screenPadding,
                                end = DriverDimens.screenPadding,
                                top = DriverDimens.screenPadding,
                            ),
                        )
                    }

                    LazyColumn(
                        // weight(1f) en vez de fillMaxSize(): el listado ocupa
                        // exactamente el espacio que deja el aviso fijo, sin
                        // desbordar la pantalla cuando la fuente del sistema
                        // está aumentada.
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = DriverDimens.screenPadding,
                            end = DriverDimens.screenPadding,
                            top = DriverDimens.screenPadding,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(DriverDimens.cardGap),
                    ) {
                        model.header?.let { header ->
                            item(key = "header") { TripHeaderCard(header = header) }
                        }

                        item(key = "now") { NowCard(model = model) }

                        if (model.phase == TripPhase.AT_STOP) {
                            val boarders = model.boarders
                            val alighters = model.alighters

                            if (boarders.isEmpty() && alighters.isEmpty()) {
                                item(key = "manifest_empty") {
                                    InfoCard(
                                        icon = Icons.Default.Info,
                                        text = "No hay pasajeros que suban ni bajen en esta parada.",
                                    )
                                }
                            }

                            if (boarders.isNotEmpty()) {
                                item(key = "boarders_title") {
                                    SectionTitle(
                                        text = "Pasajeros en esta parada (${boarders.size})",
                                        supporting = "Marque «Subió» o «No se presentó» en cada uno.",
                                    )
                                }
                                items(boarders, key = { "b_" + it.key }) { row ->
                                    BoarderCard(
                                        row = row,
                                        busy = pending != null,
                                        pendingKey = pending,
                                        onBoard = { viewModel.board(row.reservationId) },
                                        onNoShow = { noShowTarget = row },
                                    )
                                }
                            }

                            if (alighters.isNotEmpty()) {
                                item(key = "alighters_title") {
                                    SectionTitle(
                                        text = "Bajan en esta parada (${alighters.size})",
                                        supporting = "La bajada se registra con la hora de llegada a la parada.",
                                    )
                                }
                                items(alighters, key = { "a_" + it.key }) { row ->
                                    AlighterCard(
                                        row = row,
                                        busy = pending != null,
                                        onAlight = { viewModel.alight(row.reservationId) },
                                    )
                                }
                            }
                        }

                        item(key = "other_actions") {
                            OtherActionsCard(
                                busy = pending != null,
                                onScanQr = { onScanQr(tripId) },
                                onOccupySeat = { onOccupySeat(tripId) },
                                onReportIncident = { onReportIncident(tripId) },
                            )
                        }

                        if (state.stopsErrorMessage != null) {
                            item(key = "stops_error") {
                                InfoCard(
                                    icon = Icons.Default.ErrorOutline,
                                    text = "No se pudo cargar el cronograma de paradas: " +
                                        state.stopsErrorMessage.orEmpty(),
                                    tone = InfoTone.ERROR,
                                )
                            }
                        } else if (model.stops.isNotEmpty()) {
                            item(key = "stops_title") {
                                SectionTitle(text = "Paradas del viaje (${model.stops.size})")
                            }
                            items(model.stops, key = { "s_" + it.id }) { stop ->
                                StopCard(stop = stop)
                            }
                        }
                    }
                }
            }
        }
    }

    // Única confirmación previa: "No se presentó" libera el asiento y el
    // backend no permite revertirla (no existe endpoint de corrección).
    noShowTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { noShowTarget = null },
            title = { Text("No se presentó", style = DriverText.title) },
            text = {
                Text(
                    "${target.displayName} (asiento ${target.seatLabel}) quedará como " +
                        "«No se presentó» y su asiento se liberará. Esta marca no se puede deshacer.",
                    style = DriverText.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.noShow(target.reservationId)
                    noShowTarget = null
                }) {
                    Text("Sí, no se presentó", style = DriverText.button)
                }
            },
            dismissButton = {
                TextButton(onClick = { noShowTarget = null }) {
                    Text("Cancelar", style = DriverText.button)
                }
            },
        )
    }

    // Salida anticipada: solo aparece cuando quedan pasajeros sin registrar y
    // el conductor decide continuar el recorrido igual.
    overrideTarget?.let { action ->
        val pendingCount = model.pendingBoarders
        AlertDialog(
            onDismissRequest = { overrideTarget = null },
            title = { Text(action.label, style = DriverText.title) },
            text = {
                Text(
                    if (pendingCount == 1) {
                        "Queda 1 pasajero sin registrar en esta parada. Si continúa, se guardará la hora real de salida y ese pasajero quedará pendiente para la siguiente parada."
                    } else {
                        "Quedan $pendingCount pasajeros sin registrar en esta parada. Si continúa, se guardará la hora real de salida y esos pasajeros quedarán pendientes para la siguiente parada."
                    },
                    style = DriverText.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        is PrimaryAction.DepartFromStop -> viewModel.markDeparture(action.stopId)
                        is PrimaryAction.FinishTrip -> viewModel.finishTrip()
                        else -> Unit
                    }
                    overrideTarget = null
                }) {
                    Text("Sí, continuar", style = DriverText.button)
                }
            },
            dismissButton = {
                TextButton(onClick = { overrideTarget = null }) {
                    Text("Cancelar", style = DriverText.button)
                }
            },
        )
    }
}

private const val PERIODIC_REFRESH_MILLIS = 45_000L

private fun overrideLabelForDeparture(model: TripDetailModel): String =
    "Salir sin registrar ${pendingPassengersText(model.pendingBoarders)}"

private fun overrideLabelForFinish(model: TripDetailModel): String =
    "Finalizar viaje sin registrar ${pendingPassengersText(model.pendingBoarders)}"

private fun pendingPassengersText(count: Int): String =
    if (count == 1) "1 pasajero" else "$count pasajeros"

// ------------------------------------------------------------------ estructura

/**
 * Barra superior: título del viaje + botón "Volver".
 *
 * El botón de volver se rotula (icono + palabra "Volver") para que el
 * conductor no tenga que interpretar una flecha, pero se mantiene compacto
 * (52dp, texto de 18sp) para no quitarle espacio al título ni competir con la
 * acción principal de abajo.
 *
 * Se mantiene el inset de la barra de estado: la app corre edge-to-edge y sin
 * él el botón queda debajo de la barra del sistema, que se come el toque.
 */
@Composable
private fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                // OJO: nada de fillMaxHeight() aquí. Dentro de la barra, el
                // botón recibiría la altura máxima disponible y se estiraría
                // hasta el fondo de la pantalla (el texto quedaría a media
                // pantalla y el toque no llegaría donde el conductor lo busca).
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        // Decorativo: la etiqueta visible es "Volver".
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Volver", style = DriverText.body)
                }
            }

            Spacer(Modifier.width(10.dp))

            Text(
                title,
                style = DriverText.title,
                modifier = Modifier.weight(1f),
            )

            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).padding(end = 4.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Actualizar estado del viaje",
                    )
                }
            }
        }
    }
}

/**
 * Barra inferior con la ÚNICA acción principal del momento. Siempre en la
 * misma posición, botón de 64dp mínimo que crece con la fuente del sistema.
 */
@Composable
private fun PrimaryActionBar(
    primary: PrimaryAction,
    blockedReason: String?,
    overrideLabel: String,
    overrideVisible: Boolean,
    pending: TripActionKey?,
    onPrimary: () -> Unit,
    onOverride: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Igual que la barra superior: sin este inset la acción
                // principal queda debajo de la barra de navegación/gestos.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = DriverDimens.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(DriverDimens.buttonGap),
        ) {
            if (blockedReason != null) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(DriverDimens.icon),
                    )
                    Text(
                        blockedReason,
                        style = DriverText.bodyStrong,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val busy = pending != null
            Button(
                onClick = onPrimary,
                enabled = !busy && blockedReason == null,
                colors = ButtonDefaults.buttonColors(
                    // Se conserva el contraste cuando está deshabilitado: el
                    // motivo real está en el texto de arriba.
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DriverDimens.primaryButtonMinHeight),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(loadingLabel(pending), style = DriverText.button)
                } else {
                    Icon(
                        primary.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(DriverDimens.icon),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(primary.label, style = DriverText.button)
                }
            }

            if (overrideVisible && !busy) {
                OutlinedButton(
                    onClick = onOverride,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DriverDimens.secondaryButtonMinHeight),
                ) {
                    Text(overrideLabel, style = DriverText.body)
                }
            }
        }
    }
}

private fun PrimaryAction.icon(): ImageVector = when (this) {
    is PrimaryAction.StartTrip -> Icons.Default.PlayArrow
    is PrimaryAction.ArriveAtStop -> Icons.Default.Place
    is PrimaryAction.DepartFromStop -> Icons.AutoMirrored.Filled.ArrowForward
    is PrimaryAction.FinishTrip -> Icons.Default.Flag
}

private fun loadingLabel(key: TripActionKey?): String = when (key) {
    TripActionKey.StartTrip -> "Iniciando…"
    TripActionKey.FinishTrip -> "Finalizando…"
    is TripActionKey.Arrival -> "Registrando llegada…"
    is TripActionKey.Departure -> "Registrando salida…"
    is TripActionKey.Board -> "Registrando…"
    is TripActionKey.NoShow -> "Registrando…"
    is TripActionKey.Alight -> "Registrando…"
    null -> ""
}

// -------------------------------------------------------------------- tarjetas

@Composable
private fun TripHeaderCard(header: TripHeaderModel) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    header.routeName,
                    style = DriverText.title,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    text = header.directionLabel,
                    icon = Icons.Default.DirectionsBus,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(10.dp))
            IconLine(
                icon = Icons.Default.Schedule,
                text = "Horario: ${header.scheduleLabel}",
            )
            Spacer(Modifier.height(6.dp))
            IconLine(
                icon = Icons.Default.DirectionsBus,
                text = "Placa: ${header.plateLabel}",
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${header.statusLabel} · ${header.tripCodeLabel}",
                style = DriverText.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Tarjeta "qué hacer ahora": resumen operativo del momento. Explica por qué
 * la acción principal es la que es.
 */
@Composable
private fun NowCard(model: TripDetailModel) {
    val container = when (model.phase) {
        TripPhase.AT_STOP -> MaterialTheme.colorScheme.primaryContainer
        TripPhase.READY_TO_FINISH -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (model.phase) {
                TripPhase.SCHEDULED -> {
                    Text("VIAJE PROGRAMADO", style = DriverText.chip)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        model.firstStopLabel?.let { "Primera parada: $it" }
                            ?: "Este viaje no tiene paradas configuradas.",
                        style = DriverText.actionTitle,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (model.expectedBoarders > 0) {
                        IconLine(
                            icon = Icons.Default.People,
                            text = passengersText(model.expectedBoarders, "esperan abordar en la primera parada"),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "Al iniciar se registra el comienzo del recorrido. La llegada a la primera " +
                            "parada se marca después, con el botón «Llegué a …».",
                        style = DriverText.supporting,
                    )
                }

                TripPhase.EN_ROUTE -> {
                    Text("SIGUIENTE PARADA", style = DriverText.chip)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        model.focusStop?.name.orEmpty(),
                        style = DriverText.actionTitle,
                    )
                    Spacer(Modifier.height(10.dp))
                    model.focusStop?.scheduledLabel?.let { time ->
                        IconLine(icon = Icons.Default.Schedule, text = "Hora programada: $time")
                        Spacer(Modifier.height(6.dp))
                    }
                    IconLine(
                        icon = Icons.Default.People,
                        text = boardersText(model.expectedBoarders),
                    )
                    if (model.expectedAlighters > 0) {
                        Spacer(Modifier.height(6.dp))
                        IconLine(
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            text = alightersText(model.expectedAlighters),
                        )
                    }
                    if (model.onboardCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        IconLine(
                            icon = Icons.Default.DirectionsBus,
                            text = "A bordo: ${model.onboardCount}",
                        )
                    }
                }

                TripPhase.AT_STOP -> {
                    Text("PARADA ACTUAL", style = DriverText.chip)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        model.focusStop?.name.orEmpty(),
                        style = DriverText.actionTitle,
                    )
                    Spacer(Modifier.height(10.dp))
                    model.focusStop?.scheduledLabel?.let { time ->
                        IconLine(icon = Icons.Default.Schedule, text = "Hora programada: $time")
                        Spacer(Modifier.height(6.dp))
                    }
                    model.focusStop?.arrivalLabel?.let { time ->
                        IconLine(
                            icon = Icons.Default.CheckCircle,
                            text = "Llegada registrada: $time",
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (model.pendingBoarders > 0) {
                        IconLine(
                            icon = Icons.Default.Warning,
                            text = "Pendientes de registrar: ${model.pendingBoarders}",
                        )
                    } else {
                        IconLine(
                            icon = Icons.Default.CheckCircle,
                            text = "Todos los pasajeros de esta parada están registrados.",
                        )
                    }
                }

                TripPhase.READY_TO_FINISH -> {
                    Text("RECORRIDO TERMINADO", style = DriverText.chip)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        model.message ?: "Finalice el viaje para cerrarlo.",
                        style = DriverText.body,
                    )
                    if (model.onboardCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        IconLine(
                            icon = Icons.Default.DirectionsBus,
                            text = "A bordo: ${model.onboardCount}",
                        )
                    }
                }

                TripPhase.FINISHED, TripPhase.UNAVAILABLE -> {
                    Text(
                        if (model.phase == TripPhase.FINISHED) "VIAJE FINALIZADO" else "SIN ACCIONES",
                        style = DriverText.chip,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        model.message ?: "Este viaje no requiere ninguna acción.",
                        style = DriverText.body,
                    )
                }

                TripPhase.LOADING -> {
                    Text("Cargando el viaje…", style = DriverText.body)
                }
            }
        }
    }
}

@Composable
private fun BoarderCard(
    row: BoarderRowModel,
    busy: Boolean,
    pendingKey: TripActionKey?,
    onBoard: () -> Unit,
    onNoShow: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    row.displayName,
                    style = DriverText.subtitle,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                MarkStateChip(state = row.state)
            }
            Spacer(Modifier.height(6.dp))
            Text("Asiento ${row.seatLabel}", style = DriverText.body)
            if (row.isGuest) {
                Text(
                    "Invitado registrado por el conductor",
                    style = DriverText.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.boardingStopAlreadyPassed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Su parada de subida fue ${row.boardingStopName}. Resuélvalo aquí para cerrar el manifiesto.",
                    style = DriverText.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (row.state) {
                PassengerMarkState.PENDING -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onBoard,
                        enabled = !busy,
                        // El lector de pantalla necesita saber a quién
                        // corresponde el botón: hay uno igual por pasajero.
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = DriverDimens.primaryButtonMinHeight)
                            .semantics { contentDescription = "Subió: ${row.displayName}" },
                    ) {
                        if (pendingKey == TripActionKey.Board(row.reservationId)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(DriverDimens.icon),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Subió", style = DriverText.button)
                    }
                    Spacer(Modifier.height(DriverDimens.buttonGap))
                    OutlinedButton(
                        onClick = onNoShow,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = DriverDimens.primaryButtonMinHeight)
                            .semantics {
                                contentDescription = "No se presentó: ${row.displayName}"
                            },
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(DriverDimens.icon),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "No se presentó",
                            style = DriverText.button,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                PassengerMarkState.BOARDED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        row.timeLabel?.let { "Subió a las $it" } ?: "Subió",
                        style = DriverText.bodyStrong,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                PassengerMarkState.NO_SHOW -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        row.timeLabel?.let { "No se presentó · registrado a las $it" }
                            ?: "No se presentó",
                        style = DriverText.bodyStrong,
                    )
                }

                PassengerMarkState.ALIGHTED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        row.timeLabel?.let { "Bajó a las $it" } ?: "Bajó",
                        style = DriverText.bodyStrong,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlighterCard(
    row: AlighterRowModel,
    busy: Boolean,
    onAlight: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    row.displayName,
                    style = DriverText.subtitle,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                MarkStateChip(state = row.state)
            }
            Spacer(Modifier.height(6.dp))
            Text("Asiento ${row.seatLabel}", style = DriverText.body)

            if (row.automatic) {
                Spacer(Modifier.height(8.dp))
                Text(
                    row.timeLabel?.let { "Bajó a las $it (registrado con la llegada a la parada)" }
                        ?: "Bajó (registrado con la llegada a la parada)",
                    style = DriverText.bodyStrong,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAlight,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DriverDimens.primaryButtonMinHeight)
                        .semantics { contentDescription = "Bajó: ${row.displayName}" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        modifier = Modifier.size(DriverDimens.icon),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Bajó", style = DriverText.button)
                }
            }
        }
    }
}

@Composable
private fun StopCard(stop: StopRowModel) {
    val container = if (stop.isFocus) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (stop.isFocus) {
                Text(
                    if (stop.isCurrent) "PARADA ACTUAL" else "SIGUIENTE PARADA",
                    style = DriverText.chip,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${stop.order}",
                            style = DriverText.bodyStrong,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stop.name, style = DriverText.subtitle)
                    Spacer(Modifier.height(4.dp))
                    stop.scheduledLabel?.let {
                        Text("Hora programada: $it", style = DriverText.body)
                    }
                    stop.arrivalLabel?.let {
                        Text("Llegada registrada: $it", style = DriverText.body)
                    }
                    stop.departureLabel?.let {
                        Text("Salida registrada: $it", style = DriverText.body)
                    }
                    if (stop.pendingPassengers > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            passengersText(stop.pendingPassengers, "por registrar"),
                            style = DriverText.supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (stop.alightingPassengers > 0) {
                        Text(
                            alightersText(stop.alightingPassengers),
                            style = DriverText.supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StopStatusChip(status = stop.status, label = stop.statusLabel)
            }
        }
    }
}

@Composable
private fun OtherActionsCard(
    busy: Boolean,
    onScanQr: () -> Unit,
    onOccupySeat: () -> Unit,
    onReportIncident: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            text = "Otras acciones",
            supporting = "Acciones de apoyo. La acción principal está siempre abajo.",
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onScanQr,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DriverDimens.secondaryButtonMinHeight),
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(DriverDimens.icon),
            )
            Spacer(Modifier.width(12.dp))
            Text("Escanear QR del pasajero", style = DriverText.body)
        }
        Spacer(Modifier.height(DriverDimens.buttonGap))
        OutlinedButton(
            onClick = onOccupySeat,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DriverDimens.secondaryButtonMinHeight),
        ) {
            Icon(
                Icons.Default.EventSeat,
                contentDescription = null,
                modifier = Modifier.size(DriverDimens.icon),
            )
            Spacer(Modifier.width(12.dp))
            Text("Ocupar asiento de invitado", style = DriverText.body)
        }
        Spacer(Modifier.height(DriverDimens.buttonGap))
        OutlinedButton(
            onClick = onReportIncident,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DriverDimens.secondaryButtonMinHeight),
        ) {
            Icon(
                Icons.Default.Report,
                contentDescription = null,
                modifier = Modifier.size(DriverDimens.icon),
            )
            Spacer(Modifier.width(12.dp))
            Text("Reportar incidencia", style = DriverText.body)
        }
    }
}

@Composable
private fun NoticeCard(
    notice: ActionNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when (notice.kind) {
        NoticeKind.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        NoticeKind.ERROR -> MaterialTheme.colorScheme.errorContainer
        NoticeKind.INFO -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (notice.kind) {
        NoticeKind.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        NoticeKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        NoticeKind.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = when (notice.kind) {
        NoticeKind.SUCCESS -> Icons.Default.CheckCircle
        NoticeKind.ERROR -> Icons.Default.ErrorOutline
        NoticeKind.INFO -> Icons.Default.Info
    }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(DriverDimens.icon),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        notice.title,
                        style = DriverText.bodyStrong,
                        color = content,
                    )
                    notice.detail?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = DriverText.body, color = content)
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cerrar aviso", style = DriverText.body)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, supporting: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text, style = DriverText.title)
        supporting?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = DriverText.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(DriverDimens.icon),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = DriverText.body, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = DriverText.chip, color = content)
        }
    }
}

/** Estado de parada con texto + icono (nunca solo color). */
@Composable
private fun StopStatusChip(status: TripStopStatus, label: String) {
    val icon = when (status) {
        TripStopStatus.PENDING -> Icons.Default.RadioButtonUnchecked
        TripStopStatus.ARRIVED -> Icons.Default.Place
        TripStopStatus.DEPARTED -> Icons.AutoMirrored.Filled.ArrowForward
        TripStopStatus.SKIPPED -> Icons.Default.RemoveCircleOutline
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = DriverText.chip)
        }
    }
}

@Composable
private fun MarkStateChip(state: PassengerMarkState) {
    val spec = when (state) {
        PassengerMarkState.PENDING -> MarkChipSpec(
            label = "Por registrar",
            icon = Icons.Default.RadioButtonUnchecked,
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PassengerMarkState.BOARDED -> MarkChipSpec(
            label = "Subió",
            icon = Icons.Default.CheckCircle,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PassengerMarkState.NO_SHOW -> MarkChipSpec(
            label = "No se presentó",
            icon = Icons.Default.PersonOff,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        PassengerMarkState.ALIGHTED -> MarkChipSpec(
            label = "Bajó",
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    StatusChip(
        text = spec.label,
        icon = spec.icon,
        container = spec.container,
        content = spec.content,
    )
}

private data class MarkChipSpec(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

private enum class InfoTone { INFO, ERROR }

@Composable
private fun InfoCard(icon: ImageVector, text: String, tone: InfoTone = InfoTone.INFO) {
    val container = when (tone) {
        InfoTone.INFO -> MaterialTheme.colorScheme.secondaryContainer
        InfoTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        InfoTone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        InfoTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(DriverDimens.icon),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = DriverText.body, color = content)
        }
    }
}

@Composable
private fun LoadErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(DriverDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DriverDimens.cardGap),
    ) {
        InfoCard(
            icon = Icons.Default.ErrorOutline,
            text = "No se pudo cargar el viaje: $message",
            tone = InfoTone.ERROR,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DriverDimens.primaryButtonMinHeight),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(DriverDimens.icon),
            )
            Spacer(Modifier.width(12.dp))
            Text("Reintentar", style = DriverText.button)
        }
    }
}

private fun passengersText(count: Int, suffix: String): String =
    if (count == 1) "1 pasajero $suffix" else "$count pasajeros $suffix"

private fun boardersText(count: Int): String = when (count) {
    0 -> "Ningún pasajero sube en esta parada"
    1 -> "1 pasajero sube en esta parada"
    else -> "$count pasajeros suben en esta parada"
}

private fun alightersText(count: Int): String = when (count) {
    1 -> "1 pasajero baja en esta parada"
    else -> "$count pasajeros bajan en esta parada"
}
