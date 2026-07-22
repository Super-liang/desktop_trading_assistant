#!/usr/bin/env bash

# 桌面发布构建的公共配置。允许通过环境变量覆盖，便于未来切换正式域名。
RELEASE_API_URL="${VITE_API_URL:-https://211.159.158.165}"
PACKAGE_VERSION="$(node -p "require('./apps/desktop/package.json').version")"
TAURI_VERSION="$(node -p "require('./apps/desktop/src-tauri/tauri.conf.json').version")"
if [[ "${PACKAGE_VERSION}" != "${TAURI_VERSION}" ]]; then
  echo "错误：package.json 与 tauri.conf.json 的版本不一致" >&2
  return 1 2>/dev/null || exit 1
fi
if [[ -n "${RELEASE_VERSION:-}" && "${RELEASE_VERSION}" != "${PACKAGE_VERSION}" ]]; then
  echo "错误：RELEASE_VERSION 不能覆盖应用真实版本 ${PACKAGE_VERSION}" >&2
  return 1 2>/dev/null || exit 1
fi
RELEASE_VERSION="${PACKAGE_VERSION}"
RELEASE_ROOT="${RELEASE_ROOT:-build/installers/${RELEASE_VERSION}}"

if [[ ! "${RELEASE_API_URL}" =~ ^https:// ]]; then
  echo "错误：发布 API 必须使用 HTTPS，当前值为 ${RELEASE_API_URL}" >&2
  return 1 2>/dev/null || exit 1
fi
