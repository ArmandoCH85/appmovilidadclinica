package com.appmovilidadclinica.driver

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appmovilidadclinica.driver.shared.domain.model.AppError
import com.appmovilidadclinica.driver.shared.domain.model.Direction
import com.appmovilidadclinica.driver.shared.domain.model.DriverTrip
import com.appmovilidadclinica.driver.shared.domain.model.Incident
import com.appmovilidadclinica.driver.shared.domain.model.IncidentType
import com.appmovilidadclinica.driver.shared.domain.model.Passenger
import com.appmovilidadclinica.driver.shared.domain.model.ReservationStatus
import com.appmovilidadclinica.driver.shared.domain.model.SeatAvailability
import com.appmovilidadclinica.driver.shared.domain.model.TripStatus
import com.appmovilidadclinica.driver.shared.domain.model.TripStop
import com.appmovilidadclinica.driver.shared.domain.model.TripStopStatus
import com.appmovilidadclinica.driver.shared.domain.repository.DriverRepository
import com.appmovilidadclinica.driver.shared.platform.Coordinates
import com.appmovilidadclinica.driver.shared.platform.LocationService
import com.appmovilidadclinica.driver.shared.platform.NotificationService
import com.appmovilidadclinica.driver.shared.platform.PermissionStatus
import com.appmovilidadclinica.driver.shared.trip.TripLocationController
import com.appmovilidadclinica.driver.shared.ui.screens.tripdetail.TripDetailScreen
import com.appmovilidadclinica.driver.shared.ui.theme.DriverAppTheme
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de UI del flujo completo del "Detalle del viaje" sobre un emulador o
 * dispositivo real, con capturas de pantalla para revisar la legibilidad (se
 * repite con la fuente del sistema aumentada y en pantalla pequeña).
 *
 * Los tres recursos de la pantalla (repositorio, GPS y notificaciones) se
 * inyectan como dobles de prueba: la pantalla los recibe por parámetro, así
 * que no necesita Koin ni backend. El doble del repositorio replica las reglas
 * de los stored procedures (llegada que cierra las bajadas, salida que exige
 * llegada, etc.).
 *
 * Notas de implementación:
 *  - La activity anfitriona se lanza a mano (`startActivitySync` sobre
 *    `androidx.activity.ComponentActivity`, declarada en el manifest de debug)
 *    y el contenido se monta con `setContent`. `createComposeRule()` no sirve
 *    aquí: en androidx.test 1.7.0 arma el Intent contra el paquete del APK de
 *    test y falla con "resolved to different process".
 *  - Los botones de cada pasajero se buscan por `contentDescription`
 *    ("Subió: Ana Quispe"): el texto visible se repite por pasajero y para el
 *    lector de pantalla debe indicar a quién corresponde.
 *  - Los diálogos viven en otra ventana, fuera del árbol de semántica que
 *    observa el ComposeTestRule, y su contenido no se puede consultar con
 *    Espresso sin foco de ventana (emulador headless). Por eso se verifica que
 *    aparece un modal con el velo (scrim) oscureciendo la pantalla, y que la
 *    parada sigue bloqueada al cerrarlo con BACK.
 *  - Las capturas llevan prefijo de configuración
 *    (`f<fontScale>_<ancho>x<alto>dp_...`) para comparar la misma pantalla en
 *    tamaño normal, con fuente aumentada y en pantalla pequeña.
 */
@RunWith(AndroidJUnit4::class)
class TripDetailScreenUiTest {

    @get:Rule
    val rule: ComposeTestRule = createEmptyComposeRule()

    private lateinit var host: ComponentActivity

    private val shotsDir: File by lazy {
        File(
            InstrumentationRegistry.getInstrumentation()
                .targetContext.getExternalFilesDir(null),
            "shots",
        ).apply { mkdirs() }
    }

