#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEPLOY_DIR=$(cd "${SCRIPT_DIR}/.." && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

dnf install -y java-17-openjdk-headless python3 python3-pip curl tar redis

if ! id stockwatch >/dev/null 2>&1; then
  useradd --system --home-dir /opt/stock-watch --shell /sbin/nologin stockwatch
fi

install -d -o root -g root -m 0755 /opt/stock-watch/releases
install -d -o root -g root -m 0700 /etc/stock-watch /var/backups/stock-watch
install -d -o root -g root -m 0755 /usr/local/libexec/stock-watch
install -d -o root -g root -m 0755 /var/www/letsencrypt

install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/systemd/stock-watch-api.service" /etc/systemd/system/
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/systemd/stock-watch-akshare.service" /etc/systemd/system/
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/systemd/stock-watch-cert-renew.service" /etc/systemd/system/
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/systemd/stock-watch-cert-renew.timer" /etc/systemd/system/
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/systemd/stock-watch-db-backup.service" /etc/systemd/system/
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/systemd/stock-watch-db-backup.timer" /etc/systemd/system/
install -o root -g root -m 0755 \
  "${SCRIPT_DIR}/backup-postgres.sh" /usr/local/libexec/stock-watch/
install -o root -g root -m 0644 \
  "${SCRIPT_DIR}/lib.sh" /usr/local/libexec/stock-watch/
if [[ -d /www/server/nginx/conf ]]; then
  NGINX_CONF_DIR=/www/server/nginx/conf
elif [[ -d /etc/nginx ]]; then
  NGINX_CONF_DIR=/etc/nginx
else
  die "无法识别 Nginx 配置目录"
fi
install -o root -g root -m 0644 \
  "${DEPLOY_DIR}/nginx/stock-watch-proxy.conf" "${NGINX_CONF_DIR}/"

NGINX_SITE_FILE=$(nginx_site_file)
if [[ -e ${NGINX_SITE_FILE} ]]; then
  cmp --silent "${DEPLOY_DIR}/nginx/stock-watch-http.conf" "${NGINX_SITE_FILE}" \
    || die "Nginx 站点配置已存在且内容不同，拒绝覆盖：${NGINX_SITE_FILE}"
else
  install -o root -g root -m 0600 \
    "${DEPLOY_DIR}/nginx/stock-watch-http.conf" "${NGINX_SITE_FILE}"
fi

if [[ ! -e /etc/stock-watch/api.env ]]; then
  install -o root -g root -m 0600 \
    "${DEPLOY_DIR}/env/api.env.example" /etc/stock-watch/api.env.example
fi
if [[ ! -e /etc/stock-watch/akshare.env ]]; then
  install -o root -g root -m 0600 \
    "${DEPLOY_DIR}/env/akshare.env.example" /etc/stock-watch/akshare.env.example
fi

if [[ $(getenforce 2>/dev/null || true) == "Disabled" ]]; then
  echo "警告：SELinux 在部署前已关闭，本脚本不会自动开启；请安排独立加固窗口。" >&2
else
  require_command semanage
  semanage fcontext -a -t httpd_sys_content_t '/opt/stock-watch/releases(/.*)?' 2>/dev/null \
    || semanage fcontext -m -t httpd_sys_content_t '/opt/stock-watch/releases(/.*)?'
  restorecon -Rv /opt/stock-watch/releases >/dev/null
  setsebool -P httpd_can_network_connect 1
fi

if ! systemctl is-active --quiet firewalld; then
  echo "警告：firewalld 在部署前未运行，本脚本不会自动启用；必须通过 PostgreSQL 回环监听和云安全组关闭公网 5433。" >&2
fi

systemctl daemon-reload
nginx -t
echo "主机基础安装完成，Nginx 站点文件：${NGINX_SITE_FILE}。请先加固并启动 Redis，再配置 /etc/stock-watch/*.env 后发布。"
