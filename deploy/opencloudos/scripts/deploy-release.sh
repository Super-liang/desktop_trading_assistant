#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

ARCHIVE=${1:?用法: deploy-release.sh <release.tar.gz> <backup-id>}
BACKUP_ID=${2:?必须提供已验证的数据库备份标识}
[[ -f ${ARCHIVE} ]] || die "发布包不存在：${ARCHIVE}"
CHECKSUM_FILE=${ARCHIVE}.sha256
[[ -s ${CHECKSUM_FILE} ]] || die "发布包缺少 SHA-256 文件：${CHECKSUM_FILE}"
(
  cd "$(dirname "${ARCHIVE}")"
  sha256sum -c "$(basename "${CHECKSUM_FILE}")" >/dev/null
) || die "发布包 SHA-256 校验失败"
[[ ${BACKUP_ID} =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "备份标识格式无效"

validate_secret_file /etc/stock-watch/api.env
validate_secret_file /etc/stock-watch/akshare.env
source /etc/stock-watch/api.env
source /etc/stock-watch/akshare.env
systemctl is-active --quiet redis.service || die "Redis 服务未启动"
REDISCLI_AUTH="${REDIS_PASSWORD}" redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ping \
  | grep -q '^PONG$' || die "Redis 鉴权或连通性检查失败"
[[ ${QUOTE_HTTP_API_KEY} == "${AKSHARE_API_KEY}" ]] \
  || die "API 与 AKShare 共享密钥不一致"
BACKUP_FILE=/var/backups/stock-watch/trading-"${BACKUP_ID}".dump
validate_postgres_backup "${BACKUP_FILE}" /www/server/pgsql/bin

RELEASE_ID=$(basename "${ARCHIVE}" .tar.gz)
[[ ${RELEASE_ID} =~ ^[a-zA-Z0-9_.-]+$ ]] || die "发布版本标识格式无效"
RELEASE_DIR=/opt/stock-watch/releases/${RELEASE_ID}
[[ ! -e ${RELEASE_DIR} ]] || die "版本目录已存在：${RELEASE_DIR}"

install -d -o root -g root -m 0755 "${RELEASE_DIR}"
tar -xzf "${ARCHIVE}" -C "${RELEASE_DIR}"
[[ -f ${RELEASE_DIR}/api/app.jar ]] || die "发布包缺少 API JAR"
[[ -f ${RELEASE_DIR}/web/index.html ]] || die "发布包缺少 Web"
[[ -f ${RELEASE_DIR}/akshare/requirements.txt ]] || die "发布包缺少 AKShare"
python3 -m venv "${RELEASE_DIR}/venv"
"${RELEASE_DIR}/venv/bin/python" -m pip install --upgrade pip
"${RELEASE_DIR}/venv/bin/python" -m pip install \
  -r "${RELEASE_DIR}/akshare/requirements.txt"
chown -R root:root "${RELEASE_DIR}"
chmod -R go-w "${RELEASE_DIR}"
if [[ $(getenforce 2>/dev/null || true) != "Disabled" ]] && command -v restorecon >/dev/null 2>&1; then
  restorecon -Rv "${RELEASE_DIR}" >/dev/null
fi

PREVIOUS=
if [[ -L /opt/stock-watch/current ]]; then
  PREVIOUS=$(readlink -f /opt/stock-watch/current)
  [[ ${PREVIOUS} == /opt/stock-watch/releases/* && -d ${PREVIOUS} ]] \
    || die "当前版本链接不指向合法 release：${PREVIOUS}"
fi
atomic_release_link "${RELEASE_DIR}"
systemctl enable stock-watch-akshare.service stock-watch-api.service
systemctl enable --now stock-watch-db-backup.timer

VERIFY_SCRIPT=${SCRIPT_DIR}/verify.sh
if [[ -f /etc/letsencrypt/live/211.159.158.165/fullchain.pem ]]; then
  if ! verify_active_release "${VERIFY_SCRIPT}"; then
    echo "新版本完整验收失败，开始应用版本回滚" >&2
    if ! rollback_release "${PREVIOUS}"; then
      die "新版本失败且旧版本未恢复，请依据备份 ${BACKUP_ID} 检查服务"
    fi
    die "发布失败，已恢复版本：${PREVIOUS}"
  fi
else
  if ! start_release_services || ! reload_nginx; then
    echo "首次 HTTP 引导发布失败，开始应用版本回滚" >&2
    if ! rollback_release "${PREVIOUS}"; then
      die "首次发布失败且没有可恢复的旧版本"
    fi
    die "发布失败，已恢复版本：${PREVIOUS}"
  fi
  echo "警告：当前为无证书 HTTP 引导发布，签发证书后必须执行 verify.sh。" >&2
fi

if ! curl --fail --silent --max-time 3 \
  http://127.0.0.1:8080/actuator/health >/dev/null; then
  echo "新版本健康检查失败，开始应用版本回滚" >&2
  rollback_release "${PREVIOUS}" \
    || die "旧版本也未恢复，请检查 systemd 日志"
  die "发布失败；数据库迁移如已执行，请依据备份 ${BACKUP_ID} 人工评估"
fi

echo "发布成功：${RELEASE_ID}，数据库备份：${BACKUP_ID}"
