#!/usr/bin/env bash
set -euo pipefail

DIST_DIR="${1:-apps/desktop/dist}"
EXPECTED_API="${2:-${VITE_API_URL:-}}"

if [[ -z "${EXPECTED_API}" ]]; then
  echo "用法：$0 <dist目录> <期望的HTTPS API地址>" >&2
  exit 2
fi
if [[ ! "${EXPECTED_API}" =~ ^https:// ]]; then
  echo "错误：期望 API 不是 HTTPS：${EXPECTED_API}" >&2
  exit 2
fi
if [[ ! -d "${DIST_DIR}" ]]; then
  echo "错误：前端产物目录不存在：${DIST_DIR}" >&2
  exit 1
fi

if ! grep -R -F -q --binary-files=without-match "${EXPECTED_API}" "${DIST_DIR}"; then
  echo "错误：前端产物未包含期望的 API 地址：${EXPECTED_API}" >&2
  exit 1
fi

if grep -R -E -q --binary-files=without-match 'https?://(localhost|127\.0\.0\.1):8080' "${DIST_DIR}"; then
  echo "错误：发布前端仍包含 localhost API 回退地址" >&2
  exit 1
fi

echo "前端 API 校验通过：${EXPECTED_API}"
