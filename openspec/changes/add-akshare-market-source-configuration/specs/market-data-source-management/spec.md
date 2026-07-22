## ADDED Requirements

### Requirement: 可扩展行情源配置页面
系统 SHALL 提供按 Provider 分组的实时行情源配置页面，一期 SHALL 展示 AKShare 子页面，并 SHALL 允许后续新增 Provider 而无需改变页面主结构。

#### Scenario: 登录用户查看行情策略
- **WHEN** 任一已登录用户打开行情源配置页面
- **THEN** 页面以只读方式展示当前 Provider、模式、具体源、刷新频率和接口说明

#### Scenario: 管理员修改行情策略
- **WHEN** ADMIN 用户选择模式、具体源和有效刷新频率并保存
- **THEN** 系统持久化系统级配置、记录管理审计并使后端使用新策略

#### Scenario: 普通用户尝试修改配置
- **WHEN** 非 ADMIN 用户调用行情配置更新接口
- **THEN** 系统返回 403 且不改变配置

### Requirement: AKShare 全市场快照模式
系统 SHALL 支持东财 `stock_zh_a_spot_em` 和新浪 `stock_zh_a_spot` 两种全市场快照源，并 SHALL 由后端在 A 股交易时段按管理员配置的秒级间隔抓取。

#### Scenario: 东财快照定时刷新
- **WHEN** 模式为全市场快照、源为东财、当前处于交易时段且刷新间隔已到
- **THEN** 后端调用一次东财全市场接口并将标准化快照原子写入 Redis

#### Scenario: 新浪频率保护
- **WHEN** 管理员选择新浪并设置小于 30 秒的刷新间隔
- **THEN** 系统拒绝配置并说明新浪访问频率限制

#### Scenario: 非交易时段
- **WHEN** 当前不在北京时间工作日 `09:15-11:30` 或 `13:00-15:00`
- **THEN** 调度器不调用全市场上游接口

#### Scenario: 快照刷新失败
- **WHEN** 当前上游抓取、标准化或 Redis 原子写入失败
- **THEN** 系统保留最后成功的真实快照、记录故障状态且不切换到 DEMO 数据

### Requirement: 快照查询只读取 Redis
全市场快照模式下，用户行情查询 SHALL 只从 Redis 当前快照读取指定证券，不得由用户请求触发全市场 AKShare 调用。

#### Scenario: 查询已缓存证券
- **WHEN** 用户请求的证券存在于当前 Redis 快照
- **THEN** API 返回缓存行情及来源、抓取时间和陈旧状态

#### Scenario: Redis 或快照不可用
- **WHEN** Redis 不可连接或尚无有效快照
- **THEN** API 返回明确的服务不可用错误且不返回 DEMO 行情

### Requirement: AKShare 单股低延迟模式
系统 SHALL 支持东财 `stock_bid_ask_em` 与雪球 `stock_individual_spot_xq` 单股源，并 SHALL 通过受鉴权的 Java API 代理访问 AKShare 网关。

#### Scenario: 查询东财单股行情
- **WHEN** 模式为单只股票且具体源为东财，用户查询一组有效 A 股代码
- **THEN** 网关使用东财单股接口返回统一格式的最新行情

#### Scenario: 查询雪球单股行情
- **WHEN** 模式为单只股票且具体源为雪球，用户查询一组有效 A 股代码
- **THEN** 网关使用雪球单股接口返回统一格式的最新行情

#### Scenario: 桌面端尝试绕过后端
- **WHEN** 桌面应用查询单股行情
- **THEN** 请求只发送到 Java API，客户端不持有 AKShare 网关密钥且不直接访问网关或公开站点

### Requirement: 联通性状态可观测
系统 SHALL 暴露 AKShare 网关、Redis 快照和当前具体上游的独立状态，并 SHALL 在主页面用指示灯展示。

#### Scenario: 所有组件正常
- **WHEN** 网关可达、所需 Redis 快照可用且当前上游最近调用成功
- **THEN** 主页面分别显示绿色状态及最后成功时间或缓存年龄

#### Scenario: 单个组件故障
- **WHEN** 网关、Redis或当前上游任一组件故障
- **THEN** 对应指示灯显示降级或故障及安全的错误摘要，其他组件状态保持独立

#### Scenario: 单股模式查看 Redis 状态
- **WHEN** 当前模式为单只股票
- **THEN** Redis 快照状态显示为不适用而不是故障

### Requirement: 禁止 DEMO 行情回退
系统 SHALL 默认禁用 DEMO Provider，并 SHALL 在生产环境拒绝启用 DEMO Provider。

#### Scenario: 真实行情源不可用
- **WHEN** AKShare、选中上游或 Redis 无法提供允许范围内的真实行情
- **THEN** 系统返回陈旧标记的最后真实行情或服务不可用错误，不得生成模拟价格

#### Scenario: 生产配置启用 DEMO
- **WHEN** 生产环境配置 `demo-enabled=true`
- **THEN** 应用启动失败并记录不含敏感信息的配置错误
