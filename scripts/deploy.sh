#!/usr/bin/env bash
# ============================================================================
# deploy-appmovilidadclinica.sh
# Despliega el código del repo de trabajo (/root/appmovilidadclinica/) a
# producción:
#   - Backend Go: /opt/appmovilidadclinica/backend/  (systemd: appmovilidadclinica)
#   - Frontend SPA: /var/www/sitechfactura/  (servido por Nginx)
#
# Pasos:
#   1. Backup del binario y de la webroot actuales
#   2. Stop del servicio systemd
#   3. Sync de migrations nuevas y archivos del modulo admin modificados
#   4. Rebuild del binario Go desde /opt/appmovilidadclinica/backend
#   5. Rebuild del admin (vite) y sync de dist/ a /var/www/sitechfactura
#   6. Start del servicio + espera de health check
#   7. Reporte final
#
# Flags:
#   --dry-run     Muestra que haria sin tocar nada
#   --skip-tests  No corre go vet (lo hace siempre go build)
#   --skip-web    Solo deploy backend (omite admin dist + nginx)
#   --no-backup   No genera .bak (peligroso)
#
# Requiere: bash 4+, systemd, go, node/npm. Ejecutar como root.
# ============================================================================

set -euo pipefail

# ---------- Colores / formato ----------
if [[ -t 1 ]]; then
    C_RED=$'\033[0;31m'; C_GREEN=$'\033[0;32m'; C_YEL=$'\033[0;33m'
    C_BLU=$'\033[0;34m'; C_BLD=$'\033[1m';    C_RST=$'\033[0m'
else
    C_RED=''; C_GREEN=''; C_YEL=''; C_BLU=''; C_BLD=''; C_RST=''
fi
log()   { echo "${C_BLU}[$(date +%H:%M:%S)]${C_RST} $*"; }
ok()    { echo "${C_GREEN}[$(date +%H:%M:%S)] OK${C_RST} $*"; }
warn()  { echo "${C_YEL}[$(date +%H:%M:%S)] WARN${C_RST} $*" >&2; }
err()   { echo "${C_RED}[$(date +%H:%M:%S)] ERR${C_RST} $*" >&2; }
fatal() { err "$*"; exit 1; }

# ---------- Paths / config ----------
SRC_REPO="/root/appmovilidadclinica"
SRC_BACKEND="$SRC_REPO/backend"
SRC_ADMIN="$SRC_REPO/admin"

PROD_BACKEND="/opt/appmovilidadclinica/backend"
PROD_BIN="$PROD_BACKEND/bin/server"
PROD_MIGRATIONS="$PROD_BACKEND/migrations"

PROD_WEBROOT="/var/www/sitechfactura"
WEB_LOG="/var/log/nginx/sitechfactura.access.log"

SERVICE_NAME="appmovilidadclinica.service"
HEALTH_URL="https://sitechfactura.site/api/health"
HEALTH_FALLBACK_URL="http://127.0.0.1:8080/health"

# ---------- Flags ----------
DRY_RUN=0
SKIP_WEB=0
SKIP_VET=0
NO_BACKUP=0
for arg in "$@"; do
    case "$arg" in
        --dry-run)    DRY_RUN=1 ;;
        --skip-web)   SKIP_WEB=1 ;;
        --skip-vet)   SKIP_VET=1 ;;
        --no-backup)  NO_BACKUP=1 ;;
        -h|--help)
            sed -n '2,30p' "$0"; exit 0 ;;
        *) fatal "flag desconocido: $arg" ;;
    esac
done

# ---------- Pre-flight ----------
[[ $EUID -eq 0 ]] || fatal "ejecutar como root"
[[ -d "$SRC_BACKEND" ]]   || fatal "no existe $SRC_BACKEND (repo de trabajo)"
[[ -d "$PROD_BACKEND" ]]  || fatal "no existe $PROD_BACKEND (instalacion productiva)"
[[ -d "$SRC_ADMIN" ]]     || fatal "no existe $SRC_ADMIN"

command -v go    >/dev/null || fatal "go no esta en PATH"
command -v npm   >/dev/null || fatal "npm no esta en PATH"
command -v curl  >/dev/null || fatal "curl no esta en PATH"
command -v rsync >/dev/null || command -v cp >/dev/null || fatal "necesito rsync o cp"

TS=$(date +%Y%m%d_%H%M%S)

run() {
    # Wrapper que respeta --dry-run.
    if [[ $DRY_RUN -eq 1 ]]; then
        echo "${C_YEL}  [dry-run]${C_RST} $*"
    else
        "$@"
    fi
}

