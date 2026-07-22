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
grep -F -q "tags: [\"v*\"]" .github/workflows/desktop-installers.yml
grep -F -q "inputs.api_url || 'https://211.159.158.165'" .github/workflows/desktop-installers.yml
grep -F -q 'needs: [macos-arm64, windows-x64]' .github/workflows/desktop-installers.yml
grep -F -q 'contents: write' .github/workflows/desktop-installers.yml
grep -F -q 'GITHUB_REF_NAME' .github/workflows/desktop-installers.yml
grep -F -q 'prepare-release-assets.sh' .github/workflows/desktop-installers.yml
grep -F -q 'publish-github-release.sh' .github/workflows/desktop-installers.yml
grep -F -q '$machine -ne 0x8664' .github/workflows/desktop-installers.yml
grep -E -q 'actions/checkout@[0-9a-f]{40}' .github/workflows/desktop-installers.yml
grep -E -q 'actions/download-artifact@[0-9a-f]{40}' .github/workflows/desktop-installers.yml

ARTIFACT_ROOT="${TMP_DIR}/artifacts"
PUBLISH_ROOT="${TMP_DIR}/publish"
MAC_ROOT="${ARTIFACT_ROOT}/macos-arm64/0.1.0/macos-arm64"
WINDOWS_ROOT="${ARTIFACT_ROOT}/windows-x64/0.1.0/windows-x64"
mkdir -p "${MAC_ROOT}" "${WINDOWS_ROOT}"
touch "${MAC_ROOT}/StockTradingAssistant_0.1.0_macos-arm64.dmg"
touch "${MAC_ROOT}/StockTradingAssistant_0.1.0_macos-arm64.app.zip"
touch "${MAC_ROOT}/SHA256SUMS.txt"
touch "${WINDOWS_ROOT}/StockTradingAssistant_0.1.0_windows-x64-setup.exe"
touch "${WINDOWS_ROOT}/StockTradingAssistant_0.1.0_windows-x64.msi"
touch "${WINDOWS_ROOT}/SHA256SUMS.txt"
bash deploy/desktop/prepare-release-assets.sh 0.1.0 "${ARTIFACT_ROOT}" "${PUBLISH_ROOT}"
[[ $(find "${PUBLISH_ROOT}" -maxdepth 1 -type f | wc -l | tr -d ' ') == 6 ]]
[[ -f "${PUBLISH_ROOT}/StockTradingAssistant_0.1.0_macos-arm64_SHA256SUMS.txt" ]]
[[ -f "${PUBLISH_ROOT}/StockTradingAssistant_0.1.0_windows-x64_SHA256SUMS.txt" ]]

FAKE_BIN="${TMP_DIR}/bin"
FAKE_STATE="${TMP_DIR}/release-state"
FAKE_ASSETS="${TMP_DIR}/remote-assets"
FAKE_LOG="${TMP_DIR}/gh.log"
mkdir -p "${FAKE_BIN}"
cat > "${FAKE_BIN}/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "$*" >> "${FAKE_GH_LOG}"
command_name=${1:-}
subcommand=${2:-}
tag=${3:-}
if [[ ${command_name} != release ]]; then exit 2; fi
case "${subcommand}" in
  view)
    [[ -f ${FAKE_GH_STATE} ]] || exit 1
    if [[ "$*" == *"isDraft"* ]]; then
      [[ $(cat "${FAKE_GH_STATE}") == draft ]] && echo true || echo false
    else
      find "${FAKE_GH_ASSETS}" -maxdepth 1 -type f -exec basename {} \; | sort
    fi
    ;;
  create)
    printf 'draft' > "${FAKE_GH_STATE}"
    mkdir -p "${FAKE_GH_ASSETS}"
    ;;
  upload)
    mkdir -p "${FAKE_GH_ASSETS}"
    shift 3
    for argument in "$@"; do
      [[ ${argument} == --clobber ]] && continue
      cp "${argument}" "${FAKE_GH_ASSETS}/"
    done
    ;;
  edit)
    printf 'published' > "${FAKE_GH_STATE}"
    ;;
  *) exit 2 ;;
esac
EOF
chmod +x "${FAKE_BIN}/gh"
PATH="${FAKE_BIN}:${PATH}" FAKE_GH_STATE="${FAKE_STATE}" \
  FAKE_GH_ASSETS="${FAKE_ASSETS}" FAKE_GH_LOG="${FAKE_LOG}" \
  bash deploy/desktop/publish-github-release.sh v0.1.0 0.1.0 "${PUBLISH_ROOT}"
[[ $(cat "${FAKE_STATE}") == published ]]
[[ $(find "${FAKE_ASSETS}" -maxdepth 1 -type f | wc -l | tr -d ' ') == 6 ]]
grep -F -q 'release create v0.1.0 --draft' "${FAKE_LOG}"
grep -F -q 'release edit v0.1.0 --draft=false --latest' "${FAKE_LOG}"

FIRST_RUN_LINES=$(wc -l < "${FAKE_LOG}" | tr -d ' ')
PATH="${FAKE_BIN}:${PATH}" FAKE_GH_STATE="${FAKE_STATE}" \
  FAKE_GH_ASSETS="${FAKE_ASSETS}" FAKE_GH_LOG="${FAKE_LOG}" \
  bash deploy/desktop/publish-github-release.sh v0.1.0 0.1.0 "${PUBLISH_ROOT}"
SECOND_RUN_LINES=$(wc -l < "${FAKE_LOG}" | tr -d ' ')
[[ $((SECOND_RUN_LINES - FIRST_RUN_LINES)) -eq 2 ]]

echo "桌面安装包脚本定向测试通过"
