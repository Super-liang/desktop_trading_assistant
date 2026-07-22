## Context

应用由 React/Vite 前端、Spring Boot API、AKShare FastAPI 网关和 PostgreSQL 组成。只读审计确认现有数据库是宝塔目录 `/www/server/pgsql` 下的宿主机 PostgreSQL 18.0，不是容器；数据目录为 `/www/server/pgsql/data`，端口 5433，数据库 `trading`。桌面端是 Tauri 本地应用，无法把原生窗口进程迁移到服务器；服务器部署的是同一 React UI 的 Web 构建以及供 Web/桌面共同访问的 API。

目标环境为 OpenCloudOS 9.6 x86_64，服务器公网地址为 `211.159.158.165`，当前无域名，SSH 使用 root/22。SSH 公钥和只读审计已完成：主机有 3.6 GiB 内存、约 32 GiB 可用磁盘，Python 3.11 和宝塔 Nginx 1.30.4 已安装，Java 17 未安装；SELinux 已在部署前处于 Disabled，firewalld 未运行。公网目前开放 22、80、5433，其中 5433 绑定所有网卡且 `pg_hba.conf` 允许 `trading` 从 `0.0.0.0/0` 发起认证，必须在上线前收口。实际写入仍需用户确认影响范围和数据库备份。公网最终只开放 SSH、HTTP 和 HTTPS，API、AKShare 与 PostgreSQL 不得直接暴露。

## Goals / Non-Goals

**Goals:**

- 复用现有 PostgreSQL，安全部署 Web、API 和 AKShare，并支持桌面客户端通过 HTTPS 连接。
- 提供可重复、可审计、可回滚的安装与版本发布流程。
- 使用最小权限、环境文件、回环监听、防火墙、TLS 和安全响应头保护服务。
- 在执行 Flyway 前验证数据库连通性与备份确认，发布后验证注册登录、行情、持仓和管理接口。
- 服务异常后自动恢复，并提供不泄密的 systemd/journald 运维命令。

**Non-Goals:**

- 不自动安装、删除、升级、重建或开放现有 PostgreSQL。
- 不自动修改云厂商安全组、DNS 记录或取得未知域名的证书。
- 不在服务器运行 Tauri 原生窗口、透明小窗、老板键或托盘。
- 不把 AKShare 转为可商用数据源，也不在本次引入 Kubernetes、多机高可用或 Redis。

## Decisions

### 1. 使用 systemd + 现有宝塔 Nginx，不接管数据库生命周期

服务器已有宿主机 PostgreSQL，使用无容器部署可避免端口、数据目录和数据库生命周期冲突。现有 Nginx 由宝塔安装在 `/www/server/nginx`，站点配置入口是 `/www/server/panel/vhost/nginx/*.conf`，运行用户为 `www`。部署脚本只新增独立的 `stock-watch.conf`，不覆盖宝塔主配置。Nginx 托管静态 Web并把 `/api/` 转发到 `127.0.0.1:8080`；Spring 通过 `http://127.0.0.1:8090` 调用 AKShare。Spring 与 AKShare由两个 systemd service 管理。

脚本不得安装第二套 Nginx，也不得启动、停止或升级现有 PostgreSQL。

### 2. 版本目录和原子 current 链接

部署根目录为 `/opt/stock-watch`：

```text
/opt/stock-watch/
  releases/<release-id>/
    api/app.jar
    akshare/
    web/
  current -> releases/<release-id>
/etc/stock-watch/
  api.env
  akshare.env
```

发布脚本在新版本目录完成校验后原子切换 `current`，重启后验证健康；失败则切回上一个链接并重启。密钥环境文件归 root 所有、权限 `0600`，不进入 release 和 Git。

### 3. 同源 Web 与 HTTPS

Web 构建使用空的 `VITE_API_URL`，浏览器请求同源 `/api`，从而不依赖宽泛 CORS。Nginx 为 SPA 使用 `try_files ... /index.html`，为 SSE 关闭代理缓冲，并设置安全响应头、请求体限制和认证端点限速。

当前使用 Let’s Encrypt 短期 IP 地址证书：Certbot 5.4+ 通过 webroot、`--preferred-profile shortlived` 和 `--ip-address 211.159.158.165` 申请，证书约 6 天有效，Nginx 手工引用证书文件并以 systemd timer 高频自动续期。正式验收必须使用 `https://211.159.158.165`；后续取得域名后迁移到普通域名证书。

### 4. 服务只监听回环地址

- Spring：`127.0.0.1:8080`
- AKShare：`127.0.0.1:8090`
- PostgreSQL：现有宿主机进程监听 5433；Spring 使用 `127.0.0.1:5433`。上线前把 `listen_addresses` 收窄到 `127.0.0.1`，并把 `pg_hba.conf` 的 `0.0.0.0/0` 应用规则删除或收窄；同时在腾讯云安全组关闭公网 5433
- Nginx：公网 80/443

