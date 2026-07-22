#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root
PUBLIC_IP=${PUBLIC_IP:-211.159.158.165}

systemctl is-active --quiet stock-watch-akshare.service
systemctl is-active --quiet stock-watch-api.service
systemctl is-active --quiet redis.service
pgrep -x nginx >/dev/null
curl --fail --silent --show-error http://127.0.0.1:8090/health | grep -q '"status":"UP"'
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
[[ $(curl --silent --output /dev/null --write-out '%{http_code}' "http://${PUBLIC_IP}/") == "308" ]]
curl --fail --silent --show-error "https://${PUBLIC_IP}/" | grep -q '<title>股票盯盘助手</title>'
curl --silent --show-error "https://${PUBLIC_IP}/api/v1/quotes/providers" \
  -o /dev/null -w '%{http_code}' | grep -Eq '^(401|403)$'

HEADERS=$(curl --silent --show-error --dump-header - --output /dev/null "https://${PUBLIC_IP}/")
for header in strict-transport-security content-security-policy \
  x-content-type-options x-frame-options referrer-policy permissions-policy; do
  printf '%s' "${HEADERS}" | grep -Eiq "^${header}:"
done

if ss -lnt | grep -E '0\.0\.0\.0:(5433|6379|8080|8090)|\[::\]:(5433|6379|8080|8090)' >/dev/null; then
  die "发现内部服务监听公网地址"
fi

nginx -t
openssl x509 -checkend 86400 -noout \
  -in "/etc/letsencrypt/live/${PUBLIC_IP}/fullchain.pem"
openssl x509 -in "/etc/letsencrypt/live/${PUBLIC_IP}/fullchain.pem" \
  -noout -ext subjectAltName | grep -q "IP Address:${PUBLIC_IP}"
systemctl is-enabled --quiet stock-watch-cert-renew.timer
systemctl is-active --quiet stock-watch-cert-renew.timer
systemctl is-enabled --quiet stock-watch-db-backup.timer
systemctl is-active --quiet stock-watch-db-backup.timer

validate_secret_file /etc/stock-watch/api.env
source /etc/stock-watch/api.env
REDISCLI_AUTH="${REDIS_PASSWORD}" redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ping \
  | grep -q '^PONG$'
PG_BIN=/www/server/pgsql/bin
PGPASSWORD="${DB_PASSWORD}" "${PG_BIN}/pg_isready" \
  -h 127.0.0.1 -p 5433 -U "${DB_USERNAME}" -d trading >/dev/null
LATEST_BACKUP=$(find /var/backups/stock-watch -maxdepth 1 -type f \
  -name 'trading-*.dump' -printf '%T@ %p\n' | sort -nr | head -n1 | cut -d' ' -f2-)
[[ -n ${LATEST_BACKUP} ]]
validate_postgres_backup "${LATEST_BACKUP}" "${PG_BIN}"

LOGS=$(journalctl -u stock-watch-api.service -u stock-watch-akshare.service \
  --since "24 hours ago" --no-pager)
for secret in "${DB_PASSWORD}" "${REDIS_PASSWORD}" "${JWT_SECRET}" "${QUOTE_HTTP_API_KEY}"; do
  if printf '%s' "${LOGS}" | grep -Fq -- "${secret}"; then
    die "服务日志包含生产密钥"
  fi
done

bash "${SCRIPT_DIR}/verify-e2e.sh"
echo "云端完整验收通过。"
