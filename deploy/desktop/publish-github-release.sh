#!/usr/bin/env bash
set -euo pipefail

TAG=${1:?用法: publish-github-release.sh <tag> <version> <asset-dir>}
VERSION=${2:?缺少应用版本}
ASSET_DIR=${3:?缺少发布资产目录}

[[ ${TAG} == "v${VERSION}" ]] \
  || { echo "错误：标签 ${TAG} 与应用版本 ${VERSION} 不一致" >&2; exit 1; }
[[ -d ${ASSET_DIR} ]] || { echo "错误：发布资产目录不存在" >&2; exit 1; }

EXPECTED_ASSETS=(
  "StockTradingAssistant_${VERSION}_macos-arm64.dmg"
  "StockTradingAssistant_${VERSION}_macos-arm64.app.zip"
  "StockTradingAssistant_${VERSION}_macos-arm64_SHA256SUMS.txt"
  "StockTradingAssistant_${VERSION}_windows-x64-setup.exe"
  "StockTradingAssistant_${VERSION}_windows-x64.msi"
  "StockTradingAssistant_${VERSION}_windows-x64_SHA256SUMS.txt"
)
for asset in "${EXPECTED_ASSETS[@]}"; do
  [[ -f ${ASSET_DIR}/${asset} ]] \
    || { echo "错误：缺少发布资产 ${asset}" >&2; exit 1; }
done
[[ $(find "${ASSET_DIR}" -maxdepth 1 -type f | wc -l | tr -d ' ') == 6 ]] \
  || { echo "错误：发布目录必须且只能包含 6 个资产" >&2; exit 1; }

verify_remote_assets() {
  local expected_file actual_file
  expected_file=$(mktemp)
  actual_file=$(mktemp)
  trap 'rm -f "${expected_file:-}" "${actual_file:-}"' RETURN
  printf '%s\n' "${EXPECTED_ASSETS[@]}" | sort > "${expected_file}"
  gh release view "${TAG}" --json assets --jq '.assets[].name' \
    | sort > "${actual_file}"
  cmp -s "${expected_file}" "${actual_file}" \
    || { echo "错误：GitHub Release 远端资产不完整或包含意外文件" >&2; return 1; }
}

if release_is_draft=$(gh release view "${TAG}" --json isDraft --jq '.isDraft' 2>/dev/null); then
  if [[ ${release_is_draft} == false ]]; then
    verify_remote_assets
    echo "GitHub Release 已发布且资产完整：${TAG}"
    exit 0
  fi
  [[ ${release_is_draft} == true ]] \
    || { echo "错误：无法识别 GitHub Release 状态" >&2; exit 1; }
else
  gh release create "${TAG}" \
    --draft \
    --verify-tag \
    --title "股票盯盘助手 ${VERSION}" \
    --generate-notes
fi

gh release upload "${TAG}" "${ASSET_DIR}"/* --clobber
verify_remote_assets
gh release edit "${TAG}" --draft=false --latest
echo "GitHub Release 发布完成：${TAG}"
