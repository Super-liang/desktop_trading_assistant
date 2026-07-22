#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"
source deploy/desktop/release-env.sh

# Homebrew 的 LLVM 默认 keg-only，需要显式加入 PATH 才能找到 llvm-rc。
if command -v brew >/dev/null 2>&1 && [[ -d "$(brew --prefix llvm 2>/dev/null)/bin" ]]; then
  export PATH="$(brew --prefix llvm)/bin:${PATH}"
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "错误：此脚本用于在 Mac 上交叉构建 Windows NSIS；Windows 原生构建请使用 desktop-installers workflow" >&2
  exit 1
fi

for command_name in cargo-xwin makensis llvm-rc; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "错误：缺少 ${command_name}。请先安装 cargo-xwin、Homebrew llvm 和 nsis。" >&2
    exit 1
  fi
done

TARGET="x86_64-pc-windows-msvc"
PLATFORM_DIR="${RELEASE_ROOT}/windows-x64-cross"
BUNDLE_DIR="apps/desktop/src-tauri/target/${TARGET}/release/bundle/nsis"
APP_EXE="apps/desktop/src-tauri/target/${TARGET}/release/trading-assistant.exe"

echo "开始交叉构建股票盯盘助手 ${RELEASE_VERSION}（Windows x64 NSIS）"
echo "云 API：${RELEASE_API_URL}"

rustup target add "${TARGET}"
VITE_API_URL="${RELEASE_API_URL}" \
  npm --workspace apps/desktop run tauri -- build \
    --runner cargo-xwin \
    --target "${TARGET}" \
    --bundles nsis

deploy/desktop/verify-frontend-api.sh apps/desktop/dist "${RELEASE_API_URL}"

EXE_PATH="$(find "${BUNDLE_DIR}" -maxdepth 1 -type f -name '*setup.exe' -print -quit)"
if [[ -z "${EXE_PATH}" || ! -f "${APP_EXE}" ]]; then
  echo "错误：未找到交叉构建生成的应用 EXE 或 NSIS setup.exe" >&2
  exit 1
fi
APP_FILE_INFO="$(file "${APP_EXE}")"
if [[ "${APP_FILE_INFO}" != *"x86-64"* ]]; then
  echo "错误：Windows 应用可执行文件不是 x86-64 架构" >&2
  exit 1
fi
if ! "$(brew --prefix llvm)/bin/llvm-objdump" -p "${APP_EXE}" | grep -q 'Subsystem.*(Windows GUI)'; then
  echo "错误：Windows 应用不是 GUI 子系统，启动时可能出现控制台窗口" >&2
  exit 1
fi

mkdir -p "${PLATFORM_DIR}"
EXE_OUTPUT="${PLATFORM_DIR}/StockTradingAssistant_${RELEASE_VERSION}_windows-x64-cross-setup.exe"
cp "${EXE_PATH}" "${EXE_OUTPUT}"
echo "${APP_FILE_INFO}"
file "${EXE_OUTPUT}"
(
  cd "${PLATFORM_DIR}"
  shasum -a 256 "$(basename "${EXE_OUTPUT}")" > SHA256SUMS.txt
)

echo "Windows 交叉测试包已生成：${PLATFORM_DIR}"
echo "提示：该产物仍需在 Windows x64 真机完成安装和启动验收。"
