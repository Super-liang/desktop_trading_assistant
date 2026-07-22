## ADDED Requirements

### Requirement: 单服务器服务拓扑
系统 SHALL 在受支持的 Linux 云服务器上部署 Web 静态前端、Spring API 和 AKShare 网关，并复用用户指定的现有 PostgreSQL；除 Nginx 的 80/443 外，API、AKShare 和数据库 MUST NOT 直接监听公网地址。

#### Scenario: 服务端口检查
- **WHEN** 部署完成后检查监听端口
- **THEN** Spring 和 AKShare 仅监听回环地址，公网只能访问 Nginx 暴露的 HTTP/HTTPS 端口

#### Scenario: 复用现有数据库
- **WHEN** 部署程序获得现有 PostgreSQL 连接参数
- **THEN** 它只执行连通性检查和应用 Flyway 迁移，不创建、删除或替换 PostgreSQL 实例

### Requirement: Web 与桌面访问边界
Nginx SHALL 同源托管 React Web 构建并代理 `/api`；桌面客户端 SHALL 通过构建时 HTTPS API 地址连接同一后端。Web 版 MUST NOT 宣称支持透明小窗、老板键、托盘等 Tauri 原生能力。

#### Scenario: Web 同源调用
- **WHEN** 用户通过生产域名打开 Web 应用并登录
- **THEN** 浏览器通过同一 HTTPS Origin 请求 `/api`，无需宽泛跨域规则

#### Scenario: 桌面客户端连接
- **WHEN** 使用生产 API 地址构建并安装 Tauri 客户端
- **THEN** 客户端通过 HTTPS 访问云 API，透明小窗和老板键继续在用户本机运行

### Requirement: HTTPS 与入口安全
生产入口 SHALL 使用有效 HTTPS 证书、HTTP 到 HTTPS 跳转、安全响应头、合理请求体限制和认证接口限速；无域名时 SHALL 使用受信任的短期 IP 地址证书，证书自动续期 MUST 可验证。

#### Scenario: HTTPS 验收
- **WHEN** 访问生产域名
- **THEN** HTTP 跳转到 HTTPS，证书域名匹配且 API 响应不暴露内部服务地址

#### Scenario: 证书续期测试
- **WHEN** 运维执行证书续期 dry-run
- **THEN** 测试成功且不会中断现有 Nginx 服务

### Requirement: 进程守护与最小权限
Spring 和 AKShare SHALL 由 systemd 使用专用无登录用户运行，配置自动重启、启动顺序、资源/文件系统隔离和 journald 日志；服务不得以 root 运行。

#### Scenario: 进程异常退出
- **WHEN** API 或 AKShare 进程意外退出
- **THEN** systemd 按受控退避策略重启服务并保留不含密钥的故障日志

### Requirement: 生产密钥门禁
JWT、数据库凭证、管理员引导密码和行情共享密钥 MUST 来自服务器环境文件或秘密管理系统，不得写入仓库、命令历史、Web 产物或日志；生产环境缺失或使用示例值时 SHALL 快速失败。

#### Scenario: 缺少生产 JWT
- **WHEN** 使用 `prod` profile 启动但未提供合格 `JWT_SECRET`
- **THEN** API 在监听端口前失败，日志仅说明配置项无效而不输出密钥

#### Scenario: 环境文件权限过宽
- **WHEN** 部署预检发现环境文件可被 group 或 other 读取
- **THEN** 发布停止并提示修复到仅 root 可读

### Requirement: 数据库安全迁移
发布流程 SHALL 在 Flyway 迁移前验证目标数据库身份、连接能力和可恢复备份确认；没有备份确认时 MUST 停止，且任何脚本 MUST NOT 执行数据库删除、重建或硬编码凭证。

#### Scenario: 未确认备份
- **WHEN** 操作人未提供本次有效备份标识
- **THEN** 发布脚本在启动新 API 和运行迁移前退出

#### Scenario: Flyway 迁移失败
- **WHEN** 新版本数据库迁移失败
- **THEN** 新版本不接收流量，部署保留上一应用版本并输出人工恢复指引

### Requirement: 原子发布与回滚
部署 SHALL 使用不可变版本目录和原子 `current` 链接，并在切换后执行健康与端到端检查；检查失败 SHALL 自动恢复上一应用版本链接。

#### Scenario: 新版本健康失败
- **WHEN** 切换新版本后 API 或 AKShare 健康检查未在超时内成功
- **THEN** 发布脚本切回上一版本、重启服务并验证旧版本健康

### Requirement: 可重复验收
项目 SHALL 提供构建、配置校验、Nginx 校验、systemd 状态、数据库连通、健康、认证、行情和日志脱敏的验证命令，并如实记录未通过项。

#### Scenario: 云端部署验收
- **WHEN** 运维执行部署验证脚本
- **THEN** 脚本检查 Web、API、AKShare、PostgreSQL、HTTPS 和关键业务链路，并以非零状态报告任一失败

### Requirement: 行情许可边界
云端部署 AKShare MUST 持续标记为非商业研究用途，不得因部署到公网而描述为已授权生产行情；面向 ToC 正式用户前 MUST 替换为覆盖 PC/Web 展示和终端分发的授权 Provider。

#### Scenario: AKShare 云端展示
- **WHEN** 非商业研究环境返回 AKShare 行情
- **THEN** Web 和桌面继续显示 `AKSHARE`、延迟/陈旧状态和非投资建议提示
