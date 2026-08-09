## ADDED Requirements

### Requirement: 系统维护三地市场交易会话
系统 SHALL 为 A 股、港股和美股保存包含本地交易日、时区、开盘、午休、收盘、提前收市、来源和同步时间的交易会话，并 SHALL 保持至少未来 400 天和过去 30 天的覆盖范围。

#### Scenario: 同步三地日历
- **WHEN** 日历同步任务正常完成
- **THEN** 数据库中存在 `A_SHARE`、`HK_STOCK`、`US_STOCK` 的滚动会话，且每个时间点均可转换为 UTC 瞬时值

#### Scenario: 人工修正规则优先
- **WHEN** Provider 同步到与人工修正记录相同的市场和交易日
- **THEN** 系统保留人工修正的会话，不用 Provider 数据覆盖

### Requirement: 系统基于日历计算市场阶段
系统 SHALL 返回 `PRE_OPEN`、`OPEN`、`BREAK`、`CLOSED`、`HOLIDAY` 或 `UNKNOWN`，并 SHALL 同时返回下一次开盘/收盘时间和日历数据状态。

#### Scenario: A 股午间休市
- **WHEN** 当前时间处于 A 股交易日午间休市区间
- **THEN** A 股状态为 `BREAK` 且不被误判为 `OPEN`

#### Scenario: 美股夏令时
- **WHEN** 美国进入或退出夏令时
- **THEN** 美股常规开收盘的 UTC/北京时间随 `America/New_York` 正确变化，而不是使用固定北京时间

#### Scenario: 美股提前收市
- **WHEN** 交易日历标记美股当日提前收市
- **THEN** 到达该日实际收盘时间后状态切换为 `CLOSED`

#### Scenario: 日历覆盖缺失
- **WHEN** 当前日期超出该市场已同步覆盖范围
- **THEN** 市场状态为 `UNKNOWN`，响应明确指出日历不可用

### Requirement: 主动刷新由市场阶段控制
系统 MUST 只在适用市场的 `OPEN` 阶段主动刷新股票行情，并 SHALL 在 `BREAK`、`CLOSED`、`HOLIDAY` 或 `UNKNOWN` 时保留最后成功行情且停止调用上游。

#### Scenario: 非交易时间保留行情
- **WHEN** 市场收盘后用户读取持仓
- **THEN** 系统返回最后成功行情及其时间，不清空 Redis 数据

#### Scenario: 日历不可用时安全降级
- **WHEN** 市场状态为 `UNKNOWN`
- **THEN** 调度器不按工作日猜测刷新，并产生可观测告警
