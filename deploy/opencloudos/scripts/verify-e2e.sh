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

restore_market_config() {
  local restored
  restored=$(curl --fail --silent --show-error --max-time 20 -X PUT \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    --data "${ORIGINAL_MARKET_CONFIG}" \
    "${BASE_URL}/api/v1/admin/market-data/config")
  printf '%s' "${restored}" | python3 -c \
    "import json,sys; expected=json.loads(sys.argv[1]); actual=json.load(sys.stdin); assert all(actual[k] == v for k,v in expected.items())" \
    "${ORIGINAL_MARKET_CONFIG}"
}

TEST_SUFFIX=$(date -u +%Y%m%d%H%M%S)-$(openssl rand -hex 4)
TEST_EMAIL=verify-${TEST_SUFFIX}@example.invalid
TEST_PASSWORD=VerifyAa1-${TEST_SUFFIX}
USER_TOKEN=
ITEM_ID=
ORIGINAL_MARKET_CONFIG=

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
  if [[ -n ${ORIGINAL_MARKET_CONFIG} && -n ${ADMIN_TOKEN:-} ]]; then
    curl --silent --max-time 20 -X PUT \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" \
      -H 'Content-Type: application/json' \
      --data "${ORIGINAL_MARKET_CONFIG}" \
      "${BASE_URL}/api/v1/admin/market-data/config" >/dev/null || true
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

# 验收期间临时切到东财单股模式，避免全市场 Redis 在非交易时段尚无首份快照。
CURRENT_MARKET_CONFIG=$(curl --fail --silent --show-error --max-time 20 \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${BASE_URL}/api/v1/market-data/config")
ORIGINAL_MARKET_CONFIG=$(printf '%s' "${CURRENT_MARKET_CONFIG}" | python3 -c \
  "import json,sys; d=json.load(sys.stdin); print(json.dumps({k:d[k] for k in ('provider','mode','snapshotSource','singleSource','refreshSeconds')}))")
SINGLE_MARKET_CONFIG=$(printf '%s' "${ORIGINAL_MARKET_CONFIG}" | python3 -c \
  "import json,sys; d=json.load(sys.stdin); d['mode']='SINGLE_STOCK'; d['singleSource']='EASTMONEY'; print(json.dumps(d))")
curl --fail --silent --show-error --max-time 20 -X PUT \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  --data "${SINGLE_MARKET_CONFIG}" \
  "${BASE_URL}/api/v1/admin/market-data/config" >/dev/null

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
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert len(d)==1; q=d[0]; assert q['instrumentId']=='SSE:600519' and q['source']=='AKSHARE_EASTMONEY_SINGLE' and not q['demo'] and float(q['last'])>0"

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

restore_market_config
ORIGINAL_MARKET_CONFIG=
cleanup
USER_TOKEN=
ITEM_ID=
trap - EXIT
echo "注册登录、管理后台、AKShare 真实行情和持仓盈亏验收通过。"
