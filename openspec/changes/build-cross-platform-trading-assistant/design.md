## Context

仓库当前为空壳，需要同时建立桌面端、ToC API、数据模型、行情网关与交付流水线。竞品调研显示，东方财富/同花顺等综合终端覆盖深但偏重，轻量隐蔽工具则常见 Windows 单平台、未签名、单接口失效、热键和内存问题。本项目一期聚焦“管理主窗 + 原生透明小窗”的窄而完整垂直切片。

利益相关方包括个人用户、平台管理员、行情供应商、运维/客服和合规团队。关键约束是沪深实时行情的 PC 展示及再分发授权、持仓成本的高敏感性、Windows/macOS 原生差异，以及三期 AI 投顾边界。

## Goals / Non-Goals

**Goals:**

- 交付可本地部署执行、可在 Windows/macOS 构建的完整一期。
- 完成注册登录、自选持仓、Mock 实时行情、盈亏计算、透明置顶和老板键的端到端闭环。
- 用 Provider SPI 和标准行情模型隔离供应商差异，真实生产源可在获得授权后接入。
- 默认最小化采集与暴露个人信息，管理员不可见持仓明细。
- 用模块化单体保持一期简单，并为会员、设置和 AI 网关保留清晰边界。

**Non-Goals:**

- 一期不接交易下单、券商账户、资金账户、支付、Level-2、资讯社区或真实 AI 建议。
- 不爬取或逆向同花顺、东方财富、雪球等私有接口。
- 不在未取得书面授权时承诺生产实时行情。
- 不在未取得投顾资质或持牌合作时提供荐股、买卖时机或收益承诺。

## Decisions

### 1. Tauri 2 + React/TypeScript 桌面端

Tauri 原生窗口 API 和 global-shortcut 插件直接覆盖透明、置顶、托盘、全局快捷键，安装体积与常驻内存通常低于 Electron，适合极简常驻小窗。React 负责管理主窗和盯盘视图，TanStack Query 管理服务端状态，Zustand 管理窗口/UI 状态。

替代方案：Electron 生态更成熟但包体和常驻资源较高；Flutter 桌面 UI 一致性好但 Web 管理复用和透明窗口插件风险更高。本项目优先 Tauri。

macOS 不启用私有 API；透明背景通过 `transparent: true` 实现，公开分发必须实际签名、公证并在 Intel/Apple Silicon 验收。

### 2. Java 17 + Spring Boot 3 模块化单体 API

服务端按 `auth`、`user/admin`、`portfolio`、`quote` 模块划分，使用 Spring Security、JPA、Flyway、PostgreSQL。模块化单体比一期拆微服务更易部署和保证事务，未来行情网关或 AI 网关达到独立伸缩需求时再拆分。

SSE 向当前用户推送行情更新；相比 WebSocket，当前单向数据流更简单、可自动重连且易穿越代理。行情供应商若提供流式接口，由 Provider 在服务端聚合后转换成 SSE。

### 3. 安全会话与权限

密码使用 BCrypt 自适应哈希。访问令牌采用短期 JWT；刷新令牌为高熵随机值，服务端仅保存 SHA-256 摘要并在刷新时轮换。管理员能力使用 `ROLE_ADMIN`；初始管理员只允许通过显式环境变量引导创建，生产缺少强 JWT 密钥则启动失败。

一期默认仅在进程内存保存会话，应用重启后重新登录，并通过 Tauri 进程内事件向透明小窗同步；不使用 Web Storage 或明文文件。二期若加入重启免登录，再使用操作系统 Keychain/Credential Manager（Tauri Stronghold/安全存储适配）保存刷新信息。所有业务查询按当前用户 ID 限定。

### 4. 行情网关与演示源

`QuoteProvider` 提供 `search`、`snapshots`、`health` 和能力描述；标准模型使用 `InstrumentId(exchange, code, assetType)` 和 `BigDecimal`，携带 source、sourceTimestamp、receivedAt、marketPhase、delayed、stale。

一期内置固定种子的 `MockQuoteProvider`，按交易时段产生可复现小幅波动，并在 API 和 UI 显示 `DEMO`。生产 Adapter 的配置扩展点保留，但不提供任何未经授权的网页接口。主备 Provider 只在授权用途相同的来源间切换，熔断、配额和源切换写审计。

