# App Movilidad Clínica

Sistema de transporte corporativo para una clínica: reserva de asientos por
tramo, app de pasajero, app de conductor y panel de administración. Los viajes
se generan solos a partir de plantillas recurrentes y calendarios de servicio.

## Componentes

| Carpeta | Qué es | Stack |
|---|---|---|
| `backend/` | API REST + motor de viajes/reservas | Go 1.25, MariaDB (stored procedures) |
| `admin/` | Panel de administración | Vue 3, Vite, PrimeVue |
| `driver-android-current/` | App del conductor **(vigente)** | Kotlin Multiplatform (Compose) |
| `driver-android/` | App del conductor anterior | Jetpack Compose |
| `passenger-android/` | App del pasajero | Jetpack Compose |
| `Documentacion/`, `docs/` | Documentación funcional, arquitectura y diccionario de datos | Markdown |

## Requisitos

- **Go** 1.25+
- **MariaDB** 10.6+ (o MySQL 8.0.16+)
- **Node.js** 20+ — solo para el admin
- **Android Studio + Android SDK** — solo para las apps

## 1. Base de datos

```sql
CREATE DATABASE transporte_corporativo_mvp
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'appuser'@'localhost' IDENTIFIED BY 'tu_password';
GRANT ALL PRIVILEGES ON transporte_corporativo_mvp.* TO 'appuser'@'localhost';
FLUSH PRIVILEGES;
```

## 2. Backend

```bash
cd backend
cp ../.env.example ../.env       # editar credenciales y JWT_SECRET
set -a; source ../.env; set +a   # exportar las variables al shell
go run ./cmd/server
```

- Al arrancar, el backend aplica las migraciones de `backend/migrations`
  **una sola vez** por base (tabla `schema_migrations`). No hay que correrlas a mano.
- `JWT_SECRET` es obligatorio. `MIGRATIONS_DIR` por defecto es `./migrations`,
  así que arrancá desde `backend/`.
- Health check: `GET /api/health`.

### Cargar datos

Con el esquema ya migrado (basta con que el backend haya arrancado una vez):

```bash
# Operación real: paradas, vehículos, rutas, tramos, perfiles por franja,
# calendario y plantillas (Lima <-> Surco).
mariadb -u appuser -p transporte_corporativo_mvp < backend/scripts/seed_operacion_real.sql

# Alternativa: dataset de demostración genérico.
mariadb -u appuser -p transporte_corporativo_mvp < backend/scripts/seed_demo_data.sql
```

> `seed_operacion_real.sql` crea 2 conductores de desarrollo
> (`driver01` / `driver02`, contraseña `12345678`). **Cambiar esa contraseña
> fuera de desarrollo.**

## 3. Panel de administración

```bash
cd admin
cp .env.example .env             # VITE_API_BASE_URL=/api
npm install
npm run dev                      # desarrollo
npm run build                    # build de producción -> admin/dist
```

## 4. Apps Android

Las apps de release requieren un `keystore.properties` (no versionado). Ver
`driver-android-current/RELEASE_SIGNING.md`. El build se hace con Android Studio
o Gradle en una máquina con el Android SDK instalado.

## Migraciones

- Se ejecutan solas al arrancar el backend, en orden alfabético, y quedan
  registradas en `schema_migrations`. Cada `*.up.sql` corre una sola vez.
- Los `*.down.sql` nunca se ejecutan automáticamente.
- **No incluir `USE <base>;`** en las migraciones: la base sale del DSN
  (`DB_NAME`). Así las migraciones funcionan en cualquier entorno.

## Tests

```bash
cd backend
go test ./...
```

Los scripts `backend/scripts/verify_*.sql` corren verificaciones de los stored
procedures contra una base con datos.

## Deploy

Ver [`DEPLOY.md`](DEPLOY.md) para el procedimiento de producción (systemd,
nginx, migraciones y backups).

## Seguridad

- No versionar `.env`, keystores ni `Planilla_Usuarios.md` (ya están en `.gitignore`).
- `JWT_SECRET` y las credenciales de base de datos van por variables de entorno.
