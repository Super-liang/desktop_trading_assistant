#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"
source deploy/desktop/release-env.sh

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "错误：macOS 安装包必须在 macOS 上构建" >&2
  exit 1
fi

TARGET="aarch64-apple-darwin"
PLATFORM_DIR="${RELEASE_ROOT}/macos-arm64"
BUNDLE_DIR="apps/desktop/src-tauri/target/${TARGET}/release/bundle"

echo "开始构建股票盯盘助手 ${RELEASE_VERSION}（macOS arm64）"
echo "云 API：${RELEASE_API_URL}"

VITE_API_URL="${RELEASE_API_URL}" \
APPLE_SIGNING_IDENTITY="${APPLE_SIGNING_IDENTITY:--}" \
  npm --workspace apps/desktop run tauri -- build \
    --target "${TARGET}" \
    --bundles app,dmg

deploy/desktop/verify-frontend-api.sh apps/desktop/dist "${RELEASE_API_URL}"

APP_PATH="$(find "${BUNDLE_DIR}/macos" -maxdepth 1 -type d -name '*.app' -print -quit)"
DMG_PATH="$(find "${BUNDLE_DIR}/dmg" -maxdepth 1 -type f -name '*.dmg' -print -quit)"
if [[ -z "${APP_PATH}" || -z "${DMG_PATH}" ]]; then
  echo "错误：未找到 Tauri 生成的 APP 或 DMG" >&2
  exit 1
fi

mkdir -p "${PLATFORM_DIR}"
DMG_OUTPUT="${PLATFORM_DIR}/StockTradingAssistant_${RELEASE_VERSION}_macos-arm64.dmg"
ZIP_OUTPUT="${PLATFORM_DIR}/StockTradingAssistant_${RELEASE_VERSION}_macos-arm64.app.zip"
cp "${DMG_PATH}" "${DMG_OUTPUT}"
ditto -c -k --sequesterRsrc --keepParent "${APP_PATH}" "${ZIP_OUTPUT}"

hdiutil verify "${DMG_OUTPUT}"
codesign --verify --deep --strict "${APP_PATH}"
EXECUTABLE_PATH="$(find "${APP_PATH}/Contents/MacOS" -maxdepth 1 -type f -perm -111 -print -quit)"
if [[ -z "${EXECUTABLE_PATH}" ]]; then
  echo "错误：APP 中未找到可执行文件" >&2
  exit 1
fi
if ! file "${EXECUTABLE_PATH}" | grep -q 'arm64'; then
  echo "错误：APP 可执行文件不是 arm64 架构" >&2
  exit 1
fi
file "${EXECUTABLE_PATH}"

(
  cd "${PLATFORM_DIR}"
  shasum -a 256 "$(basename "${DMG_OUTPUT}")" "$(basename "${ZIP_OUTPUT}")" > SHA256SUMS.txt
)

echo "macOS 安装包已生成：${PLATFORM_DIR}"
