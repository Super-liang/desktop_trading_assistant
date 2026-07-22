# 0.1.3 发布记录（2026-07-22）

## 代码与云端

- 业务提交：`4961a1975baaa4cd309ccdbef8ad0be626a94c74`
- 分支：`main` 与 `codex/akshare-market-source` 均已推送该提交
- 云端 release：`/opt/stock-watch/releases/cloud-0.1.3-20260722T082744Z`
- 部署前 PostgreSQL 备份：`20260722T082859Z`
- `stock-watch-api` 与 `stock-watch-akshare` 均为 active
- Flyway V4 迁移成功，公网 HTTPS 和完整端到端验收通过

## 行情检查

- 新浪全市场接口实测成功，返回约 5527 只证券；已有 Redis 快照为永久缓存（TTL `-1`）。
- 东方财富公开全市场接口在发布时返回 503，健康状态按设计记录为 DOWN；非交易时段没有绕过调度器手工写入缓存，系统保留其他来源最后成功快照。
- 双来源刷新、锁、缓存和失败隔离已由后端测试覆盖；交易时段调度不依赖服务端默认模式。

## 桌面安装包

- GitHub Actions：`29904777420`
- GitHub Release：<https://github.com/Super-liang/desktop_trading_assistant/releases/tag/v0.1.3>
- macOS arm64：DMG 与 APP ZIP
- Windows x64：NSIS setup EXE
- Release 共 5 个资产，包含两个平台的 SHA-256 清单。
- 全部资产从 GitHub 重新下载后通过 SHA-256 校验；Windows 清单使用 LF 换行，支持 macOS/Linux 直接校验。
- macOS 包采用 ad-hoc 签名，未配置 Apple 公证凭证，因此没有完成 notarization。

## 验证摘要

- 本地 Spring：46 项测试，0 失败，3 项条件性跳过。
- 本地桌面：47 项测试通过，Web 构建与 Cargo check 通过。
- AKShare 网关：36 项测试通过。
- 主分支 CI 的 API、Web、macOS 原生与 Windows 原生 job 全部通过。
- 安装包工作流的 macOS、Windows 和 Release job 全部通过。
