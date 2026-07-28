## 1. 部署资产

- [x] 1.1 增加域名 HTTP challenge 与完整 HTTPS Nginx 模板
- [x] 1.2 增加域名证书签发与安全切换脚本，保留 IP 虚拟主机
- [x] 1.3 为域名、证书路径、跳转和回滚护栏补充部署测试

## 2. 生产部署

- [x] 2.1 验证公网 DNS、80/443 连通性、现有 IP HTTPS 与续期任务
- [x] 2.2 备份并部署域名 HTTP challenge 配置
- [x] 2.3 申请 `yinxian.com.cn` 与 `www.yinxian.com.cn` 证书
- [x] 2.4 部署域名 HTTPS 配置并保持 IP 站点可用

## 3. 验证

- [x] 3.1 验证证书 SAN、根域名首页、www/HTTP 跳转和 API 响应
- [x] 3.2 验证 Certbot 续期配置和 Nginx reload
- [x] 3.3 记录生产备份、证书有效期、备案提示和验证证据
