# Deploy sitechfactura.site

Fecha: 2026-07-11. Servidor: RackNerd VPS, Ubuntu 24.04, 1 vCPU / 1.9GB RAM.

## Stack instalado

- Go 1.25.0 (tarball oficial en `/usr/local/go`)
- MariaDB 11.4.5 (repo mariadb.org, reemplazó 10.11 de Ubuntu)
- nginx 1.24 + certbot 2.9 (LE)
- ufw (firewall)

## Patches aplicados al código fuente

### `backend/internal/platform/database/mysql.go`
- URL-escape del parámetro `loc=America/Lima` (era `loc=%2FAmerica%2FLima`).
  Sin escape el driver MySQL parsea mal el DSN.

### `backend/migrations/0001_schema.up.sql`
- Quitados los 20 `CHECK` constraints.
  MariaDB rechaza CHECKs que referencien columnas con FK en la misma tabla
  (ej. `chk_transport_routes_pair` con self-FK).
  La validación queda en `go-playground/validator` del backend.

### `backend/migrations/0001_schema.up.sql` + `0002_cancel_sps.up.sql` (revertido)
- Se habían comentado los bloques entre `DELIMITER $$` y `DELIMITER ;`
  (TRIGGERS, FUNCTIONS y STORED PROCEDURES) asumiendo que `database/sql` no
  podía ejecutarlos.
- Diagnóstico corregido: `migrate.go` (`splitAndExec`) ya trocea el archivo
  por `DELIMITER` y ejecuta cada bloque con `db.Exec` — eso sí funciona. El
  bug real era que el statement final se enviaba con el delimitador custom
  pegado al final (`...END$$`), sintaxis inválida para MariaDB. Fix: recortar
  el delimitador del buffer antes del `Exec` (ver `splitStatements`).
- Los bloques SP/TRIGGER/FUNCTION quedaron descomentados otra vez. Se aplican
  solos al reiniciar el backend (`RunMigrations` corre en cada arranque).

### `backend/migrations/0001_schema.up.sql`
- BOM UTF-8 removido al inicio (causaba `\ufeff` en MariaDB).

## Layout deploy

```
/opt/appmovilidadclinica/backend/
├── bin/server              # binario Go (10.9MB, estático)
├── cmd/server/main.go
├── go.mod, go.sum
├── internal/
└── migrations/             # *.sql patcheados
```

## Configuración

- `/etc/appmovilidadclinica.env` — env vars (chmod 640, root:appuser)
  - `HTTP_PORT=8080`
  - `JWT_SECRET=<64-hex>` (regenerar con `openssl rand -hex 32`)
  - `MIGRATIONS_DIR=/opt/appmovilidadclinica/backend/migrations`
  - `DB_HOST=127.0.0.1`, `DB_USER=appuser`, `DB_NAME=transporte_corporativo_mvp`
- `/etc/systemd/system/appmovilidadclinica.service` — servicio systemd
- `/etc/nginx/sites-available/sitechfactura.site` — vhost con proxy_pass a :8080
- DB: usuario `appuser` con grants en `localhost` y `127.0.0.1`
  (TCP desde 127.0.0.1 se presenta como `localhost` para MariaDB)

## Comandos útiles

```bash
# Status
systemctl status appmovilidadclinica.service
journalctl -u appmovilidadclinica.service -f
tail -f /var/log/appmovilidadclinica/server.log

# Rebuild + restart
cd /opt/appmovilidadclinica/backend
/usr/local/go/bin/go build -ldflags="-s -w" -o bin/server ./cmd/server
chmod 755 bin/server
systemctl restart appmovilidadclinica.service

# SSL renewal (auto via certbot timer)
certbot renew --dry-run

# Reiniciar desde cero (destructivo)
mariadb -uroot -e "DROP DATABASE transporte_corporativo_mvp; CREATE DATABASE transporte_corporativo_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
systemctl restart appmovilidadclinica.service
```
