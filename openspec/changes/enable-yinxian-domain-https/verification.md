## 验证记录

日期：2026-07-27（Asia/Shanghai）

### 前置检查

- Google Public DNS over HTTPS 返回 `yinxian.com.cn` 与 `www.yinxian.com.cn` 的 A 记录均为 `211.159.158.165`，TTL 600。
- OpenCloudOS 9.6、Nginx 1.30.4 正常，80/443 监听公网；现有 IP 证书与 `stock-watch-cert-renew.timer` 有效。
- 初始状态下域名 HTTP 命中宝塔默认站点，HTTPS 因只提供 IP SAN 证书而校验失败。

### 部署结果

- 域名证书：`/etc/letsencrypt/live/yinxian.com.cn/fullchain.pem`。
- 私钥：`/etc/letsencrypt/live/yinxian.com.cn/privkey.pem`。
- SAN：`yinxian.com.cn`、`www.yinxian.com.cn`。
- 签发机构：Let's Encrypt YE2；有效期至 2026-10-25 07:14:17 UTC。
- 域名站点：`/www/server/panel/vhost/nginx/stock-watch-domain.conf`。
- 域名配置备份：`/www/server/panel/vhost/nginx/stock-watch-domain.conf.pre-domain-https.20260727T081237Z`。
- IP 配置备份：`/www/server/panel/vhost/nginx/stock-watch.conf.pre-ip-default.20260727T081415Z`。
- 远程部署暂存目录：`/tmp/stock-watch-domain-https-20260727-160925`。

### 功能验证

- `https://yinxian.com.cn/`：HTTP/2 200，页面标题“股票盯盘助手”，证书校验结果 0。
- 强制使用 Google DoH 的公网解析复验：根域名连接 `211.159.158.165`、HTTP 200、证书校验结果 0；`www` HTTP 308、证书校验结果 0。
- `https://www.yinxian.com.cn/path?q=1`：HTTP/2 308 到 `https://yinxian.com.cn/path?q=1`。
- `http://yinxian.com.cn/path?q=1`：HTTP 308 到同路径 HTTPS。
- `https://yinxian.com.cn/api/v1/market-data/config`：未认证返回 401，证明 API 反向代理正常。
- `https://211.159.158.165/api/v1/market-data/config`：继续使用 IP SAN 证书并返回 401，现有桌面客户端兼容。
- HSTS、CSP、X-Content-Type-Options、X-Frame-Options、Referrer-Policy 与 Permissions-Policy 响应头存在。
- 当前开发机的 Mihomo 曾缓存根域名未配置时的 Fake-IP，普通代理请求可能短暂失败；绕过本地缓存的公网 DNS/直连验证均成功，此现象不属于服务器故障。

### 续期验证

- `stock-watch-cert-renew.timer`：enabled、active。
- `nginx -t`：通过。
- `certbot renew --dry-run --no-random-sleep-on-renew`：IP 证书与双域名证书模拟续期全部成功。

### 备案提示

若该 CVM 位于中国大陆，正式对公网提供网站服务仍需完成 ICP 备案；本次只完成 DNS、TLS、Nginx 和自动续期技术配置。
