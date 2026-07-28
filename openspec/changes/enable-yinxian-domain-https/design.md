## Context

生产服务器为 OpenCloudOS 9.6，宝塔 Nginx 1.30.4 当前在 80/443 提供 IP 虚拟主机，已有 Let's Encrypt IP 短证书及每六小时续期任务。正式域名的两个 A 记录均已生效，但未配置对应 `server_name` 和域名证书。

## Goals / Non-Goals

**Goals:**

- 为根域名和 `www` 提供可信 HTTPS，并将公开入口统一到根域名。
- 复用当前 Webroot、Certbot 与续期服务。
- 保留 IP HTTPS，确保当前桌面应用继续访问 `https://211.159.158.165`。
- 配置变更可验证、可回滚，并同步到仓库。

**Non-Goals:**

- 不修改桌面应用 API 地址或发布新安装包。
- 不变更 API、数据库、Redis、AKShare 服务。
- 不自动处理 ICP 备案流程。

## Decisions

1. **使用独立域名虚拟主机**：域名和 IP 使用不同证书与 SNI server block，避免域名证书替换 IP 证书后破坏现有客户端。
2. **使用 Webroot HTTP-01**：复用 `/var/www/letsencrypt` 与现有 80 端口，先部署仅包含 challenge 的 HTTP 配置，再申请证书。
3. **根域名作为规范入口**：`http://` 和 `https://www` 均 308 跳转到 `https://yinxian.com.cn`，API 路径和查询参数保持不变。
4. **复用现有续期 timer**：`certbot renew` 会枚举全部 lineage；续期 deploy hook 继续 reload Nginx。
5. **仓库提供域名模板和签发脚本**：现场文件由模板生成，后续部署不再退回仅 IP 配置。

## Risks / Trade-offs

- [证书签发前 HTTPS 配置引用不存在文件] → 分两阶段部署，签发前仅加载 HTTP challenge 配置。
- [Nginx 配置错误导致现有服务中断] → 每次 reload 前执行 `nginx -t`，并保留时间戳备份。
- [证书申请频率限制] → 先验证两个域名解析与 challenge，再只提交一次生产申请。
- [未备案导致大陆访问受限] → HTTPS 技术配置与备案独立，最终明确记录备案前置要求。

## Migration Plan

1. 增加仓库域名 HTTP/HTTPS 模板和签发脚本，执行静态检查。
2. 备份服务器现有站点，部署 HTTP challenge 虚拟主机并 reload。
3. 申请包含根域名和 `www` 的证书。
4. 部署完整 HTTPS 虚拟主机，`nginx -t` 后 reload。
5. 验证证书 SAN、跳转、首页、API、IP 兼容及续期任务。
6. 失败时恢复 Nginx 备份；证书文件保留不会影响旧 IP 站点。

## Open Questions

无。
