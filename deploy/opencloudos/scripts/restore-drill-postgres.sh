#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

PG_BIN=${PG_BIN:-/www/server/pgsql/bin}
PG_PORT=${PG_PORT:-5433}
BACKUP_FILE=${BACKUP_FILE:?必须指定要演练的 BACKUP_FILE}
DRILL_DATABASE=${DRILL_DATABASE:-stock_watch_restore_drill_$(date -u +%Y%m%dT%H%M%SZ)}
CONFIRM_DROP_DATABASE=${CONFIRM_DROP_DATABASE:?必须显式确认可删除的演练数据库名}

[[ ${DRILL_DATABASE} =~ ^stock_watch_restore_drill_[0-9]{8}T[0-9]{6}Z$ ]] \
  || die "演练数据库名不符合受保护前缀和时间戳格式"
[[ ${CONFIRM_DROP_DATABASE} == "${DRILL_DATABASE}" ]] \
  || die "删除确认与演练数据库名不一致"
validate_postgres_backup "${BACKUP_FILE}" "${PG_BIN}"

as_postgres() {
  su -s /bin/sh postgres -c "$*"
}

database_exists() {
  as_postgres "${PG_BIN}/psql -p ${PG_PORT} -d postgres -At \
    -c \"select 1 from pg_database where datname = '${DRILL_DATABASE}'\""
}

cleanup() {
  if [[ $(database_exists) == "1" ]]; then
    as_postgres "${PG_BIN}/dropdb -p ${PG_PORT} '${DRILL_DATABASE}'"
  fi
}
trap cleanup EXIT

[[ -z $(database_exists) ]] || die "演练数据库已存在，拒绝覆盖"
as_postgres "${PG_BIN}/createdb -p ${PG_PORT} --template=template0 '${DRILL_DATABASE}'"
su -s /bin/sh postgres -c "${PG_BIN}/pg_restore -p ${PG_PORT} --exit-on-error \
  --dbname='${DRILL_DATABASE}'" <"${BACKUP_FILE}"

RESULT=$(as_postgres "${PG_BIN}/psql -p ${PG_PORT} -d '${DRILL_DATABASE}' -At \
  -c \"select 'tables|' || count(*) from pg_catalog.pg_tables \
      where schemaname not in ('pg_catalog','information_schema'); \
      select 'flyway|' || count(*) from flyway_schema_history where success\"")
printf '%s\n' "${RESULT}"
printf '%s\n' "${RESULT}" | grep -Eq '^tables\|[1-9][0-9]*$'
printf '%s\n' "${RESULT}" | grep -Eq '^flyway\|[1-9][0-9]*$'

cleanup
trap - EXIT
echo "数据库完整恢复演练通过，临时数据库已删除：${DRILL_DATABASE}"
