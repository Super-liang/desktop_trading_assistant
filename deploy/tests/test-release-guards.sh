#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
source "${ROOT}/deploy/opencloudos/scripts/lib.sh"
TEMP_DIR=$(mktemp -d)

cleanup() {
  find "${TEMP_DIR}" -type f -delete
  find "${TEMP_DIR}" -type l -delete
  find "${TEMP_DIR}" -depth -type d -exec rmdir {} \;
}
trap cleanup EXIT

expect_failure() {
  if ("$@" >/dev/null 2>&1); then
    echo "预期命令失败但实际成功：$*" >&2
    exit 1
  fi
}

install -d "${TEMP_DIR}/pg-ok" "${TEMP_DIR}/pg-fail" "${TEMP_DIR}/path"
ln -s /usr/bin/true "${TEMP_DIR}/pg-ok/pg_restore"
ln -s /usr/bin/false "${TEMP_DIR}/pg-fail/pg_restore"
printf 'VALID BACKUP\n' >"${TEMP_DIR}/valid.dump"
sha256sum "${TEMP_DIR}/valid.dump" >"${TEMP_DIR}/valid.dump.sha256"

validate_postgres_backup "${TEMP_DIR}/valid.dump" "${TEMP_DIR}/pg-ok"
expect_failure validate_postgres_backup "${TEMP_DIR}/missing.dump" "${TEMP_DIR}/pg-ok"
printf '' >"${TEMP_DIR}/empty.dump"
expect_failure validate_postgres_backup "${TEMP_DIR}/empty.dump" "${TEMP_DIR}/pg-ok"
printf 'INVALID\n' >"${TEMP_DIR}/invalid.dump"
printf '%064d  %s\n' 0 "${TEMP_DIR}/invalid.dump" >"${TEMP_DIR}/invalid.dump.sha256"
expect_failure validate_postgres_backup "${TEMP_DIR}/invalid.dump" "${TEMP_DIR}/pg-ok"
cp "${TEMP_DIR}/valid.dump" "${TEMP_DIR}/restore-fail.dump"
sha256sum "${TEMP_DIR}/restore-fail.dump" >"${TEMP_DIR}/restore-fail.dump.sha256"
expect_failure validate_postgres_backup \
  "${TEMP_DIR}/restore-fail.dump" "${TEMP_DIR}/pg-fail"

printf '#!/usr/bin/env bash\nexit 1\n' >"${TEMP_DIR}/path/systemctl"
chmod +x "${TEMP_DIR}/path/systemctl"
if PATH="${TEMP_DIR}/path:${PATH}" start_release_services; then
  echo "systemctl restart 失败时服务启动门禁未返回失败" >&2
  exit 1
fi

install -d "${TEMP_DIR}/releases/old" "${TEMP_DIR}/releases/new"
ln -s "${TEMP_DIR}/releases/new" "${TEMP_DIR}/current"
SERVICE_CHECKS=0
RELOAD_CHECKS=0
start_release_services() {
  SERVICE_CHECKS=$((SERVICE_CHECKS + 1))
  [[ $(readlink "${TEMP_DIR}/current") == "${TEMP_DIR}/releases/old" ]]
}
reload_nginx() {
  RELOAD_CHECKS=$((RELOAD_CHECKS + 1))
  return 0
}
printf '#!/usr/bin/env bash\nexit 1\n' >"${TEMP_DIR}/verify-fail.sh"
chmod +x "${TEMP_DIR}/verify-fail.sh"
if verify_active_release "${TEMP_DIR}/verify-fail.sh"; then
  echo "新版本完整验收失败时门禁未返回失败" >&2
  exit 1
fi
rollback_release "${TEMP_DIR}/releases/old" "${TEMP_DIR}/current"
[[ $(readlink "${TEMP_DIR}/current") == "${TEMP_DIR}/releases/old" ]]
[[ ${SERVICE_CHECKS} -eq 2 && ${RELOAD_CHECKS} -eq 1 ]]

echo "发布失败路径测试通过"
