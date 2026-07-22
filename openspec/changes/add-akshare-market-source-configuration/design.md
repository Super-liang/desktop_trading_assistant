## Context

系统当前由 Tauri/React 桌面端、Spring Boot API 和 FastAPI AKShare 网关组成。AKShare 网关用进程内 TTL 缓存懒加载全市场数据，固定优先东财并在失败时切新浪；Spring `LICENSED_HTTP` Provider 每次用户查询都调用网关，并在失败后降级到始终可用的 DEMO Provider。该结构无法让管理员控制模式与源，也无法在多 API 实例间共享快照。

AKShare 文档中，`stock_zh_a_spot_em` 与 `stock_zh_a_spot` 返回沪深京全市场快照；新浪明确提示高频重复调用会临时封禁 IP。单股可使用东财 `stock_bid_ask_em` 和雪球 `stock_individual_spot_xq`。AKShare 与这些公开站点不提供本项目的商用授权保证，所有界面必须继续披露数据来源与许可边界。

## Goals / Non-Goals

**Goals:**

- 提供可扩展到其他 Provider 的系统级行情源配置页面，一期实现 AKShare。
- 支持全市场快照和单股低延迟两种模式及具体上游选择。
- 全市场模式只由后端在交易时段抓取，并通过 Redis 为所有用户共享。
- 仅管理员可修改配置与秒级刷新频率，所有登录用户可查看当前策略和联通状态。
- 禁止生产与默认配置回退到 DEMO 数据。
- 提供网关、Redis、选中上游的可解释健康状态。

**Non-Goals:**

- 不把 AKShare 描述为授权商用实时行情。
- 不实现用户级不同源偏好；本期配置是系统级，避免重复抓取全市场。
- 不让桌面端直接访问 FastAPI 或公开数据站点。
- 不在本期接入除 AKShare 之外的 Provider。
- 不处理法定节假日交易日历；本期按北京时间工作日和盘中时段调度，节假日空数据/闭市数据由状态和陈旧标记反映。

## Decisions

### 1. 配置是系统级单例并由 PostgreSQL 持久化

新增 `market_data_config` 单例表，保存 `provider=AKSHARE`、模式、快照源、单股源、刷新秒数和更新时间。`GET /api/v1/market-data/config` 对登录用户只读；`PUT /api/v1/admin/market-data/config` 仅管理员可写并生成审计记录。相比本地前端配置，系统级配置能保证调度器、缓存与所有 API 实例使用同一策略。

### 2. 快照模式由 Java 调度、FastAPI 抓取、Redis 提供查询

Java 每秒检查一次是否达到数据库中的动态刷新间隔，只在北京时间工作日 `09:15-11:30`、`13:00-15:00` 触发。它调用网关的显式来源接口 `GET /v1/market/snapshot?source=EASTMONEY|SINA`，将标准化行情写入 Redis 临时 Hash，再用 `RENAME` 原子替换正式 Hash，并写入元数据。用户查询执行 Redis `HMGET`，不会触发全市场抓取。

东财刷新间隔允许 5–300 秒；新浪因文档的封 IP 提示限制为 30–300 秒。调度失败时保留最后成功的真实快照并标记陈旧，不清空缓存、不切换到未配置源。

### 3. 单股模式仍通过受控服务端代理

桌面端继续只访问 Spring API。Spring 根据系统配置调用 FastAPI 单股批量契约，FastAPI 内部分别使用东财 `stock_bid_ask_em` 或雪球 `stock_individual_spot_xq`，标准化为统一 Quote。网关对同一 `source+symbol` 做 1 秒请求合并/短缓存，并限制单次最多 50 只；这保留低延迟，同时避免暴露 AKShare 服务密钥、绕过鉴权、CORS 差异和客户端格式耦合。

### 4. Provider 健康状态由真实调用结果驱动

网关记录每个具体上游的最后尝试、最后成功、延迟和脱敏错误类型。Java 状态接口组合：AKShare 网关健康、Redis PING/快照年龄、当前具体源状态。状态枚举为 `UP`、`DEGRADED`、`DOWN`、`UNKNOWN`、`NOT_APPLICABLE`。主页面轮询并显示独立指示灯，不用“Provider Bean 已创建”代替连通性。

### 5. DEMO Provider 默认关闭并禁止生产启用

`MockQuoteProvider` 改为仅在显式 `app.quotes.demo-enabled=true` 时注册；默认 false，生产配置验证器拒绝 true。真实源失败时 API 返回明确的 503 Problem Detail，已有陈旧快照只在允许的最大陈旧窗口内返回并带 `stale=true`。

### 6. 配置页面按 Provider 子页面扩展

桌面端新增“行情源配置”入口和页面。左侧 Provider 列表一期只有 AKShare，右侧展示模式卡片、具体源单选、刷新频率、接口说明和状态。普通用户看到只读内容；管理员获得保存和立即检测按钮。后续新增 Provider 时扩展 DTO 的 provider descriptors，不改页面整体路由。

## Risks / Trade-offs

- [新浪高频访问导致 IP 临时封禁] → 新浪最小间隔 30 秒、无隐式重试风暴、展示最后失败状态。
- [Redis 不可用导致快照模式无行情] → 保留清晰 DOWN 状态并返回 503；不绕过 Redis直接打上游，避免用户流量放大。
- [多 API 实例重复调度] → 使用 Redis 分布式锁，只有获得锁的实例执行一次刷新。
- [全市场响应和 Redis 写入较大] → 网关只返回必要字段，Redis 使用 Hash 与原子 key swap，用户查询使用 HMGET。
- [单股模式持仓较多时上游压力增大] → 最多 50 只、受控并发、1 秒请求合并，并对失败做限流而非 DEMO 降级。
- [节假日仍按工作日触发] → 失败/空数据不覆盖最后成功快照；后续接入交易所日历。
- [公开源许可不满足 ToC 商用] → UI 和文档持续披露，正式商业上线前替换为合同授权 Provider。

## Migration Plan

1. Flyway 新增配置表和默认 AKShare/快照/东财/10 秒记录。
2. 部署内网 Redis，设置密码或 ACL，只允许 Java API 访问，配置 `REDIS_HOST/PORT/PASSWORD`。
3. 先升级 AKShare 网关，验证显式快照源和单股源接口。
4. 再升级 Java API；初始快照成功前 UI 显示缓存未就绪且不返回 DEMO。
5. 最后升级桌面/Web 前端，验证管理员配置、普通用户只读与主页面指示灯。
6. 回滚时恢复旧 API/网关版本；数据库新表可保留。若回滚到 DEMO 行为需显式配置且不得在生产启用。

## Open Questions

- 正式商用前选择哪家具有 PC 展示与再分发授权的行情供应商？
- 后续是否引入交易所交易日历，以完全跳过法定节假日？
- 是否需要为多租户或会员提供用户级行情策略；若需要，应先评估上游并发与授权计费模型。
