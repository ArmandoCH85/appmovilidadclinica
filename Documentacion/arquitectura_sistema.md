# Arquitectura del Sistema — Transporte Corporativo MVP

**Fecha:** 11 de Julio de 2026
**Autor:** Armando Jairo Correa Herrera
**Consultora:** A&M Solutions (Sitech)

---

## 1. Restricciones de Infraestructura

### Servidor VPS RackNerd

| Recurso | Valor |
|---|---|
| CPU | 1 vCPU — Intel Xeon Gold 6152 @ 2.10GHz |
| RAM | 1.9 GB total (1.6 GB disponibles) |
| Disco | 34 GB (27 GB libres) |
| SO | Ubuntu 24.04.4 LTS |
| Kernel | Linux 6.8.0-134-generic |
| Swap | 1.0 GB |
| Virtualización | KVM |

**Constraint crítico:** 1 vCPU + 1.9 GB RAM. Cada decisión arquitectónica debe optimizar para este budget.

---

## 2. Stack Tecnológico Decidido

| Capa | Tecnología | Justificación |
|---|---|---|
| **Backend** | Go (Modular Monolith) | Único stack que cabe en <150MB RAM manejando 100 conexiones concurrentes. JVM descartado (300-500MB heap), Node descartado (2-3x más pesado), Python descartado (GIL en 1 vCPU). |
| **Base de Datos** | MariaDB 10.6+ (compatible MySQL 8.0.16+) | Schema ya diseñado en `transporte_corporativo_mvp.sql`. InnoDB para bloqueo transaccional por segmentos. |
| **Mobile (Pasajero + Conductor)** | Kotlin Multiplatform + Compose Multiplatform | Un código base para Android (e iOS si se requiere). Compilación nativa, sin runtime pesado. |
| **Admin (Panel Central)** | Vue 3 SPA | Panel administrativo ligero, no requiere SSR. |
| **Reverse Proxy** | Nginx | Termina TLS, sirve static assets del admin y del mobile (si fuera PWA). |
| **Cache** | Go in-memory (sync.Map o LRU) | Sin Redis — las SPs ya tienen índices optimizados. |
| **Job Scheduler** | Goroutine ticker interna (cada 6h) | Generador de viajes futuros. Sin cron externo. |

---

## 3. Budget de Memoria (1.9 GB total)

| Componente | RAM estimada |
|---|---|
| OS + sistema | 200 MB |
| MariaDB | 300 MB (`innodb_buffer_pool_size=256M`, `max_connections=50`) |
| Backend Go | 80 MB (`MaxOpenConns=20`, `MaxIdleConns=10`) |
| Nginx | 25 MB |
| **Total** | **~605 MB** |
| Disponible restante | ~1.3 GB (margen para picos) |

---

## 4. Estructura de Directorios

```
appmovilidadclinica/
├── Documentacion/          # Docs de referencia (ya existe)
├── backend/                # Go Modular Monolith
│   ├── cmd/
│   │   └── server/         # main.go, entrypoint
│   ├── internal/
│   │   ├── modules/
│   │   │   ├── admin/      # Gestión de catálogos, rutas, plantillas
│   │   │   ├── trips/      # Generación, búsqueda, cronograma
│   │   │   ├── booking/    # Reserva, confirmación, check-in
│   │   │   ├── driver/     # Hoja de ruta, marca llegada, no-show
│   │   │   └── shared/     # DB, config, middleware, cache
│   │   └── platform/
│   │       ├── database/   # MariaDB connection pool
│   │       ├── server/     # HTTP router, middleware
│   │       └── jobs/       # Goroutine ticker — generador de viajes
│   ├── migrations/         # Schema SQL + SPs (transporte_corporativo_mvp.sql)
│   └── go.mod
├── mobile/                 # Kotlin Multiplatform + Compose Multiplatform
│   ├── shared/             # Lógica compartida (API client, modelos)
│   ├── androidApp/         # App Android (pasajero + conductor)
│   └── iosApp/             # App iOS (si se requiere)
├── admin/                  # Vue 3 SPA
│   ├── src/
│   │   ├── views/          # Gestión flota, rutas, matriz, calendarios
│   │   ├── api/            # HTTP client al backend Go
│   │   └── components/     # Tablas, formularios, matrices
│   └── package.json
├── deploy/                 # Config de despliegue
│   ├── nginx/
│   ├── systemd/            # .service para Go backend
│   └── mariadb/            # my.cnf tuning
└── .gitignore
```

