#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

PG_BIN=${PG_BIN:-/www/server/pgsql/bin}
PG_HOST=${PG_HOST:-127.0.0.1}
PG_PORT=${PG_PORT:-5433}
DB_NAME=${DB_NAME:-trading}
DB_USERNAME=${DB_USERNAME:-trading}
DB_PASSWORD=${DB_PASSWORD:?必须通过受保护环境文件提供 DB_PASSWORD}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/stock-watch}
BACKUP_ID=${BACKUP_ID:-$(date -u +%Y%m%dT%H%M%SZ)}
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}-${BACKUP_ID}.dump"

[[ ${PG_HOST} == "127.0.0.1" || ${PG_HOST} == "::1" ]] \
  || die "数据库备份只允许连接回环地址"
[[ ${PG_PORT} =~ ^[0-9]+$ ]] || die "PostgreSQL 端口格式无效"
for command in pg_isready pg_dump pg_restore; do
  [[ -x ${PG_BIN}/${command} ]] || die "缺少 PostgreSQL 工具：${PG_BIN}/${command}"
done
install -d -o root -g root -m 0700 "${BACKUP_DIR}"

PGPASSWORD="${DB_PASSWORD}" "${PG_BIN}/pg_isready" \
  -h "${PG_HOST}" -p "${PG_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" >/dev/null
umask 077
PGPASSWORD="${DB_PASSWORD}" "${PG_BIN}/pg_dump" \
  -h "${PG_HOST}" -p "${PG_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" \
  -Fc --file="${BACKUP_FILE}"
[[ -s ${BACKUP_FILE} ]] || die "数据库备份为空"
"${PG_BIN}/pg_restore" --list "${BACKUP_FILE}" >/dev/null
sha256sum "${BACKUP_FILE}" >"${BACKUP_FILE}.sha256"
validate_postgres_backup "${BACKUP_FILE}" "${PG_BIN}"

echo "BACKUP_ID=${BACKUP_ID}"
echo "BACKUP_FILE=${BACKUP_FILE}"
