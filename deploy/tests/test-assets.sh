#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
OPEN_CLOUD="${ROOT}/deploy/opencloudos"

for script in "${ROOT}/deploy/build-release.sh" "${OPEN_CLOUD}"/scripts/*.sh; do
  bash -n "${script}"
done
bash "${ROOT}/deploy/tests/test-release-guards.sh"

if rg -n 'rm[[:space:]]+-rf|DROP[[:space:]]+(DATABASE|TABLE)|git[[:space:]]+reset[[:space:]]+--hard|force-push' \
  "${ROOT}/deploy/opencloudos" "${ROOT}/deploy/build-release.sh"; then
  echo "部署资产包含禁止的破坏性命令" >&2
  exit 1
fi

rg -q '127\.0\.0\.1:8080' "${OPEN_CLOUD}/nginx/stock-watch-http.conf"
rg -q -- '--host 127\.0\.0\.1 --port 8090' \
  "${OPEN_CLOUD}/systemd/stock-watch-akshare.service"
rg -q 'BIND_ADDRESS=127\.0\.0\.1' "${OPEN_CLOUD}/env/api.env.example"
rg -q 'REDIS_HOST=127\.0\.0\.1' "${OPEN_CLOUD}/env/api.env.example"
rg -q 'REDIS_PASSWORD=__SET_ON_SERVER__' "${OPEN_CLOUD}/env/api.env.example"
rg -q 'Requires=redis.service' "${OPEN_CLOUD}/systemd/stock-watch-api.service"
rg -q '6379' "${OPEN_CLOUD}/scripts/verify.sh"
rg -q 'proxy_buffering off' "${OPEN_CLOUD}/nginx/stock-watch-https.conf"
rg -q 'preferred-profile shortlived' "${OPEN_CLOUD}/scripts/issue-ip-certificate.sh"
rg -q 'ip-address' "${OPEN_CLOUD}/scripts/issue-ip-certificate.sh"
rg -q -- '-Fc --file' "${OPEN_CLOUD}/scripts/backup-postgres.sh"
rg -q 'pg_restore" --list' "${OPEN_CLOUD}/scripts/backup-postgres.sh"
rg -q '/www/server/pgsql/bin' "${OPEN_CLOUD}/scripts/backup-postgres.sh"
rg -q '/www/server/panel/vhost/nginx' "${OPEN_CLOUD}/scripts/lib.sh"
rg -q '/www/server/nginx/conf/stock-watch-proxy.conf' \
  "${OPEN_CLOUD}/nginx/stock-watch-http.conf"
if rg -q 'systemctl enable --now firewalld' "${OPEN_CLOUD}/scripts/install-host.sh"; then
  echo "安装脚本不应擅自启用已停止的 firewalld" >&2
  exit 1
fi
rg -q 'BACKUP_ID' "${OPEN_CLOUD}/scripts/deploy-release.sh"
rg -q 'NoNewPrivileges=true' "${OPEN_CLOUD}/systemd/stock-watch-api.service"
rg -q 'OnCalendar=.*03:20:00' \
  "${OPEN_CLOUD}/systemd/stock-watch-db-backup.timer"
rg -q 'ReadWritePaths=/var/backups/stock-watch' \
  "${OPEN_CLOUD}/systemd/stock-watch-db-backup.service"
rg -q 'verify-e2e.sh' "${OPEN_CLOUD}/scripts/verify.sh"
rg -q "AKSHARE_CONFIGURED" "${OPEN_CLOUD}/scripts/verify-e2e.sh"
rg -q "CURRENT_MARKET_CONFIG" "${OPEN_CLOUD}/scripts/verify-e2e.sh"
rg -F -q "q['source'].startswith('AKSHARE_')" "${OPEN_CLOUD}/scripts/verify-e2e.sh"
rg -q 'validate_postgres_backup' "${OPEN_CLOUD}/scripts/deploy-release.sh"
rg -q 'start_release_services' "${OPEN_CLOUD}/scripts/deploy-release.sh"
rg -q 'sha256sum -c' "${OPEN_CLOUD}/scripts/deploy-release.sh"
rg -q 'current/venv/bin/python' \
  "${OPEN_CLOUD}/systemd/stock-watch-akshare.service"
rg -q 'trap restore_http_config ERR' \
  "${OPEN_CLOUD}/scripts/issue-ip-certificate.sh"
rg -q 'CONFIRM_DROP_DATABASE' \
  "${OPEN_CLOUD}/scripts/restore-drill-postgres.sh"
rg -q 'stock_watch_restore_drill_' \
  "${OPEN_CLOUD}/scripts/restore-drill-postgres.sh"

TEMP_FILE=$(mktemp)
trap 'rm -f "${TEMP_FILE}"' EXIT
cp "${OPEN_CLOUD}/env/api.env.example" "${TEMP_FILE}"
chmod 600 "${TEMP_FILE}"
if bash -c "source '${OPEN_CLOUD}/scripts/lib.sh'; validate_secret_file '${TEMP_FILE}'" \
  >/dev/null 2>&1; then
  echo "示例密钥未被配置门禁拒绝" >&2
  exit 1
fi

echo "部署资产静态测试通过"
