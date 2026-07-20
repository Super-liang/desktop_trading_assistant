## Context

现有 Spring Boot 服务通过 `QuoteProvider` SPI 聚合行情，`LicensedHttpQuoteProvider` 已定义 `/v1/search` 与 `/v1/snapshots` 的标准 HTTP 契约，并在调用失败时由 Registry 降级至 DEMO。AKShare 是 Python 库，`stock_zh_a_spot_em()` 与 `stock_zh_a_spot()` 均可返回沪深京全市场快照，字段包含代码、名称、最新价、昨收、开高低、涨跌额、涨跌幅和成交量；它不提供可直接嵌入 Java 的稳定 SDK，也不提供交易所级逐笔推送。

AKShare 官方声明数据接口仅供学术研究、不可商业使用。因此该接入服务面向本地开发、功能联调与非商业评估，不改变正式 ToC 发行必须使用获授权行情源的门禁。

## Goals / Non-Goals

**Goals:**

- 在不改变桌面端和持仓 API 的前提下展示真实 A 股公开行情。
- 复用现有 Provider 优先级与降级机制，并让每条行情明确标识 `AKSHARE`、时间和陈旧状态。
- 用单进程并发安全缓存合并请求，限制 AKShare 上游调用频率。
- 提供无需 Docker 的 macOS 本地启动、健康检查、自动化测试和可回滚配置。
- 对网关接口使用共享密钥鉴权，且不在日志中输出密钥、用户自选或持仓。

**Non-Goals:**

- 不把 AKShare 声明为交易所授权实时行情，不保证毫秒级、逐笔或持续推送。
- 不将 AKShare 用于 ToC 商业发行、收费会员或生产终端分发。
- 不实现历史 K 线、复权、交易、荐股或 AI 分析。
- 不在本期引入 Redis、多进程共享缓存或持久化行情。

## Decisions

### 1. 独立 FastAPI sidecar，复用标准 HTTP Provider

新增 `services/akshare-gateway`，由 Python 进程加载 AKShare，Spring Boot 继续通过现有 `LicensedHttpQuoteProvider` 调用。相比在 Java 中启动 Python 子进程，该方案隔离运行时和故障、便于测试，也能在未来无感替换为获授权供应商。

网关提供：

- `GET /health`：不泄露配置的进程与缓存状态；
- `GET /v1/search?query=...`：从缓存的沪深京全市场表按代码或名称搜索；
- `GET /v1/snapshots?symbols=SSE:600519,SZSE:000001`：按请求顺序返回标准 `Quote[]`；
- `/v1/*` 使用 `X-API-Key` 与环境变量中的共享密钥做恒定时间比较。

### 2. 全市场快照缓存，而不是逐证券重复抓取

网关优先调用东方财富版 `stock_zh_a_spot_em()`，若其网络或接口失败则在同一次刷新中尝试新浪版 `stock_zh_a_spot()`；两者均返回全市场数据并归一化到相同字段。网关按默认 10 秒 TTL 缓存成功结果，并用锁保证并发请求只触发一次刷新。刷新失败时，在可配置的最大陈旧窗口内返回最后成功数据并标记 `stale=true`；两个 AKShare 接口均失败且超过窗口时返回 503，使 Spring Registry 执行既有降级。

该选择降低被上游限流的概率。代价是单进程缓存不能跨实例共享；一期本地运行只启动一个 worker。

### 3. 严格字段归一化与诚实的时间语义

- 用六位代码规则映射 `SSE`、`SZSE`、`BSE`，并保留股票/ETF 资产类型判断。
- 缺失、非数字或无效价格不伪造；请求证券缺失时返回 404/422，使上层明确降级。
- `sourceTimestamp` 使用本次上游抓取完成时间，因为 AKShare 表没有交易所行情时间字段。
- `receivedAt` 使用响应构建时间，`source=AKSHARE`、`demo=false`、`delayed=true`；`delayed=true` 表示无法证明交易所级实时性。
- 市场阶段按 Asia/Shanghai 的工作日和交易时段计算，闭市期间缓存不被描述为盘中持续实时。

### 4. 动态桌面提示

主界面和透明小窗不再固定写“DEMO/演示行情”。它们从实际持仓返回的 `quote.source`、`quote.demo`、`quote.delayed` 和 `quote.stale` 派生提示；混合或降级结果必须逐条保留来源，避免用户把 DEMO 数据误认为真实价格。

### 5. 配置启用与安全回滚

默认仍关闭 HTTP Provider。开发者显式启动 Python 网关并设置相同的 `AKSHARE_API_KEY`/`QUOTE_HTTP_API_KEY` 后才启用。回滚只需将 `QUOTE_HTTP_ENABLED=false` 并重启 Spring Boot，数据库无迁移。

## Risks / Trade-offs

- [AKShare 数据不可商用且接口可能被移除] → 代码、界面和文档明确非商业用途，默认关闭，并保持获授权 Provider 契约。
- [东方财富或新浪上游限流、字段或网络变化] → AKShare 内双公开源切换、短时缓存、单飞刷新、严格解析、陈旧标记、503 与 DEMO 降级，并锁定/测试 AKShare 版本。
- [抓取完成时间不等于交易所撮合时间] → `delayed=true`，不宣称交易所级实时；展示来源与更新时间。
- [全市场抓取耗时高于 SSE 刷新间隔] → 缓存复用；刷新在请求线程内单飞，后续可按观测结果升级后台预取。
- [降级至 DEMO 造成价格语义突变] → 每条行情持续显示来源，界面在 DEMO 时显示醒目提示；不隐藏数据源切换。

## Migration Plan

1. 新增并测试 AKShare 网关，默认不改变现有启动行为。
2. 本地启动网关，使用共享密钥配置 Spring HTTP Provider。
3. 验证搜索、沪深北快照、持仓盈亏、陈旧状态和断网降级。
4. 若异常，将 `QUOTE_HTTP_ENABLED=false` 回滚至 DEMO，无数据库变更。

## Open Questions

- 正式 ToC 发行前需确定具有沪深北 PC 展示、目标地域和终端分发授权的供应商；该决策不阻塞本地 AKShare 验证。
