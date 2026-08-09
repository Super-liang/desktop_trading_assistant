## Context

当前系统以 `SSE|SZSE|BSE:六位代码` 表示证券，证券目录每天北京时间 08:00 同步一次，行情调度只识别 A 股固定交易时段。持仓只有数量和成本；0.1.5 的参考收益依赖有限的日结记录，无法支持可信年化收益。AKShare 网关已经具备来源隔离、超时进程、健康状态和 Redis 最后成功行情，可作为跨市场适配基础。

AKShare 1.18.72 已包含 `stock_zh_a_spot_em`、`stock_hk_spot_em`、`stock_us_spot_em`、`fund_name_em`、`fund_open_fund_daily_em` 与 `stock_zh_index_spot_em`。其中港股东财接口文档明确约有 15 分钟延迟，美股新浪接口也有约 15 分钟延迟；开放式基金当日净值通常在交易日 16:00–23:00 才陆续披露。AKShare 的 `tool_trade_date_hist_sina` 只适合 A 股日期参考，不能承担三地市场日历和美股提前收市判断。

## Goals / Non-Goals

**Goals:**

- 用统一市场日历驱动 A/H/美行情、目录刷新、市场状态和收益基线。
- 在不破坏既有 A 股持仓的前提下支持港股、美股和场外开放式公募基金。
- 以可解释、可测试的开盘基线计算日收益，以成本和当前价/净值计算持有收益。
- 复用现有行情设置、Redis 缓存、健康检查和失败回退框架，同时明确每个市场真实可用的来源能力。
- 确保跨时区、夏令时、午间休市、节假日和提前收市不会触发错误刷新。

**Non-Goals:**

- 0.1.6 不接券商、不记录逐笔交易、不计算已实现收益、现金流、分红、手续费或税费。
- 不做港币/美元兑人民币换算，不跨币种汇总金额或收益率。
- 不支持美股盘前/盘后行情；只按常规交易时段显示市场状态和刷新。
- 不把 ETF、LOF 等交易所挂牌基金放入“公募基金”菜单；它们仍按所在交易所证券处理。
- 公募基金首期只支持开放式基金单位净值，不支持货币基金万份收益、累计净值展示、封闭式基金以及分红/拆分调整。
- 不保证 AKShare 或公开网站行情达到商业实时数据 SLA。
- 不删除 V5 历史收益数据；仅停止对外展示年度和年化指标。

## Decisions

### 1. 使用显式市场、交易所、币种和稳定证券标识

新增枚举：

| 市场 | 交易所/类型 | 规范证券标识示例 | 币种 | 时区 |
| --- | --- | --- | --- | --- |
| `A_SHARE` | `SSE` / `SZSE` / `BSE` | `SSE:600000` | CNY | `Asia/Shanghai` |
| `HK_STOCK` | `HKEX` | `HKEX:00700` | HKD | `Asia/Hong_Kong` |
| `US_STOCK` | `NASDAQ` / `NYSE` / `AMEX` | `NASDAQ:AAPL` | USD | `America/New_York` |
| `PUBLIC_FUND` | `CN_FUND` | `CN_FUND:000001` | CNY | `Asia/Shanghai` |

既有 A 股标识保持不变。美股目录同时保存 AKShare/东财原始代码（如 `105.AAPL`）作为 `provider_symbol`，由适配器映射到规范交易所；映射不明确的记录不进入可选择目录，避免同代码跨交易所碰撞。证券目录使用 `instrument_id` 主键，并新增 `market`、`exchange`、`asset_type`、`currency`、`provider_symbol`、`active`、`source_updated_at`。

持仓新增 `market`、`currency`、`opened_on`。创建/更新接口不信任客户端传入的交易所、币种和名称，而是用所选市场和 `instrument_id` 回查证券目录。为兼容 0.1.5 客户端，缺失 `market` 时只对合法的旧 A 股标识推断为 `A_SHARE`；0.1.7 再考虑移除该兼容路径。

### 2. 交易日历落库并采用可替换 Provider

