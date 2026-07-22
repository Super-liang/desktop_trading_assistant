## Why

当前项目只有本地开发与容器示例，缺少复用现有 PostgreSQL 的云服务器部署、HTTPS 入口、进程守护、密钥隔离、备份前检查和可回滚发布流程。需要形成可审计的生产部署能力，使 Web 前端、Spring API 与 AKShare 研究网关能在单台 Linux 云服务器稳定运行，同时让桌面客户端安全连接云端 API。

## What Changes

- 增加面向 OpenCloudOS 9.6（x86_64、dnf、systemd、SELinux）的无容器部署目录，安装静态 Web、Spring Boot JAR 和 Python AKShare 网关，但不创建、不删除也不接管现有 PostgreSQL 容器。
- 使用 Nginx 托管 Web 静态资源，并同源反向代理 `/api` 与受限健康检查；Spring 和 AKShare 仅监听回环地址。
- 提供 systemd service、最小权限运行用户、只读部署目录、独立环境文件、日志轮转和自动重启配置。
- 增加生产环境强校验：JWT、数据库密码、管理员密码和 AKShare 共享密钥不得使用默认值或写入仓库。
- 提供数据库连通性/迁移前检查、备份确认、Flyway 迁移、健康检查、原子版本切换和回滚脚本。
- 增加 Let’s Encrypt 短期 IP 证书 HTTPS、桌面客户端 API 地址构建方式、防火墙端口和部署验收文档；后续获得域名后可迁移到普通域名证书。
- 明确云端 Web 版不具备 Tauri 的透明窗口、老板键、托盘等原生能力；这些能力继续由本地桌面客户端提供。
- 保留 AKShare 仅供非商业研究的限制，正式 ToC 商用必须换成获授权行情源。

## Capabilities

### New Capabilities

- `single-server-production-deployment`: 覆盖现有 PostgreSQL 接入、Web/API/AKShare 部署、HTTPS、服务守护、密钥、迁移、回滚、桌面连接和验收。

### Modified Capabilities

无。

## Impact

- 新增 `deploy/` 下的 Nginx、systemd、环境变量模板、安装/发布/回滚/验证脚本。
- 调整 Spring 生产配置的默认值门禁、反向代理处理和可配置 CORS。
- 增加 Web 同源生产构建方式及桌面端云 API 构建说明。
- 更新 README、架构、运维和合规文档；不修改现有 PostgreSQL 数据结构，Flyway 仍是唯一迁移入口。
- 实际服务器变更需要目标主机操作系统、域名/DNS、SSH sudo 权限和现有数据库连接参数。
