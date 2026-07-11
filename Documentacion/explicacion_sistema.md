# Documento de Arquitectura y Análisis Funcional: Sistema de Gestión de Transporte Corporativo

**Autor:** Armando Jairo Correa Herrera - Analista Funcional / Ingeniero de Sistemas  
**Consultora:** A&M Solutions (Sitech)  
**Fecha de elaboración:** 11 de Julio de 2026  

---

## 1. CONTEXTO GENERAL DEL SISTEMA

**Objetivo:** 
Plataforma integral que permite a los trabajadores de la empresa reservar asientos en los buses de transporte corporativo de manera ordenada, auditable y matemática, maximizando la ocupación de la flota.

**Rutas y Escalas:**
*   **Tipos de Ruta:** Los trayectos se dividen en dos modalidades estrictas: **IDA** (hacia el trabajo) y **VUELTA** (regreso a casa).
*   **Tipos de Escalas:** Existen puntos intermedios en la ruta clasificados lógicamente en dos categorías: **SEDES** (clínicas/oficinas corporativas) y **PARADEROS** (puntos en la vía pública).

---

## 2. REGLAS DE NEGOCIO Y OPERACIÓN

### 2.1. Regla de Reserva por Tramos
Los asientos no se reservan asumiendo la ocupación del viaje completo, sino por **tramos específicos** calculados mediante un Orden de Subida y un Orden de Bajada.

*   **Regla de IDA:** El sistema permite que el trabajador inicie su viaje (subida) en cualquier `PARADERO`, pero lo obliga estrictamente a que su destino final (bajada) sea una `SEDE`.
*   **Regla de VUELTA:** El sistema bloquea cualquier intento de iniciar el viaje desde la calle. El trabajador está obligado a iniciar su tramo (subida) en una `SEDE`, pero puede finalizar su viaje (bajar) en cualquier `PARADERO` hacia su hogar.

### 2.2. Visibilidad de Disponibilidad Futura
La aplicación móvil muestra matemáticamente cuándo se liberará un asiento ocupado evaluando los tramos.
> **Escenario de Ocupación Escalonada:** Si un trabajador reserva el "Asiento 5" desde la *Sede A* (Orden 1) hasta el *Paradero 2* (Orden 3), un segundo trabajador que consulte la App verá que el "Asiento 5" va ocupado al inicio, pero que estará **100% disponible exactamente a partir del Paradero 2** (Orden 3) en adelante.

### 2.3. Confirmación y Regla de Bloqueo
*   **Blindaje:** Al confirmarse una reserva, el asiento queda bloqueado para ese trabajador única y exclusivamente en el segmento exacto de su trayecto.
*   **Liberación Automática:** El asiento se libera bajo dos condiciones: 
    1. Matemáticamente, al llegar al Orden de Bajada programado.
    2. Operativamente, si el conductor marca su llegada y el pasajero incurre en *No-Show* tras vencer el tiempo de tolerancia.

---

## 3. ARQUITECTURA DE MÓDULOS

### 3.1. Módulo Administrativo (Panel Central)
*   **Gestión de Catálogos:** Registro de flota (`capacidad_asientos`), conductores (`nro_licencia`, vigencia) y usuarios (`roles`).
*   **Configuración de Rutas (Matriz):** Definición estática de tiempos entre ubicaciones (`SEDE`/`PARADERO`) creando el "ADN" del recorrido.
*   **Generador Automático:** Motor que proyecta los viajes futuros en base a una plantilla de horarios (`TBL_HORARIOS_BASE`), excluyendo feriados nacionales.

### 3.2. Módulo del Pasajero (Aplicación Móvil)
*   **Autoservicio:** Búsqueda de disponibilidad y bloqueo de asientos en tiempo real validando las reglas de tramos, IDA y VUELTA.
*   **Check-in (A bordo):** Confirmación de asistencia al momento de subir al bus para proteger la reserva del sistema automático de cancelaciones.

### 3.3. Módulo del Conductor (Aplicación Táctica)
*   **Hoja de Ruta Dinámica:** Visualización del itinerario y pasajeros programados por cada paradero.
*   **Telemetría Humana (Llegada Real):** El conductor emite la confirmación física ("Llegué al paradero"). Esta acción registra la `hora_llegada_real` y es el **único disparador** que activa el cronómetro de tolerancia de reservas.

---

## 4. SISTEMA ANTI-RETRASOS Y TOLERANCIA

Para evitar que el sistema castigue injustamente a los pasajeros por demoras en el tráfico vehicular, se implementó una regla de protección mediante un Job automático:

1.  El contador de tolerancia (Ej. 10 minutos) **jamás** se inicia con la hora programada (`horaestimada`).
2.  El cronómetro inicia exclusivamente cuando el chofer actualiza la `hora_llegada_real` en el paradero.
3.  Si transcurre la ventana de tolerancia y el trabajador sigue en estado `CONFIRMADO` (no hizo check-in), el motor lo pasa a estado `NOCONFIRMO`. El asiento queda libre para los siguientes tramos.
4.  Si el chofer olvida marcar su llegada (`hora_llegada_real` = NULL), el sistema entra en modo de contingencia: asume demora indefinida y **protege** todas las reservas de ese paradero.

---

## 5. DICCIONARIO DE DATOS RESUMIDO (Base Relacional)

La base de datos MySQL está dividida en tres capas normalizadas para optimizar consultas de rango por orden:

1.  **Autenticación y Actores:** 
    *   `TBL_USUARIO` (Login y roles).
    *   `TBL_CONDUCTOR` y `TBL_TRABAJADOR` (Perfiles operativos extendidos).
2.  **Maestros y Configuración:** 
    *   `TBL_VEHICULO`, `TBL_LUGAR`, `TBL_RUTA`.
    *   `TBL_MATRIZ_RUTA` (Tiempos y órdenes estáticos).
    *   `TBL_CONFIGURACION` (Parámetros globales dinámicos como el tiempo de gracia).
3.  **Transaccional Diario:** 
    *   `TBL_VIAJE` (Cabecera del bus en movimiento).
    *   `TBL_DETALLE_VIAJE` (Tramos calculados y llegadas reales del chofer).
    *   `TBL_RESERVA` (Tabla plana que almacena el `nro_asiento`, `orden_subida`, `orden_bajada` y estado del pasajero).