#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

echo "== 操作系统 =="
source /etc/os-release
echo "ID=${ID} VERSION_ID=${VERSION_ID} ARCH=$(uname -m)"
[[ ${ID} == "opencloudos" && ${VERSION_ID} == 9.* ]] \
  || die "仅支持 OpenCloudOS 9.x"
[[ $(uname -m) == "x86_64" ]] || die "当前部署资产仅验证 x86_64"

echo "== 资源 =="
df -h / /opt 2>/dev/null || df -h /
free -h

echo "== SELinux / 防火墙 =="
getenforce 2>/dev/null || true
systemctl is-active firewalld 2>/dev/null || true

echo "== 已安装组件 =="
for command in java python3 nginx redis-server redis-cli docker podman psql pg_isready; do
  if command -v "${command}" >/dev/null 2>&1; then
    echo "${command}: $(command -v "${command}")"
  else
    echo "${command}: MISSING"
  fi
done

echo "== 监听端口（不显示进程环境） =="
ss -lntp | awk 'NR == 1 || /:22 |:80 |:443 |:5433 |:6379 |:8080 |:8090 /'

echo "== PostgreSQL 运行方式 =="
if [[ -x /www/server/pgsql/bin/postgres ]]; then
  /www/server/pgsql/bin/postgres --version
  grep -E "^[[:space:]]*(listen_addresses|port)[[:space:]]*=" \
    /www/server/pgsql/data/postgresql.conf 2>/dev/null || true
elif command -v docker >/dev/null 2>&1; then
  docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -E 'postgres|5433' || true
elif command -v podman >/dev/null 2>&1; then
  podman ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -E 'postgres|5433' || true
fi

echo "只读预检完成。未安装软件、未修改防火墙、未访问数据库内容。"
