#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
RELEASE_ID=${1:-$(date -u +%Y%m%dT%H%M%SZ)}
[[ ${RELEASE_ID} =~ ^[a-zA-Z0-9_.-]+$ ]] || {
  echo "发布版本标识格式无效" >&2
  exit 1
}

OUTPUT_ROOT="${ROOT}/build/releases"
OUTPUT="${OUTPUT_ROOT}/${RELEASE_ID}"
ARCHIVE="${OUTPUT_ROOT}/${RELEASE_ID}.tar.gz"
[[ ! -e ${OUTPUT} && ! -e ${ARCHIVE} ]] || {
  echo "发布输出已存在：${RELEASE_ID}" >&2
  exit 1
}

mkdir -p "${OUTPUT}/api" "${OUTPUT}/web" "${OUTPUT}/akshare/akshare_gateway"

(
  cd "${ROOT}/services/api"
  JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)} \
    ./mvnw -q clean package
)
API_JAR=$(find "${ROOT}/services/api/target" -maxdepth 1 -type f \
  -name 'trading-assistant-api-*.jar' ! -name '*.original' | head -n 1)
[[ -n ${API_JAR} ]] || {
  echo "未找到 API JAR" >&2
  exit 1
}
cp "${API_JAR}" "${OUTPUT}/api/app.jar"

(
  cd "${ROOT}/apps/desktop"
  VITE_API_URL='' npm run build
)
cp -R "${ROOT}/apps/desktop/dist/." "${OUTPUT}/web/"

cp "${ROOT}/services/akshare-gateway/requirements.txt" "${OUTPUT}/akshare/"
cp "${ROOT}/services/akshare-gateway/akshare_gateway/"*.py \
  "${OUTPUT}/akshare/akshare_gateway/"

tar -C "${OUTPUT}" -czf "${ARCHIVE}" .
(
  cd "${OUTPUT_ROOT}"
  shasum -a 256 "$(basename "${ARCHIVE}")" >"$(basename "${ARCHIVE}").sha256"
)
echo "${ARCHIVE}"