---

## 5. Arquitectura de Capas — Backend Go

```
┌─────────────────────────────────────────────┐
│  HTTP Router (chi v5)                        │
│  Middleware: Auth (JWT), CORS, Logging      │
├─────────────────────────────────────────────┤
│  Handlers (thin) — parsea request, valida   │
│  formato, llama a servicio, devuelve JSON   │
├─────────────────────────────────────────────┤
│  Services — orquestación de SPs + reglas    │
│  que NO están en las SPs                    │
├─────────────────────────────────────────────┤
│  Repository — ejecuta stored procedures     │
│  contra MariaDB (pool conexiones)          │
├─────────────────────────────────────────────┤
│  MariaDB — InnoDB, 22 tablas, SPs           │
│  (sp_search_trips, sp_confirm_reservation,  │
│   sp_mark_trip_stop_arrival, sp_mark_*,     │
│   sp_generate_trip_instance, etc.)          │
└─────────────────────────────────────────────┘
```

**Principio clave:** Go es una capa fina sobre stored procedures. Las SPs ya contienen toda la lógica de negocio (búsqueda atómica, reserva por segmentos, no-show, liberación). Go orquesta y agrega lo que falta.

---

## 6. Reglas de Negocio que NO están en las SPs (Go debe implementar)

1. **Una reserva por viaje por trabajador** — Las SPs no validan `(trip_id, worker_id)` único. Go debe verificar antes de llamar `sp_confirm_reservation`.
2. **QR Token flow** — El SP devuelve el UUID crudo una sola vez al confirmar. Go lo pasa al cliente (mobile). El cliente lo muestra como QR. El conductor escanea, Go hashea SHA-256 y compara con `qr_token_hash`.
3. **Auth & roles** — JWT con claims `role` (ADMIN/DRIVER/WORKER) y `user_id`. Middleware valida por ruta.

---

## 7. Decisiones de Negocio (resueltas)

| Decisión | Resolución |
|---|---|
| Cantidad de reservas por trabajador por viaje | **Una** reserva por viaje por worker. Validación en Go antes de `sp_confirm_reservation`. |
| Reserva IDA+VUELTA atómica | **No atómica.** Cada dirección se confirma independientemente con `booking_group_uuid` para relacionarlas informativamente. |
| Reutilización en paraderos durante VUELTA | **No permitida.** Se mantiene regla estricta: subida solo en SEDE. La disponibilidad matemática existe pero `sp_search_trips` la rechaza. |

---

## 8. Módulos del Sistema

### 8.1 Módulo Admin (Vue 3 SPA)
- Gestión de catálogos: `transport_stops`, `vehicles`, `vehicle_seats`, `users`
- Configuración de rutas: `transport_routes`, `route_stops`, `route_segments`
- Matriz de tiempos: `travel_time_profiles`, `route_segment_travel_times`
- Calendarios: `service_calendars`, `service_calendar_exceptions`
- Plantillas de viaje: `trip_templates`
- Monitoreo: `trip_instances`, `trip_incidents`, `trip_generation_runs`

### 8.2 Módulo Pasajero (Mobile — KMP)
- Búsqueda de viajes (origen, destino, fecha)
- Visualización de asientos disponibles por tramo
- Reserva con validación de reglas IDA/VUELTA
- Check-in a bordo (protege contra NO_SHOW)
- QR code para validación del conductor

### 8.3 Módulo Conductor (Mobile — KMP)
- Hoja de ruta dinámica por viaje
- Marca de llegada real a paradero (dispara cronómetro de tolerancia)
- Validación de pasajeros via QR
- Marca de abordaje / no-show / bajada
- Reporte de incidencias

---

## 9. Flujo de Reserva (end-to-end)

