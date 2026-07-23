# 本地验证记录

验证日期：2026-07-23

## 根因证据

- 线上 `OPTIONS /api/v1/auth/login` 对 `Origin: http://tauri.localhost` 返回
  `403 Invalid CORS request`。
- 相同接口对已配置的 `https://tauri.localhost` 和 `tauri://localhost` 返回成功。
- Tauri 2 本地依赖源码确认 Windows 生产 WebView 使用
  `http://tauri.localhost`（未开启 HTTPS scheme 时）。
- `tauri-plugin-window-state` 默认状态标志包含 `VISIBLE`，会恢复历史可见状态。

## 自动化验证

- 服务端全量测试：48 个执行通过，3 个 Redis 集成测试按环境条件跳过。
- 新增 CORS 集成测试：Windows Tauri Origin 预检成功，未授权 Origin 返回 403。
- 桌面前端测试：12 个测试文件、47 个测试全部通过。
- Rust 测试：认证门禁和窗口状态标志共 3 个测试全部通过。
- TypeScript 与 Vite 生产构建通过。
- Rust `cargo check` 通过。
- 生产前端产物包含 `https://211.159.158.165`，且未包含 localhost API 回退地址。
- 独立代码审查未发现新增高、中、低优先级问题，变更范围仅限 CORS、窗口门禁、测试和本次规范。

## 待后续验证

- 本轮未部署服务端，因此线上 CORS 仍保持旧行为。
- 本轮未发布新版 Windows 安装包；需在用户确认后部署服务端、构建新包，
  并在 Windows 实机复验冷启动、登录、托盘、老板键和退出登录。
