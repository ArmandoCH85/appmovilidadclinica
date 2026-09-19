# Deploy — appmovilidadclinica

Servidor: RackNerd VPS, Ubuntu 24.04.1 vCPU / 1.9 GB RAM.
**Última verificación de este documento: 2026-09-19** (contra el servidor real).
Dominios servidos: `sitechfactura.site` y `movilidad.sitech.site`.

## Stack

- Go 1.25.0 (`/usr/local/go/bin/go`)
- MariaDB 11.4.5
- nginx 1.24 + certbot (Let's Encrypt)
- ufw

## Layout: repo vs deploy (¡son dos cosas distintas!)

| | Ruta | Qué es |
|---|---|---|
| **Repo git** | `/root/appmovilidadclinica` | El clon real. Acá se hace `git fetch/pull/build`. **Único lugar con historial git.** |
| **Deploy** | `/opt/appmovilidadclinica` | Directorio de servicio. **NO es un repo git.** Tiene el binario, una copia del fuente, `migrations/` y `scripts/`. |

```
/opt/appmovilidadclinica/backend/
├── bin/server              # binario Go en ejecución (~11 MB) + backups server.bak.*
├── cmd/server/main.go      # copia del fuente (debe estar sincronizada, ver §Deploy)
├── go.mod, go.sum
├── internal/               # copia del fuente
├── migrations/             # *.sql que ejecuta el runner (MIGRATIONS_DIR)
└── scripts/                # seed / verify / backfill
```

⚠️ **Nunca compiles desde `/opt`.** Su copia del fuente queda vieja en cuanto se
despliega un binario nuevo, así que `cd /opt/.../backend && go build` genera un
binario **anterior** y, al reiniciar, revierte features sin ningún error visible.
Se compila en `/root/appmovilidadclinica/backend` (§Deploy, paso 4).

## Configuración

- `/etc/appmovilidadclinica.env` (chmod 640, root:appuser)
  - `HTTP_PORT=8080`, `DB_HOST=127.0.0.1`, `DB_USER=appuser`, `DB_NAME=transporte_corporativo_mvp`
  - `JWT_SECRET=<64-hex>` (rotar con `openssl rand -hex 32` invalida todos los JWT vigentes)
  - `MIGRATIONS_DIR=/opt/appmovilidadclinica/backend/migrations`
- `/etc/systemd/system/appmovilidadclinica.service` → `ExecStart=/opt/appmovilidadclinica/backend/bin/server`
- `/etc/nginx/sites-available/sitechfactura.site` — un archivo, **dos** `server`:
  `sitechfactura.site` (SPA en `/var/www/sitechfactura`) y `movilidad.sitech.site`;
  ambos proxean `/api/` a `http://127.0.0.1:8080`.
- DB: usuario `appuser` con grants en `localhost` y `127.0.0.1`.

## Deploy paso a paso

```bash
# 0) Backup SIEMPRE primero (incluye SPs y triggers, no solo datos)
BK=/root/backup_$(date +%F_%H%M).sql
mariadb-dump -u appuser -p --single-transaction --routines --triggers --events \
  transporte_corporativo_mvp > "$BK"

# 1) Bajar el código EN EL REPO (no en /opt)
cd /root/appmovilidadclinica
git fetch origin
git checkout feat/extension-v2
git pull --ff-only origin feat/extension-v2
git log -1 --oneline

# 2) Tests (los 9 paquetes tienen que dar ok)
cd backend && /usr/local/go/bin/go test ./...

# 3) Si el commit trae MIGRACIONES nuevas, copiarlas a /opt ANTES del restart
#    (el runner las aplica al arrancar; ver §Migraciones)
cp /root/appmovilidadclinica/backend/migrations/00XX_*.sql \
   /opt/appmovilidadclinica/backend/migrations/

# 4) Compilar EN EL REPO y publicar el binario con mv (no cp)
/usr/local/go/bin/go build -ldflags="-s -w" -o /tmp/server_new ./cmd/server

# 5) Restart
mv /tmp/server_new /opt/appmovilidadclinica/backend/bin/server   # mv, no cp:
                                                                 # cp falla con "Text file busy"
systemctl restart appmovilidadclinica.service                    # ~3 s de corte
systemctl is-active appmovilidadclinica.service

# 6) Sincronizar el fuente de /opt para que un build ahí no quede viejo
cp -a cmd internal go.mod go.sum /opt/appmovilidadclinica/backend/
# (NO tocar /opt/.../migrations ni /opt/.../scripts: tienen extras propios)
```

## Verificación post-deploy

```bash
journalctl -u appmovilidadclinica.service -n 40 --no-pager | grep -iE "migra|error"
curl -s -o /dev/null -w "health=%{http_code}\n" http://127.0.0.1:8080/api/health
curl -s -o /dev/null -w "login=%{http_code}\n" -X POST http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"document_number":"<user>","password":"<pass>"}'

# ¿el binario desplegado tiene el código nuevo? (por marcadores de string)
grep -ac "sp_admin_close_trip" /opt/appmovilidadclinica/backend/bin/server
```

⚠️ **El `md5` del binario solo sirve si compilás en el mismo directorio y con el
mismo HEAD**: Go embebe `vcs.revision` (solo si el dir es un repo git) y las rutas
del fuente. Un build en `/opt` nunca dará el mismo hash que uno en `/root` aunque
el código sea idéntico. Para auditar de dónde salió:

```bash
/usr/local/go/bin/go version -m /opt/appmovilidadclinica/backend/bin/server | grep vcs
```

## Migraciones

- El runner (`internal/platform/database/migrate.go`, `RunMigrations`) corre **en
  cada arranque** y aplica **solo los `*.up.sql` cuyo nombre no esté ya en
  `schema_migrations`**. Los `.down.sql` **nunca** se ejecutan solos.
- Es idempotente: reiniciar dos veces no re-aplica nada.
- **Ya NO se borra nada al reiniciar.** El esquema viejo re-ejecutaba
  `0001_schema.up.sql` (con sus 22 `DROP TABLE`) en cada arranque; eso se
  terminó al introducir `0000_schema_tracking` + `schema_migrations`. Los datos
  sobreviven a los restarts.
- Estado actual:
  ```sql
  SELECT version, applied_at FROM schema_migrations ORDER BY version;
  ```
- Rollback de una migración: aplicar su `.down.sql` a mano (o restaurar el dump).
  El runner no lo hace.

## Suites de verificación (base descartable, no tocan producción)

Crean y borran su propia base (`transporte_verify_*`). Se corren **desde
`backend/migrations`** para que resuelvan los `source`:

```bash
cd backend/migrations
mariadb -u root < ../scripts/verify_guest_auto_alight.sql          # 7 tests
mariadb -u root < ../scripts/verify_guest_registration_guard.sql   # 10 tests
mariadb -u root < ../scripts/verify_reservation_manifest_guard.sql # 8 tests
mariadb -u root < ../scripts/verify_admin_trip_closure.sql         # 5 tests
```
Todos los `SELECT ... AS test` deben dar `OK`.

## Reparaciones de datos (todas idempotentes, con backup previo)

`backend/scripts/backfill_*.sql`. Se corren con `mariadb -u appuser -p
transporte_corporativo_mvp < script.sql`:

| Script | Qué repara |
|---|---|
| `backfill_0018_orphan_guests.sql` | invitados `BOARDED` + asientos ocupados en viajes cerrados |
| `backfill_closed_trip_manifest.sql` | reservas/invitados colgados en viajes `COMPLETED` |
| `backfill_actual_start_at.sql` | `actual_start_at` desde las marcas de parada (reportes vacíos) |
| `backfill_stale_reservations.sql` | reservas `CONFIRMED` de viajes vencidos que el job de NO_SHOW no puede limpiar |
| `backfill_actual_end_from_marks.sql` | `actual_end_at` reconstruido con la última parada marcada |

## Trampas conocidas

| Trampa | Consecuencia | Qué hacer |
|---|---|---|
| `go build` dentro de `/opt` | despliega código viejo (revierte features) | compilar en `/root/appmovilidadclinica/backend` |
| `cp` sobre `bin/server` en ejecución | `Text file busy` y el binario viejo sigue | usar `mv` + `systemctl restart` |
| Comparar `md5` entre directorios | falso "no coincide" | comparar dentro del mismo dir o usar `go version -m` |
| `/opt/.../migrations` es un **superset** del repo | contiene `0008_trip_stop_departure` (aplicada en 2026-08-29) que no está en git | no borrar archivos de ahí "que sobran" |
| Reiniciar sin copiar las migraciones nuevas a `/opt` | el runner no las ve y no se aplican | paso 3 del deploy |

## Comandos útiles

```bash
systemctl status appmovilidadclinica.service
journalctl -u appmovilidadclinica.service -f
tail -f /var/log/appmovilidadclinica/server.log
certbot renew --dry-run

# Reiniciar desde cero (DESTRUCTIVO: borra toda la base)
mariadb -uroot -e "DROP DATABASE transporte_corporativo_mvp; CREATE DATABASE transporte_corporativo_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
systemctl restart appmovilidadclinica.service   # recrea todo el esquema vacío
```

## Seguridad

- **Cuentas demo**: la base de producción tiene usuarios de demo (`90000001`
  ADMIN "Administrador Demo", `90000003..90000015` WORKER) creados con
  `backend/scripts/seed_demo_data.sql`. **La clave del admin demo se cambió el
  2026-09-19** (venía con `password`). Si se vuelve a correr un seed, revisar:
  el login es público en los dos dominios y `POST /api/auth/change-password`
  permite al propio usuario cambiarla.
- **Planilla de empleados**: `Planilla_Usuarios.md` (código, nombre, apellido,
  usuario, CeCo, área, gerencia, sede) **no debe versionarse**; está en
  `.gitignore` y se movió fuera del repo. El repo es público.
- `POST /auth/change-password` exige JWT y la clave actual; el `userID` sale del
  token, nunca del body.
