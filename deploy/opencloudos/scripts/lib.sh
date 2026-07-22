#!/usr/bin/env bash
set -Eeuo pipefail

die() {
  echo "错误：$*" >&2
  exit 1
}

require_root() {
  [[ ${EUID} -eq 0 ]] || die "此操作必须使用 root 执行"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

nginx_site_file() {
  if [[ -d /www/server/panel/vhost/nginx ]]; then
    echo /www/server/panel/vhost/nginx/stock-watch.conf
  elif [[ -d /etc/nginx/conf.d ]]; then
    echo /etc/nginx/conf.d/stock-watch.conf
  else
    die "无法识别 Nginx 站点配置目录"
  fi
}

reload_nginx() {
  nginx -t || return 1
  if [[ -d /www/server/nginx/conf ]]; then
    nginx -s reload || return 1
  else
    systemctl enable --now nginx || return 1
    systemctl reload nginx || return 1
  fi
}

validate_postgres_backup() {
  local backup_file=$1
  local pg_bin=${2:-/www/server/pgsql/bin}
  [[ -s ${backup_file} ]] || die "数据库备份不存在或为空：${backup_file}"
  [[ -s ${backup_file}.sha256 ]] || die "数据库备份缺少哈希文件：${backup_file}.sha256"
  sha256sum -c "${backup_file}.sha256" >/dev/null \
    || die "数据库备份哈希校验失败：${backup_file}"
  [[ -x ${pg_bin}/pg_restore ]] || die "缺少 PostgreSQL 恢复检查工具"
  "${pg_bin}/pg_restore" --list "${backup_file}" >/dev/null \
    || die "数据库备份格式校验失败：${backup_file}"
  "${pg_bin}/pg_restore" --file=/dev/null "${backup_file}" \
    || die "数据库备份完整解压校验失败：${backup_file}"
}

start_release_services() {
  systemctl restart stock-watch-akshare.service \
    && systemctl restart stock-watch-api.service \
    && wait_for_url http://127.0.0.1:8090/health 45 \
    && wait_for_url http://127.0.0.1:8080/actuator/health 60
}

atomic_release_link() {
  local target=$1
  local link=${2:-/opt/stock-watch/current}
  local next=${link}.next.$$
  [[ -d ${target} ]] || die "版本目录不存在：${target}"
  [[ ! -e ${next} && ! -L ${next} ]] || die "临时版本链接已存在：${next}"
  ln -s "${target}" "${next}"
  if mv --version >/dev/null 2>&1; then
    mv -Tf "${next}" "${link}"
  else
    mv -fh "${next}" "${link}"
  fi
}

verify_active_release() {
  local verify_script=$1
  start_release_services \
    && reload_nginx \
    && bash "${verify_script}"
}

rollback_release() {
  local previous=$1
  local link=${2:-/opt/stock-watch/current}
  [[ -n ${previous} ]] || return 1
  atomic_release_link "${previous}" "${link}" || return 1
  start_release_services || return 1
  reload_nginx || return 1
}

validate_secret_file() {
  local file=$1
  [[ -f ${file} ]] || die "缺少环境文件：${file}"
  local mode
  mode=$(stat -c '%a' "${file}")
  [[ ${mode} == "600" ]] || die "环境文件权限必须为 600：${file} 当前 ${mode}"
  if grep -Eq '__SET_|__SAME_AS_|change-me-for-production|dev-only-secret' "${file}"; then
    die "环境文件仍包含示例值：${file}"
  fi
}

wait_for_url() {
  local url=$1
  local attempts=${2:-30}
  local index
  for ((index = 1; index <= attempts; index += 1)); do
    if curl --fail --silent --show-error --max-time 3 "${url}" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}