systemd 使用专用无登录用户，开启 `NoNewPrivileges`、`PrivateTmp`、`ProtectSystem` 等沙箱选项；写权限只授予确实需要的目录。

### 5. 生产密钥必须显式配置

`prod` profile 不提供 JWT、数据库密码或 AKShare Key 默认值。部署预检检查：

- `JWT_SECRET` 至少 32 字节且不是开发示例；
- PostgreSQL JDBC URL、账号和密码非空；
- `AKSHARE_API_KEY` 与 `QUOTE_HTTP_API_KEY` 相同且随机；
- 初始管理员密码仅首次引导时使用，创建后从环境文件移除；
- 环境文件不允许 group/other 读取。

### 6. 数据库迁移采用“备份确认 + Flyway 单写者”

脚本使用 `/www/server/pgsql/bin` 中与服务端同版本的 `pg_isready`、`pg_dump` 和 `pg_restore`，以 `trading` 应用账号连接回环地址并把数据库 `trading` 导出到宿主机 root-only 备份目录；备份文件非空、校验和生成且 `pg_restore --list` 可解析后生成备份标识。随后才启动单个 Spring 实例执行 Flyway。若迁移失败，不切换流量；数据库结构回滚不由脚本猜测，恢复策略使用已验证的 custom-format 备份。

### 7. 已有主机安全状态采用补偿控制

部署前 SELinux 已被关闭，当前发布不自动开启，因为启用策略可能要求修复全机标签并重启，影响宝塔和数据库。安装脚本在 Disabled 状态仅告警；若以后恢复 Enforcing，再应用 Nginx 回环代理所需的最小 SELinux规则。

firewalld 当前未运行，安装脚本不擅自启用，以免改变 SSH、宝塔或 Docker 现有网络行为。数据库先通过 PostgreSQL 自身监听地址和云安全组双重收口；启用 firewalld 作为后续经确认的独立加固动作。

### 8. 桌面端使用云 API 的独立构建

Web 产物使用同源 API；macOS/Windows Tauri 安装包在构建时设置 `VITE_API_URL=https://<domain>`。Tauri CSP 已允许 `https:`，Spring 保留 `tauri://localhost` 与 `https://tauri.localhost` 来源。服务端不会托管或远程执行 Tauri 壳，仅可额外托管已签名安装包下载。

## Risks / Trade-offs

- [SELinux 已在部署前关闭] → 本次不自动开启、不掩盖现状；依靠回环监听、最小权限 systemd、云安全组和 PostgreSQL 自身访问控制，后续安排独立维护窗口恢复 Enforcing。
- [firewalld 已在部署前停止] → 安装脚本不擅自启用；上线门禁要求云安全组关闭 5433，且 PostgreSQL 仅监听回环。firewalld 加固需独立确认。
- [宝塔 Nginx 被覆盖] → 自动发现当前编译前缀和 vhost include，仅新增单独站点文件，写入前备份同名旧文件并执行 `nginx -t`。
- [误连或迁移错误数据库] → 发布前显示脱敏后的主机/端口/库名，要求备份标识和人工确认，禁止 DROP/reset 操作。
- [IP 证书仅约 6 天且 Certbot 版本要求高] → 使用 Certbot 5.4+、webroot 和短期 profile，配置 systemd timer 并执行 staging、production 与续期演练。
- [AKShare 公共源限流或不可商用] → 使用缓存、双公开源和陈旧标记；仅作研究验证，生产 ToC 替换授权 Provider。
- [单机故障导致整体不可用] → systemd 自动重启、外部 PostgreSQL 备份和健康监测；多机高可用留后续。
- [Web 暴露扩大攻击面] → HTTPS、同源、限速、最小端口、安全头、强密钥和日志脱敏。

## Migration Plan

1. 完成 SSH 公钥授权，确认 OpenCloudOS、IP 证书前提和脱敏数据库进程信息。
2. 在本地构建并验证 Web、JAR、Python wheel/依赖和部署模板。
3. 服务器只读审计端口、磁盘、Java/Python/Nginx/PostgreSQL 连通性和备份状态。
4. 创建运行用户、目录、环境文件和 systemd 配置；向宝塔 vhost 目录新增独立 Nginx 站点文件，不覆盖主配置且暂不切换公网流量。
5. 上传新 release，完成数据库备份确认后启动 AKShare 和 API，验证回环健康。
6. 切换 Web、校验 Nginx，申请 IP 地址证书并启用 HTTPS，再进行端到端验收。
7. 失败时切回上一 release；若数据库迁移已执行，依据已确认备份和 Flyway 策略处理，不自动逆向 SQL。

## Open Questions

- 用户是否批准修改 PostgreSQL 的 `listen_addresses`/`pg_hba.conf` 并执行一次受控 reload 或 restart？
- 腾讯云安全组是否已允许 80/443，并能否立即移除公网 5433？
- 数据库 `trading` 应用账号密码如何在服务器本地安全录入？