### 5. 持仓数据与盈亏

服务端保存用户手工录入的证券、数量和单位成本，数值使用 `numeric`/`BigDecimal`。行情不长期落库；客户端用最新标准行情实时派生市值和盈亏，服务端返回同口径快照供校验。公式不含佣金、印花税、分红和送转，一期在界面明确标注。

一期云端保存是实现完整跨设备账户闭环的默认模式；数据库传输/磁盘加密和行级应用隔离为上线要求。二期再提供“仅本机加密持仓”模式和显式云同步选择。

### 6. API 与数据模型

核心 API：

- `POST /api/v1/auth/register|login|refresh|logout`
- `DELETE /api/v1/me`
- `GET/POST /api/v1/portfolio/items`，`PUT/DELETE /api/v1/portfolio/items/{id}`
- `GET /api/v1/quotes/search`，`GET /api/v1/quotes/snapshots`，`GET /api/v1/quotes/stream`
- `GET /api/v1/admin/users`，`PATCH /api/v1/admin/users/{id}/status`，`GET /api/v1/admin/audits`
- `GET /actuator/health`

表包括 `users`、`refresh_tokens`、`portfolio_items`、`admin_audits`。唯一约束保证邮箱规范化唯一及每用户/证券唯一。

### 7. 部署与验证

开发环境用 Docker Compose 启动 PostgreSQL，API 由 Maven Wrapper 运行，桌面 Web 由 npm 启动，原生壳由 Tauri CLI 启动。CI 分层：Linux 跑后端/前端单测和 Web 构建；Windows/macOS 跑 Tauri 编译与关键原生烟测。公开发布另外要求代码签名、macOS 公证和真实双平台验收。

## Risks / Trade-offs

- [无授权真实行情无法完成商用实时承诺] → 一期默认 Mock 并全链路显示 DEMO；真实 Provider、合同范围和授权有效期作为上线硬门禁。
- [透明/置顶/老板键跨系统行为不一致] → Rust 侧集中处理窗口状态，保存可恢复状态，Windows/macOS 分平台烟测；快捷键冲突显式提示。
- [Tauri 系统 WebView 导致细微渲染差异] → 使用标准 CSS，避免依赖实验 Web API，锁定最低系统版本。
- [SSE 大规模连接增加 API 压力] → 一期按用户自选批量推送；二期根据连接量引入 Redis/NATS 扇出，不提前引入。
- [JWT 无法即时失效] → 短访问令牌 + 刷新令牌轮换；禁用账号时撤销刷新令牌，并在敏感接口检查账号状态。
- [持仓云存储增加隐私责任] → 最小字段、TLS、加密备份、日志脱敏、管理员隔离、注销删除；二期增加仅本机模式。
- [Mock 行情被误解为真实] → 顶栏、水印、字段 source 和健康状态均显示 DEMO，文档禁止用于投资决策。
- [一期范围仍较大] → 先完成纵向闭环和契约测试，会员/支付/AI 只留接口与规范。

## Migration Plan

1. 初始化 monorepo、锁定 Java/Node/Rust 依赖并建立 CI。
2. 启动 PostgreSQL，应用 Flyway 初始迁移；用开发环境变量显式创建管理员。
3. 部署 API 与 Mock Provider，验证健康、认证、持仓和 SSE。
4. 构建桌面端，验证管理主窗、盯盘窗、托盘和老板键。
5. 获得行情授权后新增 Provider Adapter，在影子环境做字段对账、延迟和故障切换验证，再按用户批次启用。
6. 公开分发前完成隐私/协议/备案评估、Windows 签名、macOS 签名公证和双平台验收。

回滚时桌面端关闭真实 Provider 功能开关回退 DEMO；API 数据库迁移保持向后兼容，应用版本可独立回滚。涉及数据删除的迁移必须先备份并单独审批。

## Open Questions

- 正式行情供应商、授权范围、SLA、并发/终端计费和缓存/衍生计算许可尚待商务与法务确认。
- Windows 代码签名证书和 Apple Developer Team/公证凭据尚待提供。
- 注册一期是否需要邮箱验证、密码找回邮件供应商，需在公开测试前确定；首个可运行版本先实现邮箱+密码。
- 面向境内用户的部署地域、ICP/应用备案和隐私政策主体尚待运营主体确认。
