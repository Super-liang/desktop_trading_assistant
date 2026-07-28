## Why

`yinxian.com.cn` 与 `www.yinxian.com.cn` 已解析到生产服务器，但 Nginx 仅识别 IP 且当前证书只覆盖 IP，导致域名 HTTP 落到默认站点、HTTPS 证书不匹配。需要为正式域名提供可自动续期的 HTTPS，同时保持现有桌面客户端对 IP API 的兼容。

## What Changes

- 增加 `yinxian.com.cn` 与 `www.yinxian.com.cn` 的 Nginx 虚拟主机配置。
- 通过现有 Certbot Webroot 流程申请双域名 Let's Encrypt 证书并纳入续期定时器。
- 将 HTTP 与 `www` 统一重定向到 `https://yinxian.com.cn`。
- 保留现有 IP HTTPS 虚拟主机和证书，避免已发布桌面客户端失效。
- 将域名配置、签发脚本和验证流程同步回仓库，避免后续部署覆盖现场配置。

## Capabilities

### New Capabilities

- `production-domain-https`: 规定正式域名解析、TLS、重定向、IP 兼容与自动续期行为。

### Modified Capabilities

无。

## Impact

- 影响生产 Nginx、Let's Encrypt 证书目录与续期任务。
- 影响 `deploy/opencloudos` 下的 Nginx 和证书部署资产。
- 不修改 PostgreSQL、Redis、Spring API、AKShare 或用户数据。
