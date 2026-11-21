# Integración GPS Traccar → aplicación Android Kotlin

## 1. Objetivo

Integrar en la aplicación Android existente las ubicaciones GPS de **dos vehículos** registrados en la plataforma MS Global GPS:

- **CJI-579**
- **C0C-965** (atención: contiene el número **0**, no la letra O)

La app ya muestra ambos vehículos. **No hay que crear otra aplicación ni cambiar los GPS físicos**. Se requiere consultar la plataforma Traccar, identificar los vehículos correctos y actualizar su ubicación en la pantalla/mapa de Android cada vez que haya una nueva posición.

El alcance mínimo es **solo lectura**: latitud, longitud, fecha/hora de posición y estado. Opcionalmente, si la interfaz ya lo utiliza, también velocidad y rumbo.

## 2. Datos de acceso y documentación

| Concepto | Valor |
|---|---|
| Sitio web | https://gps.msglobalgps.com/login |
| API base prevista | `https://gps.msglobalgps.com/api/` |
| Usuario | `turismo@inkaiko.com` |
| Contraseña de acceso para pruebas | `$Turismo85` |
| OpenAPI de Traccar | https://raw.githubusercontent.com/traccar/traccar/master/openapi.yaml |
| Referencia oficial | https://www.traccar.org/traccar-api/ |

**Importante:** el OpenAPI del enlace describe la versión publicada de Traccar y no comprueba por sí solo la versión/configuración del servidor MS Global GPS. El desarrollador debe verificar que los endpoints respondan en ese dominio con la cuenta suministrada.

**Seguridad:** este documento contiene una credencial sensible: compartirlo solo con el desarrollador autorizado y cambiar la contraseña cuando ya no sea necesaria. No incrustar usuario/contraseña compartidos en el APK, `BuildConfig`, repositorio Git, logs ni capturas. Si la app se distribuye a usuarios finales, usar un backend de integración propio para guardar las credenciales y devolver solo los datos autorizados, o un mecanismo de autenticación individual adecuado. En pruebas locales, usar variables de entorno y credenciales suministradas de forma segura.

## 3. Flujo de datos requerido

```text
Traccar de MS Global GPS
       │
       ├─ GET /api/devices   → identificar id interno por placa
       │
       └─ GET /api/positions → últimas posiciones disponibles
                                   │
                            unir por deviceId
                                   │
                          seleccionar dos placas
                                   │
                          Android Kotlin (UI/mapa)
```

**Claves de la integración:**

- La placa (`CJI-579` / `C0C-965`) es una referencia comercial, **no necesariamente el identificador de la API**.
- `devices[].id` es el identificador interno de Traccar.
- `positions[].deviceId` permite vincular cada posición con su dispositivo.
- `positions[].latitude` y `positions[].longitude` son las coordenadas a visualizar.
- `devices[].name` puede contener la placa; comprobar el JSON real. Si la placa no está en `name`, inspeccionar campos/atributos disponibles y registrar una asociación explícita y validada. No suponer que `uniqueId` es la placa.

## 4. Paso 1: comprobar conectividad y autenticación

En una terminal de desarrollo con `curl` y `jq` instalados, ejecutar:

```bash
export TRACCAR_USER='turismo@inkaiko.com'
read -rsp 'Contraseña Traccar: ' TRACCAR_PASSWORD; echo

curl -fsS \
  --user "$TRACCAR_USER:$TRACCAR_PASSWORD" \
  -H 'Accept: application/json' \
  'https://gps.msglobalgps.com/api/devices' \
  | jq '.[] | {id, name, uniqueId, status, lastUpdate, positionId}'
```

**Esperado:** un arreglo JSON con dispositivos accesibles para la cuenta. Si devuelve `401`, revisar autenticación/permisos. Si responde HTML o `404`, comprobar la ruta, el reverse proxy y la versión real del servicio. **No continuar suponiendo datos**.

Traccar documenta autenticación HTTP Basic o por sesión. El ejemplo utiliza Basic **solo para probar la API en un entorno controlado**.

## 5. Paso 2: localizar las dos placas y obtener sus IDs

En la respuesta del paso anterior, ubicar `CJI-579` y `C0C-965`. Confirmar ambos `id` internos de dispositivo, anotándolos en una tabla de mapeo:

| Placa | `devices[].id` | Nombre/atributo donde aparece |
|---|---|---|
| CJI-579 | Pendiente de consultar | Pendiente de verificar |
| C0C-965 | Pendiente de consultar | Pendiente de verificar |

Se puede filtrar por nombre cuando el servidor realmente guarde la placa en ese campo:

```bash
curl -fsS --user "$TRACCAR_USER:$TRACCAR_PASSWORD" \
  'https://gps.msglobalgps.com/api/devices' \
  | jq '[.[] | select((.name // "" | ascii_upcase) == "CJI-579" or (.name // "" | ascii_upcase) == "C0C-965") | {id,name,uniqueId,status}]'
```

Si el filtro devuelve una lista vacía, **no significa necesariamente que los vehículos no existan**: inspeccionar todos los nombres, atributos o consultar cómo están registrados en la web.

