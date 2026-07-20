## Why

当前桌面端只能展示可复现的 DEMO 行情，无法验证真实 A 股价格、搜索、盈亏刷新和数据源降级链路。需要增加一个可在本地开发与非商业评估环境启用的 AKShare 行情源，同时明确其数据许可边界，避免把研究用途数据误用于 ToC 正式发行。

## What Changes

- 新增独立的 Python AKShare HTTP 网关，提供与现有行情桥接器兼容的证券搜索、批量快照和健康检查接口。
- 使用 AKShare 沪深京 A 股实时行情构建短时内存缓存，避免每个用户和每次 SSE 刷新都直接请求上游。
- 通过 `QUOTE_HTTP_*` 环境变量把 Spring Boot 行情网关接入 AKShare，并保留失败时降级至 DEMO Provider 的行为。
- 标准化 AKShare 中文字段、交易所代码、时间戳、市场阶段、陈旧和数据源标识，桌面端按实际数据源展示真实/演示提示。
- 提供 macOS 无 Docker 本地启动方式、环境变量样例、依赖锁定和测试。
- 明确 AKShare 仅用于学术研究和本地非商业评估；ToC 商用发行仍须替换为具有 PC 展示及终端分发授权的行情源。

## Capabilities

### New Capabilities

- `akshare-real-quote-gateway`: 覆盖 AKShare 行情采集、缓存、标准 HTTP 契约、鉴权、降级、来源展示和非商业使用门禁。

### Modified Capabilities

无。

## Impact

- 新增 `services/akshare-gateway` Python 服务及其测试、依赖和启动配置。
- 复用 `services/api` 的 `LicensedHttpQuoteProvider`、`QuoteProviderRegistry` 和 `QUOTE_HTTP_*` 配置。
- 调整桌面端行情来源提示，不再固定显示 DEMO 文案。
- 更新 `.env.example`、`README.md`、架构与合规文档；不改变持仓、认证和对外桌面 API。
