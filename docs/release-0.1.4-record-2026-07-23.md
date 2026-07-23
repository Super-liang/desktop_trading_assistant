# 0.1.4 发布记录（2026-07-23）

## 发布结果

- 源码提交：`f39360c68d9c2afca91437593cea9ecdd4cb6d93`
- 源码分支：`codex/akshare-market-source` 与 `main`
- Git 标签：`v0.1.4`
- 云端 release：`/opt/stock-watch/releases/cloud-0.1.4-20260723T010343Z`
- 发布前数据库备份：`/var/backups/stock-watch/trading-20260723T010535Z.dump`
- 私有 Release：<https://github.com/Super-liang/desktop_trading_assistant/releases/tag/v0.1.4>
- 公开 Release：<https://github.com/Super-liang/stock-trading-assistant-releases/releases/tag/v0.1.4>
- GitHub Actions：<https://github.com/Super-liang/desktop_trading_assistant/actions/runs/29970985475>

## 修复内容

- 服务端允许 Windows Tauri 生产 Origin `http://tauri.localhost`，解决登录请求被 CORS 拒绝的问题。
- 未登录或退出登录时隐藏透明浮窗。
- 托盘入口和老板键恢复逻辑均受原生认证状态保护。
- 窗口状态继续保存尺寸和位置，但不再恢复历史可见状态。

## 验证证据

- 服务端 48 个测试执行通过，3 个 Redis 集成测试按环境条件跳过。
- 桌面前端 12 个测试文件、47 个测试通过。
- Rust 认证门禁和窗口状态测试 3 个通过，`cargo check` 通过。
- 部署资产、失败回滚和安装包脚本定向门禁通过。
- 云端完整验收通过，包含注册登录、管理后台、AKShare 真实行情和持仓盈亏。
- 公网 Windows Origin 预检返回 200，未授权 Origin 返回 403。
- macOS ARM64 和 Windows x64 原生构建成功；Windows 构建验证了 x64 PE 和云端 HTTPS API 地址。
- 私有和公开 Release 均包含五个预期资产，二次下载 SHA-256 校验通过。
- 公开仓库匿名 HTTPS 克隆仅跟踪 `README.md`，未公开源代码和服务器配置。

## 安装资产

- `StockTradingAssistant_0.1.4_macos-arm64.app.zip`
- `StockTradingAssistant_0.1.4_macos-arm64.dmg`
- `StockTradingAssistant_0.1.4_macos-arm64_SHA256SUMS.txt`
- `StockTradingAssistant_0.1.4_windows-x64-setup.exe`
- `StockTradingAssistant_0.1.4_windows-x64_SHA256SUMS.txt`

## 待用户实机验收

Windows 0.1.4 仍需在真实 Windows 桌面完成最终体验验收：

1. 冷启动时只出现登录主窗口，不显示空白透明浮窗。
2. 使用现有测试账号登录成功，不再提示无法连接 API。
3. 登录后可从主页和托盘打开透明浮窗。
4. 退出登录后透明浮窗立即隐藏。
5. 老板键在登录前后均符合预期。