```
[Mobile Pasajero]
  1. Busca viaje (origen, destino, fecha)
     → GET /api/trips/search
     → Go llama sp_search_trips(origen, destino, fecha)
     → Devuelve viajes + asientos disponibles por tramo

  2. Selecciona asiento y confirma
     → POST /api/reservations
     → Go valida: 1 reserva por trip por worker (in-memory check)
     → Go valida: ventana de reserva (booking_opens_at < ahora < booking_closes_at)
     → Go llama sp_confirm_reservation(trip_id, seat, origen, destino, worker_id)
     → SP bloquea celdas trip_seat_segments en transacción InnoDB
     → SP devuelve qr_token (UUID crudo, una sola vez)

[Mobile Conductor]
  3. Marca llegada a paradero
     → POST /api/trip-stops/:id/arrival
     → Go llama sp_mark_trip_stop_arrival
     → Registra actual_arrival_at = CURRENT_TIMESTAMP
     → Inicia cronómetro de tolerancia

  4. Valida pasajero (QR scan)
     → POST /api/reservations/:id/verify-qr
     → Go hashea token recibido (SHA-256)
     → Compara con qr_token_hash en DB
     → Si match: llama sp_mark_reservation_boarded

  5. Si no aborda en tolerancia
     → Job Go verifica: actual_arrival_at + no_show_tolerance_minutes < ahora
     → Llama sp_mark_reservation_no_show
     → SP libera celdas, registra evento, NO toca reservation_segments

  6. Pasajero baja en destino
     → POST /api/reservations/:id/alight
     → Go llama sp_mark_reservation_alighted
     → Cierra reserva
```

---

## 10. Configuración de Producción

### MariaDB (`my.cnf`)
```ini
[mysqld]
innodb_buffer_pool_size = 256M
max_connections = 50
innodb_log_file_size = 64M
innodb_flush_log_at_trx_commit = 2
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
```

### Go Backend (env vars)
```bash
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=transport_app
DB_PASSWORD=***
DB_NAME=transporte_corporativo_mvp
DB_MAX_OPEN_CONNS=20
DB_MAX_IDLE_CONNS=10
JWT_SECRET=***
HTTP_PORT=8080
TRIP_GENERATOR_INTERVAL_HOURS=6
TRIP_GENERATOR_HORIZON_DAYS=30
TZ=America/Lima
```

### Nginx
```nginx
server {
    listen 443 ssl http2;
    server_name transporte.example.com;

    # Admin Vue SPA
    location / {
        root /var/www/admin/dist;
        try_files $uri $uri/ /index.html;
    }

    # API Go
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 11. Job Generador de Viajes

- **Implementación:** Goroutine interna con `time.Ticker` cada 6 horas
- **Horizonte:** 30 días móviles
- **Lógica:**
  1. Inserta `trip_generation_runs` con `status=RUNNING`
  2. Recorre `trip_templates` activas × fechas del horizonte
  3. `fn_service_operates(calendar_id, fecha)` → si no opera, skip
  4. `sp_generate_trip_instance(template_id, fecha, run_id)` → idempotente
  5. Para cada tramo: `fn_select_travel_time_profile` elige perfil por prioridad
  6. Genera `trip_stop_times`, `trip_segments`, `trip_seats`, `trip_seat_segments`
  7. Valida solapamientos de vehículo/conductor (`vw_schedule_conflicts`)
  8. Publica o deja en DRAFT según `trip_templates.automatic_publish`
  9. Cierra `trip_generation_runs` con contadores

**Sin cron externo.** El ticker corre dentro del proceso Go y sobrevive a reinicios (verifica al arranque si hay viajes faltantes en el horizonte).

---

## 12. Seguridad

- **Auth:** JWT stateless con expiración 24h, sin refresh token
- **Password:** `password_hash` en `users` (bcrypt o argon2)
- **QR Token:** SHA-256 hash en DB, token crudo solo se devuelve una vez al confirmar
- **SQL Injection:** Go usa `database/sql` con prepared statements para todas las SPs
- **CORS:** Configurado en Nginx, no en Go (menor overhead)
- **Rate limiting:** Middleware Go por IP (contador in-memory sync.Map)