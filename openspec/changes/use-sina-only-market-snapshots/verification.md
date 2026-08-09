# 验证记录

验证日期：2026-08-09（周日，Asia/Shanghai）

## 自动化验证

- AKShare 网关：`PYTHONPATH=. ./.venv/bin/pytest -q`，105 passed、1 skipped。
- Java API：Java 17 执行 `./mvnw -q test`，退出码 0；包含调度、配置迁移、跨市场读取、Redis 保留和 Flyway 测试。
- Web：`npm --workspace apps/desktop test`，76 passed；`VITE_API_URL='' npm --workspace apps/desktop run build` 成功。
- 发布构建：`deploy/build-release.sh 0.1.6-sina-only-20260809T010900Z` 成功，SHA-256 校验通过。

## 本地集成

- Apple container 中 PostgreSQL 16 与 Redis 7 正常监听本机回环地址。
- AKShare 网关、Spring API、Vite 已在可见 Terminal 窗口启动，健康检查均返回 HTTP 200。
- 本地 A 股新浪全量 5538 条，约 16.6 秒；美股两只批量约 0.06 秒。
- 本机访问新浪港股分页接口当次返回 HTTP 456；没有回退东财，云端出口随后验证成功。

## 腾讯云生产验收

- 当前 release：`0.1.6-sina-only-20260809T010900Z`；回滚备份：`20260808T192651Z`。
- 能力列表：仅 `A_SHARE:SNAPSHOT:SINA`、`HK_STOCK:SNAPSHOT:SINA` 和 `US_STOCK:POSITION:SINA`。
- A 股新浪：HTTP 200，5534 条，21 秒（生产上游硬截止由 30 秒调为 50 秒）。
- 港股新浪：HTTP 200，2798 条，3.82 秒。
- 美股持仓批量：HTTP 200，2 条，0.98 秒。
- 数据库系统配置为 `SINA`；A 股 Redis 快照 TTL 为 -1。
- HTTPS、注册登录、管理后台、真实行情、持仓盈亏、Nginx、证书、备份与日志密钥扫描全部通过服务器 `verify.sh`。

## 未覆盖场景

- 验证日为周日，未观察 A 股、港股和美股在真实开盘时段由调度器自动触发。
- 港股、美股 Redis 尚未首次生成，因此未人为绕过交易日历写入；首次开盘成功后应检查两键 TTL=-1 和最后成功时间。
