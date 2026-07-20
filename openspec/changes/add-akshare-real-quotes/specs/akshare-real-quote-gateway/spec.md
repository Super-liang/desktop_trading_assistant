## ADDED Requirements

### Requirement: AKShare 标准行情网关
系统 SHALL 提供独立 AKShare HTTP 网关，把沪深京 A 股公开行情转换为现有证券搜索和批量快照契约；每条成功行情 MUST 包含规范证券标识、价格字段、市场阶段、来源时间、接收时间、延迟、陈旧、数据源和演示标志。

#### Scenario: 查询真实 A 股快照
- **WHEN** 已启用网关的用户查询 AKShare 当前返回的有效 A 股代码
- **THEN** 系统返回 `source=AKSHARE`、`demo=false` 的标准行情，且价格和涨跌字段来自同一份上游快照

#### Scenario: 搜索股票
- **WHEN** 用户按六位代码或中文名称搜索证券
- **THEN** 网关返回匹配的沪深京证券及规范交易所标识，结果数量受配置上限约束

#### Scenario: 请求不存在的证券
- **WHEN** 批量快照包含当前缓存中不存在或字段无效的证券
- **THEN** 网关返回明确的非成功响应且不生成虚假价格

### Requirement: 限频缓存与陈旧处理
网关 SHALL 缓存全市场快照并合并并发刷新，在缓存 TTL 内 MUST NOT 为每个用户或证券重复调用 AKShare；刷新失败时仅可在最大陈旧窗口内返回最后成功数据并标记陈旧，超过窗口 MUST 返回服务不可用。

#### Scenario: TTL 内并发查询
- **WHEN** 多个请求在缓存 TTL 内查询不同证券
- **THEN** 所有请求复用同一上游快照且不重复调用 `stock_zh_a_spot_em`

#### Scenario: 刷新失败但存在可用旧快照
- **WHEN** 上游刷新失败且最后成功快照未超过最大陈旧窗口
- **THEN** 网关返回旧快照并设置 `stale=true`

#### Scenario: 上游长期不可用
- **WHEN** 上游刷新失败且没有快照或快照已超过最大陈旧窗口
- **THEN** 网关返回服务不可用，使 Spring 行情 Registry 尝试下一 Provider

### Requirement: 网关访问控制与隐私
所有 `/v1/*` 行情接口 SHALL 校验 `X-API-Key` 共享密钥；密钥、用户自选、持仓和成本 MUST NOT 写入网关日志或健康响应。

#### Scenario: 密钥缺失或错误
- **WHEN** 客户端使用缺失或错误的 `X-API-Key` 请求行情接口
- **THEN** 网关返回 401 且响应和日志不包含正确密钥

### Requirement: 非商业使用与来源可见
AKShare Provider SHALL 默认关闭，并 SHALL 在配置与文档中标明仅用于学术研究和本地非商业评估；桌面端 SHALL 根据实际行情动态显示 `AKSHARE`、延迟/陈旧或 `DEMO` 来源，不得把 AKShare 描述为已授权交易所实时行情。

#### Scenario: 默认启动
- **WHEN** 开发者未配置 AKShare 网关和 HTTP Provider
- **THEN** 系统继续使用并明确显示 DEMO 行情

#### Scenario: 启用 AKShare
- **WHEN** 网关和 Spring HTTP Provider 使用匹配密钥成功启动
- **THEN** 桌面端展示实际 `AKSHARE` 来源、更新时间和非投资建议提示

#### Scenario: AKShare 调用降级
- **WHEN** AKShare 网关不可用且 Registry 使用 DEMO Provider 返回行情
- **THEN** 桌面端显示 `DEMO` 来源，不把降级数据标为真实行情

### Requirement: 无 Docker 本地运行
项目 SHALL 提供 macOS 上使用 Python 虚拟环境启动 AKShare 网关、使用 Java 17 启动 API 和配置共享密钥的可执行文档，并提供健康检查与自动化测试命令。

#### Scenario: macOS 本地启用真实行情
- **WHEN** 开发者按 README 在受支持的 Python 与 Java 环境执行命令
- **THEN** AKShare 网关健康检查成功，API Provider 状态包含已启用 HTTP 源，桌面端能够查询真实 A 股公开行情