## 6. Paso 3: obtener las últimas posiciones GPS

```bash
curl -fsS \
  --user "$TRACCAR_USER:$TRACCAR_PASSWORD" \
  -H 'Accept: application/json' \
  'https://gps.msglobalgps.com/api/positions' \
  | jq '.[] | {deviceId, latitude, longitude, fixTime, serverTime, valid, speed, course}'
```

**Importante:** `GET /api/positions` **sin parámetros** retorna las últimas posiciones conocidas de los dispositivos accesibles. Luego hay que quedarse solo con las entradas cuyo `deviceId` coincide con los IDs del paso 2. Esto **no garantiza datos en tiempo real**: una posición puede tener horas o días de antigüedad.

No usar `GET /api/positions?deviceId=...` como acceso directo universal a la última posición: la especificación actual indica que, al usar `deviceId`, se requieren `from` y `to` para consultas por fecha. Para esta necesidad es más sencillo consultar sin parámetros y filtrar por `deviceId`. La API también expone un filtro `id` para IDs de registros de posición; no confundirlo con el ID del dispositivo.

### Campos a mapear

| Campo Traccar | Uso Kotlin/Android |
|---|---|
| `deviceId` | Vincular posición a la placa correcta |
| `latitude` | Latitud GPS, tipo `Double` |
| `longitude` | Longitud GPS, tipo `Double` |
| `fixTime` | Fecha/hora de la posición GPS |
| `serverTime` | Fecha/hora de recepción por servidor |
| `valid` | Indica si el fix GPS fue marcado válido |
| `speed` | Velocidad en **nudos**, convertir a km/h multiplicando por `1.852` |
| `course` | Rumbo en grados, si se usa un marcador orientado |
| `status` (del dispositivo) | Conexión `online`, `offline` o `unknown`; no equivale a movimiento |

**Nota cartográfica:** en Google Maps Android `LatLng(latitude, longitude)`; en GeoJSON/MapLibre el arreglo es `[longitude, latitude]`. No intercambiar el orden.

## 7. Paso 4: implementar el cliente de red Kotlin

Si el proyecto ya usa Retrofit/OkHttp, **reutilizar el cliente existente**. No introducir otra librería HTTP sin necesidad. Ejemplo mínimo con Retrofit y un conversor JSON ya configurado:

```kotlin
import retrofit2.http.GET

data class TraccarDevice(
    val id: Long,
    val name: String,
    val uniqueId: String? = null,
    val status: String? = null,
    val positionId: Long? = null
)

data class TraccarPosition(
    val id: Long,
    val deviceId: Long,
    val latitude: Double,
    val longitude: Double,
    val fixTime: String? = null,
    val serverTime: String? = null,
    val valid: Boolean? = null,
    val speed: Double? = null,
    val course: Double? = null
)

interface TraccarApi {
    @GET("devices")
    suspend fun getDevices(): List<TraccarDevice>

    @GET("positions")
    suspend fun getPositions(): List<TraccarPosition>
}
```

Configurar la URL base con barra final:

```text
https://gps.msglobalgps.com/api/
```

La implementación HTTP debe inyectar el mecanismo de autenticación **aprobado para el entorno**, sin persistir la clave compartida en el binario Android. Si se utiliza un backend propio, estas llamadas se hacen allí y Kotlin consume el endpoint seguro del backend.

### Asociar los datos (ejemplo de lógica)

```kotlin
val placasObjetivo = setOf("CJI-579", "C0C-965")

val devices = api.getDevices()
val positionsByDeviceId = api.getPositions().associateBy { it.deviceId }

val vehicles = devices
    .filter { it.name.trim().uppercase() in placasObjetivo }
    .map { device ->
        val position = positionsByDeviceId[device.id]
        device to position  // Puede ser null: no inventar coordenadas.
    }
```

**Este filtro por `name` es únicamente un ejemplo:** debe adaptarse si el servidor registra la placa en otro campo. Confirmar que se identificaron **exactamente los dos vehículos**, que cada posición corresponde al `id` esperado y que no hay duplicados.

## 8. Paso 5: actualizar la pantalla/mapa de la aplicación

1. Mantener los registros/elementos existentes para `CJI-579` y `C0C-965`.
2. Por cada placa, obtener su `deviceId` validado y encontrar `positions[].deviceId == deviceId`.
3. Si hay posición válida, actualizar latitud, longitud y hora de última posición.
4. Actualizar el marcador ya existente en el mapa (no crear uno adicional por cada consulta).
5. Si no hay posición, o si el GPS no está actualizado, mantener un estado **sin ubicación / última ubicación conocida**, según el diseño existente. No poner coordenadas `0,0`.
6. No desplazar la cámara automáticamente en cada actualización; hacerlo solo cuando el usuario haya seleccionado seguir un vehículo.
7. Mostrar al usuario fecha/hora de última posición convertida a la zona horaria de la app; Traccar suele exponer timestamps ISO 8601 en UTC.
8. Mostrar posición atrasada como **última ubicación conocida** y no como “en vivo”. `status=online` tampoco prueba que el GPS haya emitido una posición reciente.

