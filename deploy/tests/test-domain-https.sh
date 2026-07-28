#!/usr/bin/env bash
set -Eeuo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
HTTP_CONFIG=${ROOT}/deploy/opencloudos/nginx/stock-watch-domain-http.conf
HTTPS_CONFIG=${ROOT}/deploy/opencloudos/nginx/stock-watch-domain-https.conf
IP_CONFIG=${ROOT}/deploy/opencloudos/nginx/stock-watch-https.conf
SCRIPT=${ROOT}/deploy/opencloudos/scripts/issue-domain-certificate.sh

bash -n "${SCRIPT}"
grep -Fq 'server_name yinxian.com.cn www.yinxian.com.cn;' "${HTTP_CONFIG}"
grep -Fq 'alias /var/www/letsencrypt/.well-known/acme-challenge/;' "${HTTP_CONFIG}"
grep -Fq 'return 308 https://yinxian.com.cn$request_uri;' "${HTTP_CONFIG}"
grep -Fq 'ssl_certificate /etc/letsencrypt/live/yinxian.com.cn/fullchain.pem;' "${HTTPS_CONFIG}"
grep -Fq 'server_name www.yinxian.com.cn;' "${HTTPS_CONFIG}"
grep -Fq 'server_name yinxian.com.cn;' "${HTTPS_CONFIG}"
grep -Fq 'proxy_pass http://127.0.0.1:8080;' "${HTTPS_CONFIG}"
grep -Fq 'server_name 211.159.158.165;' "${IP_CONFIG}"
grep -Fq 'listen 443 ssl default_server;' "${IP_CONFIG}"
grep -Fq 'BACKUP_FILE=' "${SCRIPT}"
grep -Fq 'restore_domain_config' "${SCRIPT}"
grep -Fq -- '--cert-name "${DOMAIN}"' "${SCRIPT}"
grep -Fq -- '--resolve "${name}:80:${PUBLIC_IP}"' "${SCRIPT}"
grep -Fq 'reload_nginx' "${SCRIPT}"

echo "域名 HTTPS 部署资产测试通过"