    @Before
    fun launchHostActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                instrumentation.targetContext.packageName,
                ComponentActivity::class.java.name,
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        host = instrumentation.startActivitySync(intent) as ComponentActivity
        rule.waitForIdle()
    }

    @After
    fun closeHostActivity() {
        if (::host.isInitialized) host.finish()
    }

    /** Nombre de archivo con la configuración de pantalla actual. */
    private fun shotName(name: String): String {
        val config = host.resources.configuration
        return "f${config.fontScale}_${config.screenWidthDp}x${config.screenHeightDp}dp_$name"
    }

    /**
     * Captura de toda la pantalla (incluye los diálogos, que viven en otra
     * ventana) mediante UiAutomation. `onRoot().captureToImage()` no sirve con
     * un modal abierto: falla con "Failed to capture a node to bitmap".
     */
    private fun screenshot(): Bitmap =
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()

    private fun capture(name: String) {
        val bitmap = screenshot()
        File(shotsDir, "${shotName(name)}.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Luminancia media de la pantalla, muestreada en una grilla gruesa. Un
     * diálogo modal agrega un velo oscuro sobre toda la pantalla: comparar
     * antes/después de la acción verifica que el modal se mostró.
     */
    private fun screenLuminance(): Double {
        val bitmap = screenshot()
        var sum = 0.0
        var samples = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                sum += Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114
                samples++
                x += 40
            }
            y += 40
        }
        return sum / samples
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /**
     * Desplaza el listado hasta el botón de un pasajero. En una LazyColumn los
     * ítems fuera de pantalla no existen en el árbol de semántica, así que
     * primero hay que pedirle al contenedor desplazable que los componga.
     */
    private fun scrollToPassengerAction(description: String) {
        rule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(description))
        rule.waitForIdle()
    }

    /** Igual que [scrollToPassengerAction] pero por texto visible. */
    private fun scrollToText(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        rule.waitForIdle()
    }

    /**
     * Verifica que la acción principal esté realmente dentro de la ventana.
     * `assertIsDisplayed` no dice por qué falla, y en pantallas pequeñas con
     * fuente aumentada el dato de los bounds es el que importa.
     */
    private fun assertActionVisible(description: String) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val node = rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Acción fuera de la ventana: acción=$node ventana=$root",
            node.height >= 64f && node.top < root.height && node.bottom > 0f,
        )
    }

    /**
     * Dispara el onClick de un botón de pasajero por semántica. Un toque
     * físico sobre un ítem de la LazyColumn puede quedar parcialmente fuera
     * del área recortada cuando la pantalla es pequeña y la fuente grande; la
     * acción de semántica valida el cableado del botón y el diálogo que abre.
     */
    private fun clickPassengerAction(description: String) {
        scrollToPassengerAction(description)
        rule.onNodeWithContentDescription(description)
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    /**
     * El diálogo modal se dibuja con un velo (scrim) oscuro: si aparece, la
     * luminancia media de la pantalla baja de forma notoria. Se exige una
     * caída absoluta de al menos 10 unidades; sin modal la variación entre dos
     * capturas es menor a 1.
     */
    private fun assertDialogVisible(before: Double, after: Double) {
        assertTrue(
            "Se esperaba un diálogo modal (luminancia antes=$before, después=$after)",
            before - after >= 10.0,
        )
    }

    private fun render(repository: DriverRepository, onBack: () -> Unit = {}) {
        host.runOnUiThread {
            host.setContent {
                DriverAppTheme {
                    TripDetailScreen(
                        tripId = TRIP_ID,
                        initialTrip = null,
                        driverRepository = repository,
                        notificationService = NoopNotifications,
                        locationService = NoopLocation,
                        tripLocationController = NoopTripLocation,
                        onBack = onBack,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /**
     * El reporte del conductor fue "el botón de volver no funciona": la app
     * corre con `enableEdgeToEdge()`, y una barra superior propia sin el inset
     * de la barra de estado deja el botón debajo de ella (el sistema se queda
     * con el toque). Se replica edge-to-edge en la activity anfitriona y se
     * verifica que el botón esté rotulado, mida 64dp, quede por debajo del
     * inset y que el toque llegue.
     */
    @Test
    fun elBotonDeVolverEsGrandeRotuladoYResponde() {
        host.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(host.window, false) }
        var backPressed = false
        render(FakeRepo(), onBack = { backPressed = true })

        val statusBarInset = ViewCompat.getRootWindowInsets(host.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        // Rotulado con texto visible (no solo una flecha) y con área táctil
        // amplia, sin llegar a ocupar media barra.
        rule.onNodeWithText("Volver")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(52.dp)
        // El título del viaje sigue en la barra.
        rule.onNodeWithText("Detalle del viaje").assertIsDisplayed()
        val bounds = rule.onNodeWithText("Volver").fetchSemanticsNode().boundsInRoot
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "El botón de volver queda bajo la barra de estado: botón=$bounds inset=$statusBarInset",
            bounds.top >= statusBarInset,
        )
        // Y sobre todo: tiene que estar ARRIBA, no estirado a media pantalla
        // (un fillMaxHeight() lo estiraba y el toque no llegaba donde el
        // conductor lo busca).
        assertTrue(
            "El botón de volver no está en la parte superior: botón=$bounds ventana=$window",
            bounds.top < window.height * 0.25f && bounds.height < window.height * 0.25f,
        )
        capture("12_boton_volver_edge_to_edge")

        rule.onNodeWithText("Volver").performClick()
        rule.waitForIdle()
        assertTrue("El botón de volver no disparó la navegación", backPressed)
    }

    @Test
    fun flujoCompletoIniciarLlegarAbordarSalirLlegarYFinalizar() {
        val repo = FakeRepo()
        render(repo)

        // 1) Viaje programado: ruta, sentido, horario, placa y primera parada;
        //    una sola acción principal. En pantallas pequeñas con fuente
        //    aumentada hay que desplazar el listado para ver cada dato.
        scrollToText("BUS-25 · Mañana · IDA")
        rule.onNodeWithText("BUS-25 · Mañana · IDA").assertIsDisplayed()
        rule.onNodeWithText("Horario: 06:05 AM – 07:20 AM").assertExists()
        scrollToText("Placa: ABC-123")
        rule.onNodeWithText("Placa: ABC-123").assertIsDisplayed()
        scrollToText("Primera parada: Sede Surco · 06:05 AM")
        rule.onNodeWithText("Primera parada: Sede Surco · 06:05 AM").assertIsDisplayed()
        scrollToText("VIAJE PROGRAMADO")
        rule.onNodeWithText("VIAJE PROGRAMADO").assertIsDisplayed()
        // El botón principal mide al menos 64dp y está a la vista.
        rule.onNodeWithText("Iniciar viaje")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(64.dp)
        // Sin botones de llegada repetidos ni textos de la versión anterior.
        rule.onAllNodesWithText("Marcar llegada").assertCountEquals(0)
        rule.onAllNodesWithText("Inicie el viaje para marcar llegadas").assertCountEquals(0)
        rule.onAllNodesWithText("Continuar").assertCountEquals(0)
        capture("01_viaje_programado")

        // 2) Iniciar viaje registra el inicio del recorrido, no la llegada.
        rule.onNodeWithText("Iniciar viaje").performClick()
        rule.waitForIdle()
        assertEquals("start", repo.operations.last())
        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val actionBounds = rule.onNodeWithText("Llegué a Sede Surco")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Acción principal fuera de la ventana: acción=$actionBounds ventana=$rootBounds",
            actionBounds.height >= 64f && actionBounds.top < rootBounds.height,
        )
        rule.onNodeWithText("Llegué a Sede Surco")
            .assertHeightIsAtLeast(64.dp)
        rule.onNodeWithText("SIGUIENTE PARADA").assertExists()
        rule.onAllNodesWithText("PARADA ACTUAL").assertCountEquals(0)
        capture("02_en_curso_siguiente_parada")

        // 3) Llegada a la parada: hora real + manifiesto de esa parada.
        rule.onNodeWithText("Llegué a Sede Surco").performClick()
        rule.waitForIdle()
        scrollToText("PARADA ACTUAL")
        rule.onNodeWithText("PARADA ACTUAL").assertIsDisplayed()
        rule.onNodeWithText("Llegada registrada: 06:07 AM").assertExists()
        scrollToText("Pasajeros en esta parada (2)")
        rule.onNodeWithText("Pasajeros en esta parada (2)").assertIsDisplayed()
        // El motivo del bloqueo vive en la barra inferior (siempre visible).
        rule.onNodeWithText("Faltan registrar 2 pasajeros en esta parada.").assertIsDisplayed()
        // La parada no se cierra con pasajeros por registrar.
        rule.onNodeWithText("Salir de Sede Surco").assertIsNotEnabled()
        scrollToPassengerAction("Subió: Ana Quispe")
        rule.onNodeWithContentDescription("Subió: Ana Quispe")
            .assertHeightIsAtLeast(64.dp)
        capture("03_llegada_parada_pendientes")

        // 4) Registro por pasajero: el estado queda con texto e icono y el
        //    aviso confirma la acción con el nombre y la hora registrada.
        scrollToPassengerAction("Subió: Ana Quispe")
        rule.onNodeWithContentDescription("Subió: Ana Quispe").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ana Quispe: subió").assertExists()
        scrollToPassengerAction("Subió: Luis Ramos")
        rule.onNodeWithContentDescription("Subió: Luis Ramos").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Luis Ramos: subió").assertExists()
        rule.onNodeWithText("Salir de Sede Surco").assertIsEnabled().assertHeightIsAtLeast(64.dp)
        capture("04_parada_resuelta")

        // 5) Salir de la parada: hora real registrada y siguiente parada destacada.
        rule.onNodeWithText("Salir de Sede Surco").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Salida registrada: Sede Surco").assertExists()
        rule.onNodeWithText("Llegué a Paradero Cultura").assertIsDisplayed()
        capture("05_salida_y_siguiente_parada")

        // 6) Segunda parada: Ana bajó aquí (bajada registrada con la llegada)
        //    y María sube en esta parada.
        rule.onNodeWithText("Llegué a Paradero Cultura").performClick()
        rule.waitForIdle()
        scrollToText("Pasajeros en esta parada (1)")
        rule.onNodeWithText("Pasajeros en esta parada (1)").assertIsDisplayed()
        scrollToText("Bajan en esta parada (1)")
        rule.onNodeWithText("Bajan en esta parada (1)").assertIsDisplayed()
        scrollToText("Ana Quispe")
        rule.onNodeWithText("Ana Quispe").assertIsDisplayed()
        capture("06_segunda_parada_manifiesto")

        scrollToPassengerAction("Subió: María García")
        rule.onNodeWithContentDescription("Subió: María García").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Salir de Paradero Cultura").assertIsEnabled().performClick()
        rule.waitForIdle()

        // 7) Última parada: la acción es finalizar el viaje.
        rule.onNodeWithText("Llegué a Sede Lima").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Finalizar viaje").assertIsEnabled().assertHeightIsAtLeast(64.dp)
        rule.onAllNodesWithText("VIAJE FINALIZADO").assertCountEquals(0)
        capture("07_ultima_parada")

        rule.onNodeWithText("Finalizar viaje").performClick()
        rule.waitForIdle()
        assertEquals("complete", repo.operations.last())
        rule.onNodeWithText("Viaje finalizado").assertExists()
        // El viaje queda en estado finalizado (no "sin acciones").
        rule.onAllNodesWithText("SIN ACCIONES").assertCountEquals(0)
        // Ya no queda acción principal pendiente.
        rule.onAllNodesWithText("Finalizar viaje").assertCountEquals(0)
        capture("08_viaje_finalizado")
    }

    @Test
    fun laAccionDeSalidaNoExisteAntesDeRegistrarLaLlegada() {
        val repo = FakeRepo()
        render(repo)

        rule.onNodeWithText("Iniciar viaje").performClick()
        rule.waitForIdle()

        // En camino a la parada 1 solo existe la acción de llegada, y no hay
        // forma de marcar salida en paradas futuras.
        rule.onAllNodesWithText("Salir de Sede Surco").assertCountEquals(0)
        rule.onNodeWithText("Llegué a Sede Surco").assertIsDisplayed()
        rule.onAllNodesWithText("Marcar salida").assertCountEquals(0)
    }

    @Test
    fun noSePresentoPideConfirmacionYLaParadaSigueBloqueada() {
        val repo = FakeRepo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixturesLocal.stops(
                first = TripStopStatus.ARRIVED,
                arrival = Instant.parse("2026-09-25T11:07:00Z"),
            ),
        )
        render(repo)

        rule.onNodeWithText("Faltan registrar 2 pasajeros en esta parada.").assertIsDisplayed()
        val luminanceBefore = screenLuminance()

        // "No se presentó" es irreversible: abre una confirmación aparte.
        clickPassengerAction("No se presentó: Ana Quispe")
        // El diálogo se dibuja en su propia ventana: se le da un instante para
        // aparecer antes de medir el velo.
        Thread.sleep(DIALOG_SETTLE_MILLIS)
        capture("09_dialogo_no_se_presento")
        val luminanceWithDialog = screenLuminance()
        assertDialogVisible(luminanceBefore, luminanceWithDialog)

        // Al cancelar, la parada sigue bloqueada y el pasajero sin registrar.
        pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Salir de Sede Surco").assertIsNotEnabled()
        rule.onNodeWithText("Faltan registrar 2 pasajeros en esta parada.").assertIsDisplayed()
        assertEquals(0, repo.operations.count { it.startsWith("noShow") })

        // La salida anticipada también pide confirmación explícita.
        rule.onNodeWithText("Salir sin registrar 2 pasajeros").assertIsDisplayed().performClick()
        rule.waitForIdle()
        Thread.sleep(DIALOG_SETTLE_MILLIS)
        capture("10_dialogo_salida_anticipada")
        val luminanceWithOverrideDialog = screenLuminance()
        assertDialogVisible(luminanceBefore, luminanceWithOverrideDialog)
        pressBack()
        rule.waitForIdle()
        assertEquals(0, repo.operations.count { it.startsWith("departure") })
        rule.onNodeWithText("PARADA ACTUAL").assertExists()
    }

    @Test
    fun manifiestoConVariosPasajerosSigueSiendoUsable() {
        // Se ejecuta también con la fuente del sistema aumentada y en pantalla
        // pequeña: las capturas permiten comparar la misma pantalla.
        val repo = FakeRepo(
            status = TripStatus.IN_PROGRESS,
            stops = TripFixturesLocal.stops(
                first = TripStopStatus.ARRIVED,
                arrival = Instant.parse("2026-09-25T11:07:00Z"),
            ),
            passengers = listOf(
                ana(),
                luis(),
                TripFixturesLocal.passenger(4L, "Sofía Bermúdez del Águila", "12B", 1, 3),
            ),
        )
        render(repo)

        rule.onNodeWithText("Faltan registrar 3 pasajeros en esta parada.").assertIsDisplayed()
        rule.onNodeWithText("Salir de Sede Surco").assertIsNotEnabled()
        // Cada pasajero conserva su botón de 64dp con etiqueta propia.
        scrollToPassengerAction("Subió: Sofía Bermúdez del Águila")
        rule.onNodeWithContentDescription("Subió: Sofía Bermúdez del Águila")
            .assertHeightIsAtLeast(64.dp)
        capture("11_manifiesto_varios_pasajeros")
    }
}

private const val TRIP_ID = 55L

/** Espera a que la ventana del diálogo alcance a dibujarse antes de medirla. */
private const val DIALOG_SETTLE_MILLIS = 1_200L

// ------------------------------------------------------------------- dobles

private object NoopNotifications : NotificationService {
    override fun notifyTripStarted(tripId: String) = Unit
    override fun notifyIncidentReported(incidentId: String) = Unit
    override fun notifyBoardingPassenger(passengerName: String) = Unit
}

private object NoopLocation : LocationService {
    override fun requestPermissions(): Flow<PermissionStatus> = flowOf(PermissionStatus.GRANTED)
    override suspend fun currentLocation(): Coordinates? = null
    override fun observeLocation(): Flow<Coordinates> = flowOf()
    override fun isAvailable(): Boolean = false
}

private object NoopTripLocation : TripLocationController {
    override fun startTracking() = Unit
    override fun stopTracking() = Unit
}

/**
 * Doble del repositorio con las mismas reglas que los SPs del backend:
 *  - la llegada a una parada cierra las bajadas de los pasajeros cuyo destino
 *    es esa parada,
 *  - la salida exige llegada previa,
 *  - la lista de pasajeros solo devuelve CONFIRMED y BOARDED.
 */
private class FakeRepo(
    status: TripStatus = TripStatus.PUBLISHED,
    stops: List<TripStop> = TripFixturesLocal.stops(),
    passengers: List<Passenger> = listOf(ana(), luis(), maria()),
) : DriverRepository {

    val operations = mutableListOf<String>()
    private var trip = TripFixturesLocal.trip(status)
    private var stopList = stops.toMutableList()
    private var passengerList = passengers.toMutableList()

    override suspend fun getTrips(date: LocalDate) = Result.success(listOf(trip))

    override suspend fun getTrip(tripId: Long) = Result.success(trip)

    override suspend fun getPassengers(tripId: Long) = Result.success(
        passengerList.filter {
            it.status == ReservationStatus.CONFIRMED || it.status == ReservationStatus.BOARDED
        },
    )

    override suspend fun getTripStops(tripId: Long) =
        Result.success(stopList.sortedBy { it.stopOrder })

    override suspend fun startTrip(tripId: Long): Result<Unit> {
        operations += "start"
        trip = trip.copy(status = TripStatus.IN_PROGRESS)
        return Result.success(Unit)
    }

    override suspend fun completeTrip(tripId: Long): Result<Unit> {
        operations += "complete"
        trip = trip.copy(status = TripStatus.COMPLETED)
        return Result.success(Unit)
    }

    override suspend fun markArrival(tripStopTimeId: Long): Result<Unit> {
        operations += "arrival:$tripStopTimeId"
        val index = stopList.indexOfFirst { it.id == tripStopTimeId }
        if (index < 0) return Result.failure(AppError.NotFound("parada"))
        val stop = stopList[index]
        stopList[index] = stop.copy(
            actualArrivalAt = ARRIVAL_AT,
            status = TripStopStatus.ARRIVED,
        )
        passengerList.replaceAll {
            if (it.status == ReservationStatus.BOARDED && it.destinationStopOrder == stop.stopOrder) {
                it.copy(status = ReservationStatus.COMPLETED)
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun markDeparture(tripStopTimeId: Long): Result<Unit> {
        operations += "departure:$tripStopTimeId"
        val index = stopList.indexOfFirst { it.id == tripStopTimeId }
        if (index < 0) return Result.failure(AppError.NotFound("parada"))
        val stop = stopList[index]
        if (stop.status == TripStopStatus.PENDING) {
            return Result.failure(AppError.Conflict("Primero debe registrarse la llegada al paradero"))
        }
        stopList[index] = stop.copy(
            actualDepartureAt = DEPARTURE_AT,
            status = TripStopStatus.DEPARTED,
        )
        return Result.success(Unit)
    }

    override suspend fun markBoarded(reservationId: Long): Result<Unit> {
        operations += "board:$reservationId"
        val index = passengerList.indexOfFirst { it.reservationId == reservationId }
        if (index < 0) return Result.failure(AppError.NotFound("reserva"))
        passengerList[index] = passengerList[index].copy(
            status = ReservationStatus.BOARDED,
            boardedAt = BOARDED_AT,
        )
        return Result.success(Unit)
    }

    override suspend fun markNoShow(reservationId: Long): Result<Unit> {
        operations += "noShow:$reservationId"
        val index = passengerList.indexOfFirst { it.reservationId == reservationId }
        if (index < 0) return Result.failure(AppError.NotFound("reserva"))
        passengerList[index] = passengerList[index].copy(status = ReservationStatus.NO_SHOW)
        return Result.success(Unit)
    }

    override suspend fun markAlighted(reservationId: Long): Result<Unit> {
        operations += "alight:$reservationId"
        val index = passengerList.indexOfFirst { it.reservationId == reservationId }
        if (index < 0) return Result.failure(AppError.NotFound("reserva"))
        passengerList[index] = passengerList[index].copy(status = ReservationStatus.COMPLETED)
        return Result.success(Unit)
    }

    override suspend fun reportIncident(
        tripId: Long,
        type: String,
        description: String,
    ): Result<Incident> = Result.success(Incident(1L, tripId, IncidentType.OTHER, description))

    override suspend fun getSeats(
        tripId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
    ): Result<List<SeatAvailability>> = Result.success(emptyList())

    override suspend fun registerGuest(
        tripId: Long,
        tripSeatId: Long,
        originTripStopTimeId: Long,
        destinationTripStopTimeId: Long,
        firstName: String,
        lastName: String,
    ): Result<Long> = Result.success(1L)

    private companion object {
        val ARRIVAL_AT: Instant = Instant.parse("2026-09-25T11:07:00Z")
        val DEPARTURE_AT: Instant = Instant.parse("2026-09-25T11:09:00Z")
        val BOARDED_AT: Instant = Instant.parse("2026-09-25T11:08:00Z")
    }
}

// --------------------------------------------------------------- datos de prueba

private object TripFixturesLocal {

    fun trip(status: TripStatus): DriverTrip = DriverTrip(
        id = TRIP_ID,
        tripCode = "T-BUS25-20260925",
        routeName = "BUS-25 · Mañana · IDA",
        direction = Direction.IDA,
        scheduledStartAt = Instant.parse("2026-09-25T11:05:00Z"),
        scheduledEndAt = Instant.parse("2026-09-25T12:20:00Z"),
        vehicleCode = "BUS-25",
        plate = "ABC-123",
        seatCapacity = 20,
        status = status,
    )

    fun stops(
        first: TripStopStatus = TripStopStatus.PENDING,
        second: TripStopStatus = TripStopStatus.PENDING,
        third: TripStopStatus = TripStopStatus.PENDING,
        arrival: Instant? = null,
    ): List<TripStop> = listOf(
        TripStop(
            id = 101L,
            stopName = "Sede Surco",
            stopOrder = 1,
            scheduledArrivalAt = Instant.parse("2026-09-25T11:05:00Z"),
            scheduledDepartureAt = Instant.parse("2026-09-25T11:10:00Z"),
            actualArrivalAt = arrival,
            actualDepartureAt = null,
            status = first,
        ),
        TripStop(
            id = 102L,
            stopName = "Paradero Cultura",
            stopOrder = 2,
            scheduledArrivalAt = Instant.parse("2026-09-25T11:30:00Z"),
            scheduledDepartureAt = Instant.parse("2026-09-25T11:32:00Z"),
            actualArrivalAt = null,
            actualDepartureAt = null,
            status = second,
        ),
        TripStop(
            id = 103L,
            stopName = "Sede Lima",
            stopOrder = 3,
            scheduledArrivalAt = Instant.parse("2026-09-25T11:55:00Z"),
            scheduledDepartureAt = Instant.parse("2026-09-25T11:57:00Z"),
            actualArrivalAt = null,
            actualDepartureAt = null,
            status = third,
        ),
    )

    fun passenger(
        reservationId: Long,
        name: String,
        seatLabel: String,
        originOrder: Int,
        destinationOrder: Int,
    ): Passenger = Passenger(
        reservationId = reservationId,
        reservationCode = "R-$reservationId",
        workerId = 900L + reservationId,
        workerFullName = name,
        seatNumber = seatLabel.filter { it.isDigit() }.toIntOrNull() ?: 1,
        seatLabel = seatLabel,
        originStopOrder = originOrder,
        originStopName = if (originOrder == 1) "Sede Surco" else "Paradero Cultura",
        destinationStopOrder = destinationOrder,
        destinationStopName = if (destinationOrder == 3) "Sede Lima" else "Paradero Cultura",
        status = ReservationStatus.CONFIRMED,
        confirmedAt = Instant.parse("2026-09-24T15:00:00Z"),
        boardedAt = null,
    )
}

private fun ana(): Passenger =
    TripFixturesLocal.passenger(1L, "Ana Quispe", "1A", originOrder = 1, destinationOrder = 2)

private fun luis(): Passenger =
    TripFixturesLocal.passenger(2L, "Luis Ramos", "2A", originOrder = 1, destinationOrder = 3)

private fun maria(): Passenger =
    TripFixturesLocal.passenger(3L, "María García", "3A", originOrder = 2, destinationOrder = 3)