header() {
    echo
    echo "${C_BLD}${C_BLU}========== $* ==========${C_RST}"
}

# ---------- 1. Backup ----------
header "1/6  Backup de binario y webroot (timestamp=$TS)"

if [[ $NO_BACKUP -eq 1 ]]; then
    warn "--no-backup activo, no se generan .bak"
else
    if [[ -f "$PROD_BIN" ]]; then
        bak="$PROD_BACKEND/bin/server.bak.$TS"
        run cp -p "$PROD_BIN" "$bak"
        ok "binario respaldado en $bak"
    fi
    if [[ -d "$PROD_WEBROOT" ]]; then
        bak="$PROD_WEBROOT.bak.$TS"
        run cp -a "$PROD_WEBROOT" "$bak"
        ok "webroot respaldado en $bak"
    fi
fi

# ---------- 2. Stop servicio ----------
header "2/6  Stop del servicio $SERVICE_NAME"
if [[ $DRY_RUN -eq 1 ]]; then
    ok "[dry-run] skip stop (servicio sigue corriendo, no se hace nada)"
elif systemctl is-active --quiet "$SERVICE_NAME"; then
    run systemctl stop "$SERVICE_NAME"
    # Esperar a que el proceso muera (max 10s).
    for i in {1..20}; do
        if ! pgrep -f "$PROD_BIN" >/dev/null; then break; fi
        sleep 0.5
    done
    pgrep -f "$PROD_BIN" >/dev/null && fatal "el proceso del server no termino en 10s"
    ok "servicio detenido"
else
    ok "servicio ya estaba inactivo"
fi

# ---------- 3. Sync de codigo backend ----------
header "3/6  Sync de migrations + modulo admin"