在 AKShare 网关增加固定版本的 `exchange_calendars`，使用 `XSHG`、`XHKG`、`XNYS` 生成未来 400 天和过去 30 天的标准交易会话；A 股日历额外与 `tool_trade_date_hist_sina` 可用区间交叉检查。Spring 只消费规范化后的会话，不在 Java 中复制节假日算法。

`market_sessions` 至少保存：`market`、`trading_date`、`timezone`、`open_at`、可空的 `break_start_at/break_end_at`、`close_at`、`early_close`、`source`、`synced_at`，主键为 `(market, trading_date)`。人工修正规则优先级高于 Provider，同步时不得覆盖人工记录。

市场阶段统一为：

- `PRE_OPEN`：交易日开盘前一小时以内；
- `OPEN`：常规连续交易时段；
- `BREAK`：A 股/港股午间休市；
- `CLOSED`：交易日尚未进入预开盘或已经收盘；
- `HOLIDAY`：已有覆盖且当天不是交易日；
- `UNKNOWN`：日历缺失或已超出覆盖范围。

`UNKNOWN` 时停止主动上游刷新并保留最后成功数据，页面明确显示“交易日历不可用”，禁止用工作日猜测。选择 `exchange_calendars` 而不是只用 AKShare 日历，是因为它同时支持三地市场、时区、午休与美股提前收市；代价是需要固定版本、定期升级并保留人工覆盖能力。

### 3. 用市场与能力矩阵复用行情源配置

配置不再只有一条全局 `snapshot_source/single_source`，而是按 `market + capability` 保存。0.1.6 首批能力矩阵：

| 市场 | 能力 | 首选 AKShare 接口 | 可选/降级来源 | 刷新类型 |
| --- | --- | --- | --- | --- |
| A 股 | 全市场股票 | `stock_zh_a_spot_em` | `stock_zh_a_spot` | 交易时段服务端快照 |
| A 股 | 单股 | 现有东财/雪球接口 | 现有来源隔离回退 | 客户端频率触发 |
| A 股 | 指数概览 | `stock_zh_index_spot_em` | `stock_zh_index_spot_sina` | 交易时段服务端快照 |
| 港股 | 全市场股票 | `stock_hk_spot_em` | 暂无默认第二来源 | 交易时段服务端快照，标注约 15 分钟延迟 |
| 美股 | 全市场股票 | `stock_us_spot_em` | `stock_us_spot`，标注约 15 分钟延迟 | 常规交易时段服务端快照 |
| 公募基金 | 目录 | `fund_name_em` | 保留上次目录 | 每日 07:00 |
| 公募基金 | 单位净值 | `fund_open_fund_daily_em` | 持仓基金可用 `fund_open_fund_info_em` 补查 | 每日 07:00，按净值日期幂等 |

港股和美股不伪装成现有 A 股“单股低延迟模式”。首版从各自全市场快照中按持仓代码读取，减少对公开接口逐股请求；未来若新增可靠单股接口，再通过同一 capability SPI 开放用户频率选择。配置页面复用 Provider 卡片、管理员刷新频率和普通用户来源选择交互，但只展示该市场真实支持的选项。

所有 Redis key 必须包含 `market`、`capability` 和 `source`。成功刷新只覆盖对应命名空间；失败不删除、不设置主动 TTL，并返回 `lastSuccessAt`、`quoteAsOf`、`stale`、`delayNotice`。

### 4. 目录和行情调度完全由市场会话驱动

- 交易日历每天 02:30（Asia/Shanghai）补齐滚动窗口，并在覆盖不足 30 天时报警。
- A 股、港股、美股目录在各自下一次 `open_at - 1h` 执行；美股时间必须由 `America/New_York` 转换，禁止写死北京时间。任务以 `(market, trading_date, job_type)` 幂等，失败后在开盘前每 10 分钟重试，开盘后只保留旧目录并告警。
- 公募基金目录和净值每天北京时间 07:00 执行；读取接口返回的净值日期，重复日期只更新元数据，不制造新的行情日期。
- 全市场行情只在 `OPEN` 刷新；`BREAK`、`CLOSED`、`HOLIDAY` 停止调用并保留最后成功快照。开盘后立即触发一次，不等待完整间隔。
- 市场调度使用有界线程池，不让 A 股和港股同一北京时间的任务串行阻塞；同一市场/来源仍需分布式锁防止重复抓取。

