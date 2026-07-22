#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/lib.sh"
require_root

PUBLIC_IP=${PUBLIC_IP:-211.159.158.165}
BASE_URL=https://${PUBLIC_IP}
validate_secret_file /etc/stock-watch/bootstrap-credentials
source /etc/stock-watch/bootstrap-credentials

json_field() {
  local field=$1
  python3 -c "import json,sys; print(json.load(sys.stdin)['${field}'])"
}

TEST_SUFFIX=$(date -u +%Y%m%d%H%M%S)-$(openssl rand -hex 4)
TEST_EMAIL=verify-${TEST_SUFFIX}@example.invalid
TEST_PASSWORD=VerifyAa1-${TEST_SUFFIX}
USER_TOKEN=
ITEM_ID=

cleanup() {
  if [[ -n ${USER_TOKEN} && -n ${ITEM_ID} ]]; then
    curl --silent --max-time 20 -X DELETE \
      -H "Authorization: Bearer ${USER_TOKEN}" \
      "${BASE_URL}/api/v1/portfolio/items/${ITEM_ID}" >/dev/null || true
  fi
  if [[ -n ${USER_TOKEN} ]]; then
    curl --silent --max-time 20 -X DELETE \
      -H "Authorization: Bearer ${USER_TOKEN}" \
      -H 'Content-Type: application/json' \
      --data "{\"password\":\"${TEST_PASSWORD}\"}" \
      "${BASE_URL}/api/v1/me" >/dev/null || true
  fi
}
trap cleanup EXIT

ADMIN_LOGIN=$(curl --fail --silent --show-error --max-time 20 \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" \
  "${BASE_URL}/api/v1/auth/login")
ADMIN_TOKEN=$(printf '%s' "${ADMIN_LOGIN}" | json_field accessToken)
[[ $(printf '%s' "${ADMIN_LOGIN}" | json_field role) == "ADMIN" ]]
curl --fail --silent --show-error --max-time 20 \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${BASE_URL}/api/v1/admin/users?size=5" \
  | python3 -c "import json,sys; assert json.load(sys.stdin)['totalElements'] >= 1"

# 保持生产行情配置不变。发布验收使用 Redis 中最后一份真实快照，避免临时上游
# 单股接口抖动导致健康的新版本被错误回滚。
CURRENT_MARKET_CONFIG=$(curl --fail --silent --show-error --max-time 20 \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${BASE_URL}/api/v1/market-data/config")
printf '%s' "${CURRENT_MARKET_CONFIG}" | python3 -c \
  "import json,sys; d=json.load(sys.stdin); assert d['provider']=='AKSHARE'"

REGISTER=$(curl --fail --silent --show-error --max-time 20 \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"${TEST_EMAIL}\",\"displayName\":\"云端自动验收\",\"password\":\"${TEST_PASSWORD}\"}" \
  "${BASE_URL}/api/v1/auth/register")
USER_TOKEN=$(printf '%s' "${REGISTER}" | json_field accessToken)

curl --fail --silent --show-error --max-time 20 \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "${BASE_URL}/api/v1/instruments/search?query=600519" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert any(x['instrumentId']=='SSE:600519' for x in d)"

curl --fail --silent --show-error --max-time 20 \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "${BASE_URL}/api/v1/quotes/providers" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert any(x['id']=='AKSHARE_CONFIGURED' and not x['demo'] for x in d)"

curl --fail --silent --show-error --max-time 90 \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "${BASE_URL}/api/v1/quotes/snapshots?symbols=SSE%3A600519" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert len(d)==1; q=d[0]; assert q['instrumentId']=='SSE:600519' and q['source'].startswith('AKSHARE_') and not q['demo'] and float(q['last'])>0 and q['sourceTimestamp']"

ITEM=$(curl --fail --silent --show-error --max-time 90 \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H 'Content-Type: application/json' \
  --data '{"instrumentId":"SSE:600519","displayName":"贵州茅台","quantity":100,"costPrice":1000,"sortOrder":0}' \
  "${BASE_URL}/api/v1/portfolio/items")
ITEM_ID=$(printf '%s' "${ITEM}" | json_field id)
curl --fail --silent --show-error --max-time 90 \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "${BASE_URL}/api/v1/portfolio/items" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert len(d['items'])==1 and float(d['totalMarketValue'])>0 and d['totalProfit'] is not None"

cleanup
USER_TOKEN=
ITEM_ID=
trap - EXIT
echo "注册登录、管理后台、AKShare 真实行情和持仓盈亏验收通过。"