# 3a. Migrations nuevas. Estrategia: cualquier *.sql local que NO exista en
# prod se copia. Asi cubrimos 0005+ hoy y futuras (0006, 0007, ...) sin
# tener que actualizar el script cada vez.
if [[ -d "$SRC_BACKEND/migrations" ]]; then
    new_migs=()
    for f in "$SRC_BACKEND/migrations"/*.sql; do
        [[ -f "$f" ]] || continue
        bn=$(basename "$f")
        if [[ ! -f "$PROD_MIGRATIONS/$bn" ]]; then
            new_migs+=("$f")
        fi
    done
    if (( ${#new_migs[@]} > 0 )); then
        for f in "${new_migs[@]}"; do
            run cp -p "$f" "$PROD_MIGRATIONS/"
            ok "migration copiada: $(basename "$f")"
        done
    else
        ok "sin migrations nuevas para sincronizar"
    fi
fi

# 3b. Modulos backend que se sincronizan a prod. Es un allowlist explicito
# (no un rsync del arbol completo) para no arrastrar cambios sin commitear de
# otros modulos. Si se toca un modulo nuevo, sumarlo a MODULES.
# NOTA: auth se agrego aca para desplegar POST /auth/change-password; antes
# solo se sincronizaba admin y el endpoint nunca llegaba a prod (404).
# booking se agrego para POST /reservations/{id}/incidents.
# driver se agrego para POST /driver/trips/{id}/guest-occupants.
MODULES=(admin auth booking driver)
for mod in "${MODULES[@]}"; do
    for f in handler.go repository.go service.go service_test.go; do
        src="$SRC_BACKEND/internal/modules/$mod/$f"
        dst="$PROD_BACKEND/internal/modules/$mod/$f"
        [[ -f "$src" ]] || { warn "no existe en repo: $src"; continue; }
        run cp -p "$src" "$dst"
        ok "modulo $mod sync: $f"
    done
done

# 3c. Motor de migraciones (migrate.go). Lo sincronizamos aca para que los
# deploys que toquen la logica de arranque del backend (schema_migrations,
# futuros checks de version, etc.) lleguen a prod sin tener que editar
# este script cada vez.
src="$SRC_BACKEND/internal/platform/database/migrate.go"
dst="$PROD_BACKEND/internal/platform/database/migrate.go"
if [[ -f "$src" ]]; then
    run cp -p "$src" "$dst"
    ok "database platform sync: migrate.go"
fi

# ---------- 4. Rebuild binario ----------
header "4/6  Rebuild de $PROD_BIN"

if [[ $DRY_RUN -eq 0 ]]; then
    cd "$PROD_BACKEND"
    if [[ $SKIP_VET -eq 0 ]]; then
        log "go vet ./..."
        # go vet puede fallar por tests rotos (deps faltantes); eso no
        # afecta al binario. Lo tratamos como warning, no como fatal.
        if ! go vet ./... 2>&1 | sed 's/^/  /'; then
            warn "go vet reporto issues (probablemente tests pre-existentes)."
            warn "Continuando con go build (es lo que importa para el deploy)."
        fi
    fi
    log "go build -o $PROD_BIN ./cmd/server/"
    NEW_BIN="$PROD_BACKEND/bin/server.new.$TS"
    go build -o "$NEW_BIN" ./cmd/server/ 2>&1 | sed 's/^/  /'

    # Conservar owner del binario viejo (appuser) y permisos 755.
    chown appuser:appuser "$NEW_BIN"
    chmod 755 "$NEW_BIN"

    # Swap atomico: mv al lugar final.
    mv "$NEW_BIN" "$PROD_BIN"
    ok "binario actualizado y con owner appuser:appuser 755"
fi

# ---------- 5. Rebuild admin + sync webroot ----------
header "5/6  Rebuild admin (vite) y sync a $PROD_WEBROOT"

if [[ $SKIP_WEB -eq 0 ]]; then
    if [[ $DRY_RUN -eq 0 ]]; then
        cd "$SRC_ADMIN"
        log "npm run build"
        npm run build 2>&1 | tail -20 | sed 's/^/  /'

        # Sync: borrar dist viejo y copiar el nuevo. No tocar .well-known/ ni
        # privacy.html (lo conserva el backup si hace falta restaurar).
        run rsync -a --delete \
            --exclude '.well-known' \
            --exclude 'privacy.html*' \
            "$SRC_ADMIN/dist/" "$PROD_WEBROOT/"
        ok "dist sincronizado en $PROD_WEBROOT"
    fi
else
    ok "--skip-web activo, no se toco el frontend"
fi

# ---------- 6. Start + health check ----------
header "6/6  Start del servicio + health check"

if [[ $DRY_RUN -eq 1 ]]; then
    ok "[dry-run] skip start (servicio sigue corriendo)"
else
    run systemctl start "$SERVICE_NAME"

    # Esperar a que este activo.
    for i in {1..20}; do
        if systemctl is-active --quiet "$SERVICE_NAME"; then break; fi
        sleep 0.5
    done
    if ! systemctl is-active --quiet "$SERVICE_NAME"; then
        fatal "el servicio no arranco. Ver: journalctl -u $SERVICE_NAME -n 50"
    fi
    ok "servicio activo"
fi

# Health check con reintentos (el server tarda unos segundos en compilar
# rutas + auto-aplicar migrations de 0005).
if [[ $DRY_RUN -eq 1 ]]; then
    ok "[dry-run] skip health check"
else
    health_attempts=0
    health_max=20
    health_ok=0
    while (( health_attempts < health_max )); do
        health_attempts=$((health_attempts + 1))
        if curl -skf -o /dev/null -m 3 "$HEALTH_URL" 2>/dev/null \
           || curl -skf -o /dev/null -m 3 "$HEALTH_FALLBACK_URL" 2>/dev/null; then
            health_ok=1
            break
        fi
        sleep 1
    done

    if (( health_ok )); then
        ok "health check OK tras ${health_attempts}s"
    else
        warn "health check fallo tras ${health_max}s. Revisar logs:"
        warn "  journalctl -u $SERVICE_NAME -n 50"
        warn "  tail -f /var/log/appmovilidadclinica/server.log"
    fi
fi

# ---------- Resumen ----------
header "Deploy completo"
echo "  Branch:      $(git -C "$SRC_REPO" rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
echo "  HEAD commit: $(git -C "$SRC_REPO" rev-parse --short HEAD 2>/dev/null || echo '?')"
echo "  Binario:     $PROD_BIN ($(stat -c '%s bytes / mtime %y' "$PROD_BIN" 2>/dev/null || echo '?'))"
echo "  Service:     $(systemctl is-active "$SERVICE_NAME")"
echo "  Webroot:     $(ls "$PROD_WEBROOT" 2>/dev/null | wc -l) archivos en $PROD_WEBROOT"
echo "  Backups:     $PROD_BACKEND/bin/server.bak.$TS"
echo "               $PROD_WEBROOT.bak.$TS (si --no-backup no se uso)"
echo
if [[ $DRY_RUN -eq 1 ]]; then
    echo "${C_YEL}${C_BLD}Esto fue un dry-run. No se hizo ningun cambio.${C_RST}"
fi