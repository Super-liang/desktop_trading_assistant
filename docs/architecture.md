# 架构说明

项目采用模块化单体服务端和跨平台原生桌面壳：

```text
Tauri 2（透明窗/老板键/托盘）
  └─ React + TypeScript（账户、持仓、行情、管理后台）
      └─ HTTPS/SSE
          └─ Spring Boot 3 / Java 17
              ├─ auth / admin
              ├─ portfolio
              └─ quote gateway → QuoteProvider（默认 DEMO）
                    ├─ AKShare FastAPI sidecar（仅本地非商业研究）
                    └─ 获授权 HTTP/SDK Provider（ToC 生产）
                  └─ PostgreSQL
```

一期不接交易，不采集券商账号，不使用网页私有行情接口。生产行情适配器必须在取得沪深 PC 展示和终端分发授权后接入。

生产适配器 `LicensedHttpQuoteProvider` 可通过环境变量启用。它把供应商 SDK、专线或合规聚合服务隔离在一个标准 HTTP 桥接契约后：

- `GET /v1/search?query=600519` → `InstrumentSearchResult[]`
- `GET /v1/snapshots?symbols=SSE:600519,SZSE:000001` → `Quote[]`
- 请求头：`X-API-Key`

配置 `QUOTE_HTTP_ENABLED=true`、`QUOTE_HTTP_BASE_URL`、`QUOTE_HTTP_API_KEY` 后，该源按优先级先于 DEMO 源调用；超时或异常时 Registry 会在单次调用内降级。启用适配器并不自动取得数据授权，发行方仍需确认沪深北 PC 展示、地域与终端分发权利。

## AKShare 研究网关

`services/akshare-gateway` 使用 FastAPI 封装 AKShare，优先调用 `ak.stock_zh_a_spot_em()`，失败时自动切换到 `ak.stock_zh_a_spot()`，并输出上述 HTTP 契约。由于两个接口都一次返回沪深京全市场数据，网关采用进程内单飞缓存：默认 TTL 为 10 秒，刷新失败时最多复用 30 秒的最后成功快照并设置 `stale=true`，随后返回 503 触发上层降级。

所有 `/v1/*` 请求使用 `X-API-Key`，健康检查仅返回进程、来源和缓存状态。网关不记录密钥、用户自选、持仓或成本。其 `sourceTimestamp` 是上游抓取完成时间而非交易所撮合时间，因此统一设置 `delayed=true`，桌面端显示“公开延迟行情”。

AKShare 官方声明接口与数据仅用于学术研究、不可商业使用。该网关默认不启用，只用于本地开发和非商业验证；正式发行仍必须使用获得 PC 展示、地域、缓存和下游终端分发授权的数据源。
