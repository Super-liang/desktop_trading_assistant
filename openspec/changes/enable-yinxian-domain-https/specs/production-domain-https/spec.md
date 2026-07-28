## ADDED Requirements

### Requirement: 正式域名使用可信 HTTPS
生产入口 MUST 为 `yinxian.com.cn` 与 `www.yinxian.com.cn` 提供包含相应 DNS SAN 的有效公开证书。

#### Scenario: 访问根域名
- **WHEN** 用户访问 `https://yinxian.com.cn`
- **THEN** 浏览器完成证书校验并返回股票盯盘助手页面

#### Scenario: 访问 www 域名
- **WHEN** 用户访问 `https://www.yinxian.com.cn` 的任意路径
- **THEN** 服务端使用有效证书并 308 跳转到根域名的相同路径

### Requirement: HTTP 验证与强制跳转
服务器 MUST 保持 80 端口可用于 ACME HTTP-01 challenge，并将其他域名 HTTP 请求重定向到 HTTPS。

#### Scenario: ACME 验证请求
- **WHEN** CA 请求 `/.well-known/acme-challenge/` 下的令牌
- **THEN** Nginx 从 `/var/www/letsencrypt` 返回令牌且不重定向

#### Scenario: 普通 HTTP 请求
- **WHEN** 用户通过 HTTP 访问根域名或 `www`
- **THEN** 服务端 308 跳转到 `https://yinxian.com.cn` 并保留路径与查询参数

### Requirement: 保持 IP 客户端兼容
服务器 MUST 在启用域名 HTTPS 后继续为 `https://211.159.158.165` 提供匹配 IP 的有效证书和现有 API。

#### Scenario: 现有桌面客户端请求 IP API
- **WHEN** 已发布客户端访问 `https://211.159.158.165/api/`
- **THEN** TLS 校验成功且请求仍由现有 Spring API 处理

### Requirement: 证书自动续期
域名证书 MUST 纳入现有 Certbot 自动续期流程，并在续期部署后重新加载 Nginx。

#### Scenario: 续期检查
- **WHEN** `stock-watch-cert-renew.timer` 触发 `certbot renew`
- **THEN** 域名和 IP 证书均被检查，成功更新后 Nginx reload