## 9. Paso 6: elegir mecanismo de actualización

### Opción A: REST periódico — primera entrega sencilla

- Consultar `/api/devices` al iniciar, al reconectar y cuando haga falta actualizar el mapeo.
- Consultar `/api/positions` mientras la pantalla de monitoreo esté visible. Para una primera implementación, usar **15–30 segundos como intervalo inicial ajustable**, sujeto a pruebas y límites de la plataforma.
- Ejecutar el trabajo fuera del hilo principal, mediante coroutines/Flow y el ViewModel o arquitectura ya utilizada en el proyecto.
- Detener el sondeo cuando la pantalla se cierra o deja de estar activa, para evitar consumo innecesario.
- Evitar llamadas concurrentes superpuestas; aplicar reintento con backoff ante errores transitorios.

### Opción B: WebSocket — para actualizaciones más inmediatas

Traccar recomienda WebSocket cuando se necesitan actualizaciones continuas:

```text
wss://gps.msglobalgps.com/api/socket
```

**Requisito:** Traccar autentica el WebSocket mediante **cookie de sesión**, no mediante Basic Auth directo en la conexión WebSocket.

Secuencia documentada:

1. Crear una sesión con `POST /api/session`, usando `application/x-www-form-urlencoded` con `email` y `password`, ejecutado desde un entorno seguro (preferiblemente backend).
2. Conservar la cookie de sesión emitida por el servidor (`JSESSIONID`, si ese es el nombre efectivo).
3. Establecer conexión `wss://gps.msglobalgps.com/api/socket` enviando la cookie válida.
4. Escuchar mensajes JSON que pueden incluir `positions`, `devices` o `events`.
5. Aplicar únicamente las posiciones cuyos `deviceId` correspondan a los dos vehículos.
6. Reconectar con backoff y volver a consultar el estado inicial tras cortes.
7. Verificar que el proxy de `gps.msglobalgps.com` permite la actualización HTTP a WebSocket.

Para un MVP funcional con dos vehículos, implementar **primero REST periódico** y migrar a WebSocket cuando se requiera seguimiento más continuo. Nunca mantener una conexión WebSocket con credenciales compartidas embebidas en un APK público.

## 10. Validaciones obligatorias

- **Autenticación:** credenciales autorizadas responden correctamente; errores `401`/`403` se muestran y no se esconden.
- **Visibilidad:** la cuenta realmente tiene acceso a ambas placas.
- **Identidad:** se confirma mapeo placa ↔ ID de Traccar ↔ posición recibida.
- **Datos:** latitud en rango `[-90, 90]`, longitud en `[-180, 180]`; evitar marcadores en `0,0` si eso representa datos vacíos.
- **Temporalidad:** detectar GPS sin novedades; distinguir `fixTime`, `serverTime` y conexión `status`.
- **Red:** mostrar la última posición válida en caso de falta de internet, con hora y estado visual de desactualización.
- **UI:** cuando llega una posición nueva se mueve el marcador del vehículo correcto; no se intercambian las placas ni se duplican elementos.
- **Seguridad:** HTTPS, secretos fuera de APK y repositorio; nunca registrar headers de autenticación o contraseñas.
- **Compatibilidad:** validar los endpoints en la instancia real de MS Global GPS. No asumir que el servidor tiene exactamente la versión de `master` del OpenAPI.

## 11. Criterios de aceptación y entregables

Se considera terminada la tarea cuando:

- [ ] La autenticación contra MS Global GPS funciona.
- [ ] Se identifica en Traccar el ID real de **CJI-579**.
- [ ] Se identifica en Traccar el ID real de **C0C-965**.
- [ ] Se obtiene la última latitud/longitud de cada vehículo, si hay posición disponible.
- [ ] Ambos vehículos ya existentes se actualizan correctamente en la pantalla Kotlin.
- [ ] Se muestra la fecha/hora real de la última posición y se identifica la información antigua.
- [ ] Se manejan pérdidas de red, posiciones ausentes y credenciales inválidas sin bloquear la app.
- [ ] No hay contraseñas hardcodeadas ni logs que filtren secretos.
- [ ] Se entregan los dos IDs encontrados y una evidencia de prueba funcional, sin exponer credenciales.

### Resumen para el desarrollador

**No hace falta modificar Traccar ni instalar un GPS nuevo.** Se debe consumir su API REST ya existente: **`GET /api/devices` → identificar las placas y sus IDs; `GET /api/positions` → relacionar por `deviceId` y extraer `latitude`, `longitude` y `fixTime`; actualizar los dos vehículos en Kotlin**. Para tiempo real sostenido, usar `/api/socket` con sesión autenticada.

### Documentación oficial consultada

- OpenAPI: https://raw.githubusercontent.com/traccar/traccar/master/openapi.yaml
- API REST y WebSocket: https://www.traccar.org/traccar-api/
- Referencia interactiva: https://www.traccar.org/api-reference/

