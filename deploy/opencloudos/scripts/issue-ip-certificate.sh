#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEPLOY_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

PUBLIC_IP=${PUBLIC_IP:-211.159.158.165}
[[ ${PUBLIC_IP} == "211.159.158.165" ]] || die "PUBLIC_IP 与已审核目标不一致"
require_command nginx
pgrep -x nginx >/dev/null || die "申请证书前 Nginx HTTP 必须运行"

python3 -m venv /opt/stock-watch/certbot
/opt/stock-watch/certbot/bin/python -m pip install --upgrade pip
/opt/stock-watch/certbot/bin/python -m pip install 'certbot==5.4.0'

/opt/stock-watch/certbot/bin/certbot certonly \
  --non-interactive \
  --agree-tos \
  --register-unsafely-without-email \
  --preferred-profile shortlived \
  --webroot \
  --webroot-path /var/www/letsencrypt \
  --ip-address "${PUBLIC_IP}"

NGINX_SITE_FILE=$(nginx_site_file)
[[ -f ${NGINX_SITE_FILE} ]] || die "找不到现有 HTTP 站点配置：${NGINX_SITE_FILE}"
BACKUP_FILE="${NGINX_SITE_FILE}.pre-https.$(date -u +%Y%m%dT%H%M%SZ)"
cp --preserve=all "${NGINX_SITE_FILE}" "${BACKUP_FILE}"
restore_http_config() {
  echo "HTTPS 切换失败，恢复原 Nginx 配置：${BACKUP_FILE}" >&2
  cp --preserve=all "${BACKUP_FILE}" "${NGINX_SITE_FILE}"
  reload_nginx || true
}
trap restore_http_config ERR
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/nginx/stock-watch-https.conf" "${NGINX_SITE_FILE}"
reload_nginx
systemctl enable --now stock-watch-cert-renew.timer
/opt/stock-watch/certbot/bin/certbot renew --dry-run
trap - ERR
echo "IP 地址 HTTPS 已启用：https://${PUBLIC_IP}"
