#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEPLOY_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

DOMAIN=${DOMAIN:-yinxian.com.cn}
WWW_DOMAIN=${WWW_DOMAIN:-www.yinxian.com.cn}
PUBLIC_IP=${PUBLIC_IP:-211.159.158.165}
CERTBOT=${CERTBOT:-/opt/stock-watch/certbot/bin/certbot}
[[ ${DOMAIN} == "yinxian.com.cn" ]] || die "DOMAIN 与已审核目标不一致"
[[ ${WWW_DOMAIN} == "www.yinxian.com.cn" ]] || die "WWW_DOMAIN 与已审核目标不一致"
[[ ${PUBLIC_IP} == "211.159.158.165" ]] || die "PUBLIC_IP 与已审核目标不一致"
require_command nginx
require_command curl
[[ -x ${CERTBOT} ]] || die "找不到 Certbot：${CERTBOT}"
pgrep -x nginx >/dev/null || die "申请证书前 Nginx 必须运行"

for name in "${DOMAIN}" "${WWW_DOMAIN}"; do
  getent ahostsv4 "${name}" | awk '{print $1}' | grep -Fxq "${PUBLIC_IP}" \
    || die "${name} 尚未解析到 ${PUBLIC_IP}"
done

DOMAIN_SITE_FILE=$(nginx_domain_site_file)
BACKUP_FILE=""
if [[ -e ${DOMAIN_SITE_FILE} ]]; then
  BACKUP_FILE="${DOMAIN_SITE_FILE}.pre-domain-https.$(date -u +%Y%m%dT%H%M%SZ)"
  cp --preserve=all "${DOMAIN_SITE_FILE}" "${BACKUP_FILE}"
fi

restore_domain_config() {
  trap - ERR
  echo "域名 HTTPS 切换失败，恢复原 Nginx 配置" >&2
  if [[ -n ${BACKUP_FILE} && -f ${BACKUP_FILE} ]]; then
    cp --preserve=all "${BACKUP_FILE}" "${DOMAIN_SITE_FILE}"
  else
    rm -f "${DOMAIN_SITE_FILE}"
  fi
  reload_nginx || true
}
trap restore_domain_config ERR

install -d -o root -g root -m 0755 /var/www/letsencrypt
install -o root -g root -m 0600 \
  "${DEPLOY_DIR}/nginx/stock-watch-domain-http.conf" "${DOMAIN_SITE_FILE}"
reload_nginx

challenge="domain-preflight-$$"
challenge_dir=/var/www/letsencrypt/.well-known/acme-challenge
install -d -o root -g root -m 0755 "${challenge_dir}"
printf '%s\n' "${challenge}" >"${challenge_dir}/${challenge}"
trap 'rm -f "${challenge_dir}/${challenge}"; restore_domain_config' ERR
for name in "${DOMAIN}" "${WWW_DOMAIN}"; do
  response=$(curl --fail --silent --show-error --max-time 5 \
    --resolve "${name}:80:${PUBLIC_IP}" \
    "http://${name}/.well-known/acme-challenge/${challenge}")
  [[ ${response} == "${challenge}" ]] || die "${name} 的 ACME Webroot 预检失败"
done
rm -f "${challenge_dir}/${challenge}"
trap restore_domain_config ERR

"${CERTBOT}" certonly \
  --non-interactive \
  --agree-tos \
  --register-unsafely-without-email \
  --webroot \
  --webroot-path /var/www/letsencrypt \
  --cert-name "${DOMAIN}" \
  -d "${DOMAIN}" \
  -d "${WWW_DOMAIN}"

[[ -s /etc/letsencrypt/live/${DOMAIN}/fullchain.pem ]] \
  || die "域名证书链不存在"
[[ -s /etc/letsencrypt/live/${DOMAIN}/privkey.pem ]] \
  || die "域名证书私钥不存在"

install -o root -g root -m 0600 \
  "${DEPLOY_DIR}/nginx/stock-watch-domain-https.conf" "${DOMAIN_SITE_FILE}"
reload_nginx
systemctl enable --now stock-watch-cert-renew.timer
trap - ERR

echo "域名 HTTPS 已启用：https://${DOMAIN}"
[[ -n ${BACKUP_FILE} ]] && echo "原域名配置备份：${BACKUP_FILE}"