### 5. 用开盘持仓基线计算股票日收益

新增 `position_daily_baselines`：`position_id`、`user_id`、`market`、`trading_date`、`opening_quantity`、`opening_price`、`currency`、`quote_source`、`captured_at`、`status`，主键 `(position_id, trading_date)`。

在每个股票市场进入 `OPEN` 后，以首个包含有效“今开”的行情为该交易日开盘价，并复制当时持仓数量。计算规则：

```text
股票日收益 = opening_quantity × (current_price - opening_price)
股票日收益率 = (current_price - opening_price) / opening_price
持有收益 = current_quantity × (current_price - cost_price)
持有收益率 = (current_price - cost_price) / cost_price
```

持仓在当日开盘后新建，或数量/成本/建仓日期被编辑时，该持仓当日 `dailyStatus=UNAVAILABLE`，从下一交易日重新建立标准基线；这比用当前数量套用开盘涨跌更诚实。删除持仓不会伪造当日已实现收益，因为系统没有交易流水。聚合状态沿用 `COMPLETE/PARTIAL/UNAVAILABLE`，但按市场和币种返回，禁止 CNY、HKD、USD 相加。

公募基金没有日内开盘价：

```text
基金净值日收益 = quantity × (latest_nav - previous_nav)
基金净值日收益率 = (latest_nav - previous_nav) / previous_nav
基金持有收益 = quantity × (latest_nav - cost_nav)
基金持有收益率 = (latest_nav - cost_nav) / cost_nav
```

页面必须同时展示 `navDate`。若最新/前一净值缺失或建仓日期不早于最新净值日期，净值日收益为不可用，但持有收益仍可按成本计算。

年度收益、年度收益率和年化收益率从 UI 与新 DTO 移除。为旧客户端过渡，0.1.6 旧 performance 端点可暂时返回对应字段为 `null` 并标记弃用；不得继续计算或展示。

### 6. 首页只展示可横向浏览的指数概览和分币种收益

“沪深京 A 股实时行情”解释为首页固定的七张指数卡，顺序为：上证指数 `SSE_INDEX:000001`、深证成指 `SZSE_INDEX:399001`、创业板指 `SZSE_INDEX:399006`、北证 50 `BSE_INDEX:899050`、科创综指 `SSE_INDEX:000680`、科创 50 `SSE_INDEX:000688`、沪深 300 `CSI_INDEX:000300`。其中“创业板指数”统一使用官方简称“创业板指”。首屏默认完整展示前三张，其余通过横向滑动、触控板滚动或左右控制按钮访问；窄屏支持触摸滑动和卡片吸附，键盘用户可操作左右按钮。

网关优先使用 AKShare `stock_zh_index_spot_em`，并行读取“沪深重要指数”“上证系列指数”“深证系列指数”“中证系列指数”等所需分组，按带市场的规范指数标识合并、去重和筛选；`stock_zh_index_spot_sina` 仅作为已通过代码契约测试后的降级来源。任何一个目标指数缺失时，仅对应卡片显示不可用和最后成功时间，不按名称模糊匹配、不以其他指数冒名替代。首页不返回或渲染个股列表，全市场股票快照只服务于持仓行情与目录等后台能力。

首页收益按市场/币种分组展示日收益和持有收益。若用户有多币种持仓，显示多张卡而非“总收益”。“联通检测”按钮固定在右上角，默认收起；按钮保留总体状态和异常数量徽标，展开后显示 Spring API、Redis、AKShare 及各市场来源明细，再次点击或按 Escape 收起。严重断连不能因面板收起而完全不可见。

### 7. API 按市场显式分区

主要接口调整：

