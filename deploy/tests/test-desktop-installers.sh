#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

printf '%s\n' 'const api="https://211.159.158.165";' > "${TMP_DIR}/valid.js"
deploy/desktop/verify-frontend-api.sh "${TMP_DIR}" "https://211.159.158.165"

printf '%s\n' 'const api="http://localhost:8080";' > "${TMP_DIR}/invalid.js"
if deploy/desktop/verify-frontend-api.sh "${TMP_DIR}" "https://211.159.158.165" >/dev/null 2>&1; then
  echo "错误：localhost 发布保护测试本应失败" >&2
  exit 1
fi
rm "${TMP_DIR}/invalid.js"

if VITE_API_URL="http://example.com" bash -c 'source deploy/desktop/release-env.sh' >/dev/null 2>&1; then
  echo "错误：HTTP 发布地址测试本应失败" >&2
  exit 1
fi
if RELEASE_VERSION="99.0.0" bash -c 'source deploy/desktop/release-env.sh' >/dev/null 2>&1; then
  echo "错误：错误版本覆盖测试本应失败" >&2
  exit 1
fi

grep -F -q 'windows-latest' .github/workflows/desktop-installers.yml
grep -F -q -- '--bundles nsis,msi' .github/workflows/desktop-installers.yml
grep -F -q 'APPLE_SIGNING_IDENTITY' .github/workflows/desktop-installers.yml
grep -F -q 'windows_subsystem = "windows"' apps/desktop/src-tauri/src/main.rs

echo "桌面安装包脚本定向测试通过"
