## Why

当前 AKShare 网关固定优先抓取东财全市场快照、失败后自动切换新浪，缺少可视化配置、Redis 共享缓存和真实联通状态；Java API 还会在真实源故障时回退 DEMO，可能让用户误把模拟价格当作行情。现在需要建立可管理、可观测且不使用演示数据的真实行情链路。

## What Changes

- 新增“实时行情源”配置页面，采用可扩展的 Provider → 模式 → 具体源结构；一期只展示 AKShare 子页面。
- AKShare 支持全市场快照模式：管理员可选择东财或新浪，并以秒为单位设置交易时段刷新频率。
- Java 后端在 A 股交易时段定时调用 AKShare 全市场接口，将标准化快照原子写入 Redis；用户查询只读取 Redis，不触发全市场上游请求。
- AKShare 支持单只股票低延迟模式：可选择东财行情报价或雪球个股行情，由 Java API 代理调用 AKShare 网关，不允许桌面端直接暴露或绕过服务端鉴权。
- 新增系统级配置持久化、管理员更新接口、普通用户只读配置接口、配置修改审计与安全频率下限。
- 主页面新增 AKShare 网关、Redis 快照和当前具体上游的联通状态指示灯，展示最后成功时间、缓存年龄或故障原因。
- **BREAKING**：默认及生产环境禁用 DEMO Provider；真实源不可用时返回明确错误或陈旧真实快照，不再生成模拟行情。

## Capabilities

### New Capabilities

- `market-data-source-management`: 覆盖 AKShare 模式与具体源配置、管理员权限、交易时段 Redis 快照调度、单股代理、联通性状态和禁止 DEMO 回退。

### Modified Capabilities

无。

## Impact

- 桌面端新增行情源配置页面、API 类型与主界面联通灯，并调整错误态文案。
- Spring API 新增行情配置领域、Flyway 表、Redis 依赖、调度器、缓存读取 Provider、管理员接口与状态接口。
- AKShare FastAPI 网关新增显式来源的全市场快照、单股报价、上游探测和标准化逻辑。
- 部署配置新增 Redis 连接参数；云服务器需要可由 Java API 内网访问的 Redis，禁止向公网开放。
- 真实公开网站数据仍受 AKShare 与上游使用条款、限流和许可边界约束，不能替代正式商用授权行情。