- `GET /api/v1/markets/status`：返回各市场阶段、下一开/收盘时间、日历来源和覆盖状态。
- `GET /api/v1/instruments/search?market=...&query=...`：市场必填，结果包含交易所、币种、资产类型和目录更新时间。
- `GET /api/v1/portfolio?market=...`：按市场读取；创建/更新增加 `market`、`openedOn`。
- `GET /api/v1/me/returns`：按市场和币种返回日收益、持有收益及数据状态，替代首页对旧 performance DTO 的使用。
- `GET /api/v1/market-overview/a-share`：只返回配置的沪深京指数卡。
- 行情状态和管理员来源配置接口增加 `market/capability` 维度。

管理员按用户查看持仓时继续遵守 0.1.5 隐私边界，不返回数量和成本；允许返回市场、币种、证券、建仓日期以及按市场聚合的收益率，但不跨币种合计金额。

### 8. 数据迁移与兼容策略

Flyway V6 采用只增不删：

1. 创建交易日历、同步运行和每日基线表。
2. 扩展证券目录与持仓字段；将现有目录/持仓按证券标识回填 `A_SHARE`、交易所和 CNY，将 `opened_on` 回填为持仓 `created_at` 的 Asia/Shanghai 日期。
3. 为新列增加约束前先执行完整数据校验；无法识别的旧标识使迁移失败，不静默猜测。
4. 保留 V5 收益表和旧字段，0.1.6 只停止读取年度/年化指标。

部署顺序为数据库备份 → 兼容后端/网关 → Web/桌面 0.1.6。后端必须继续接受 0.1.5 A 股请求至少一个版本。回滚应用时 V6 新表/列保留，不执行逆向 DROP；旧 0.1.5 应用忽略新增结构。

## Risks / Trade-offs

- [公开行情并非交易所级实时，港股/新浪美股约有 15 分钟延迟] → UI 展示来源、行情时间和延迟提示，文案使用“行情”或“延迟行情”，不承诺实时 SLA。
- [三地节假日或临时休市变化] → 日历落库、滚动同步、人工覆盖、覆盖告警；`UNKNOWN` 时停止主动刷新而非猜测。
- [美股夏令时和提前收市导致固定北京时间错误] → 所有会话存 UTC 瞬时值并由 `America/New_York` 日历生成，测试 DST 切换和 13:00 提前收市。
- [港股/美股全市场快照数据量大、调用慢] → 服务端共享快照、按市场有界并发、永久最后成功缓存，客户端不逐股直连 AKShare。
- [美股东财代码到交易所映射不完整] → 目录保存原始 provider symbol，只有已验证映射进入可选目录，异常记录计数并告警。
- [只记录当前持仓无法准确反映日内买卖] → 当日新增/编辑持仓日收益标记不可用；完整现金流/交易流水留给后续版本。
- [跨币种收益被误加] → API 和 UI 强制按市场/币种分组，0.1.6 不提供总金额。
- [基金净值延迟或分批披露] → 以净值日期幂等，07:00 保留最后已披露值并标记日期，不把自然日当作净值日。
- [升级 AKShare/交易日历依赖引发接口漂移] → 固定版本，针对列名、代码和样例数据做契约测试；升级必须先通过隔离网关测试。

## Migration Plan

1. 先实现日历、市场枚举和 V6 迁移，验证旧 A 股持仓无行为变化。
2. 扩展 AKShare 网关与 Redis 命名空间，完成 A/H/美/基金/指数契约测试和失败回退。
3. 实现目录、行情和基线调度，使用可控时钟验证三地时区、午休、DST、节假日及提前收市。
4. 扩展 API，再实现桌面端市场选择、四类持仓页、收益卡与联通面板。
5. 用本地 PostgreSQL/Redis 做完整迁移与端到端验证；生产发布前执行 PostgreSQL 备份和恢复演练。
6. 先部署兼容后端，再发布 0.1.6 客户端；观察一轮 A/H/美开收盘和次日 07:00 基金任务。

## Open Questions

- 无。首页指数清单、公募基金首期范围和空市场菜单展示方式均已确认。
