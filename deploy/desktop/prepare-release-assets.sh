#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?用法: prepare-release-assets.sh <version> <artifact-root> <output-dir>}
ARTIFACT_ROOT=${2:?缺少 Artifact 根目录}
OUTPUT_DIR=${3:?缺少发布输出目录}

[[ ${VERSION} =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] \
  || { echo "错误：桌面版本格式无效：${VERSION}" >&2; exit 1; }
[[ -d ${ARTIFACT_ROOT}/macos-arm64 && -d ${ARTIFACT_ROOT}/windows-x64 ]] \
  || { echo "错误：缺少平台 Artifact 目录" >&2; exit 1; }

mkdir -p "${OUTPUT_DIR}"

copy_unique() {
  local search_root=$1
  local filename=$2
  local output_name=$3
  local matches=()
  while IFS= read -r -d '' match; do
    matches+=("${match}")
  done < <(find "${search_root}" -type f -name "${filename}" -print0)
  if [[ ${#matches[@]} -ne 1 ]]; then
    echo "错误：${filename} 匹配数量应为 1，实际为 ${#matches[@]}" >&2
    exit 1
  fi
  cp "${matches[0]}" "${OUTPUT_DIR}/${output_name}"
}

copy_unique "${ARTIFACT_ROOT}/macos-arm64" \
  "StockTradingAssistant_${VERSION}_macos-arm64.dmg" \
  "StockTradingAssistant_${VERSION}_macos-arm64.dmg"
copy_unique "${ARTIFACT_ROOT}/macos-arm64" \
  "StockTradingAssistant_${VERSION}_macos-arm64.app.zip" \
  "StockTradingAssistant_${VERSION}_macos-arm64.app.zip"
copy_unique "${ARTIFACT_ROOT}/macos-arm64" \
  "SHA256SUMS.txt" \
  "StockTradingAssistant_${VERSION}_macos-arm64_SHA256SUMS.txt"
copy_unique "${ARTIFACT_ROOT}/windows-x64" \
  "StockTradingAssistant_${VERSION}_windows-x64-setup.exe" \
  "StockTradingAssistant_${VERSION}_windows-x64-setup.exe"
copy_unique "${ARTIFACT_ROOT}/windows-x64" \
  "StockTradingAssistant_${VERSION}_windows-x64.msi" \
  "StockTradingAssistant_${VERSION}_windows-x64.msi"
copy_unique "${ARTIFACT_ROOT}/windows-x64" \
  "SHA256SUMS.txt" \
  "StockTradingAssistant_${VERSION}_windows-x64_SHA256SUMS.txt"

[[ $(find "${OUTPUT_DIR}" -maxdepth 1 -type f | wc -l | tr -d ' ') == 6 ]] \
  || { echo "错误：发布资产数量不是 6" >&2; exit 1; }
