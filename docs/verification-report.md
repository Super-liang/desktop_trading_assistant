# 一期验证报告

验证日期：2026-07-20（Asia/Shanghai）

## 自动化证据

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| OpenSpec | `openspec validate build-cross-platform-trading-assistant --strict` | 通过 |
| Java 17 后端 | `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw clean verify` | 通过，8 tests，0 failures/errors |
| 前端测试 | `npm run test:desktop` | 通过，3 test files / 3 tests |
| 前端构建 | `npm run build:desktop` | 通过，Vite 产物生成 |
| Rust 原生检查 | `cargo check --locked --manifest-path apps/desktop/src-tauri/Cargo.toml` | 通过 |
| macOS 应用包 | `npm --workspace apps/desktop run tauri -- build --bundles app` | 通过，生成 `.app` |
| Compose | `JWT_SECRET=... docker compose config --quiet` | 通过 |

macOS 本地产物：

`apps/desktop/src-tauri/target/release/bundle/macos/股票盯盘助手.app`

## 端到端烟测

使用 `local` H2 配置启动 API、Vite 启动界面，在本地浏览器完成：

1. 切换注册页面并填写昵称、邮箱、密码。
2. 注册成功后进入受保护工作台。
3. 打开“添加自选”，从 Demo Provider 搜索并选择 `SSE:600519`。
4. 输入数量 `100`、成本 `1400` 并保存。
5. 页面显示市值、浮盈亏、收益率、市场阶段和 DEMO 标签。
6. 等待刷新后价格及盈亏发生一致变化。

烟测发现并修复：开发 CORS 原只允许 `localhost`，现同时允许 `127.0.0.1`。

## 仍需发行主体完成

- Windows 10/11 真机验证、代码签名证书和安装包信誉测试。
- macOS Intel/Apple Silicon 双架构真机验证、Developer ID 签名和 notarization。
- 获得沪深 PC 展示/分发授权并接入真实 Provider；当前只能作为 DEMO。
- 如需重启免登录，正式桌面令牌持久化需接入 OS Keychain/Stronghold；当前会话只在进程内存中存在。
- 隐私政策、用户协议、账号找回/邮箱验证、备案和安全评估。

## 独立 verification（首次）

首次独立核验未通过，因此 OpenSpec 任务 6.5 保持未完成。需修复的仓库内问题包括：跨 WebView 会话同步、老板键原状态恢复、快捷键冲突降级、窗口状态持久化、Provider 调用级降级与 stale 计算、行情源时间展示、管理审计视图和关键交互测试。真实行情授权与双平台签名真机验收继续作为外部门禁，不以 DEMO 或单平台编译替代。

上述仓库内问题已完成修正，并补充了持仓编辑、退出全部设备、账号注销密码重验、禁用账号访问令牌即时失效、授权 HTTP 行情桥接器及 Windows/macOS 原生 release 编译 CI。第二次独立核验结论见下节。

## 独立 verification（第二次）

第二次独立烟测发现并推动修复了两个问题：自定义 local 配置缺少 `app.quotes` 时空持仓接口 500，以及主窗登出后小窗未立即清除会话。修复后已重新执行完整自动化门禁，但独立审查通道在复测完成前中断；另一只读审查通道因此拒绝给出“通过”结论。按证据优先原则，任务 6.5 继续保持未完成。

## 独立 code review

在 verification 之后使用分离上下文完成代码审查，发现 3 个 P1 与 3 个 P2，已全部修复并重新构建：

- 主窗统一负责 refresh token 轮换，并向小窗广播会话，避免双窗口并发重放。
- 登录、刷新、退出、退出全部设备和账号注销均同步小窗；托盘直接打开也能使用已广播会话。
- 管理员账号禁止自助注销，避免审计外键导致 500。
- Provider 空请求直接返回；空/部分行情响应判为失败并触发备源降级。
- 禁止管理员自禁用，并保证至少一个启用管理员。
- Tauri 启用最小 CSP，并把生成的 RGBA 图标纳入应用包。

## 退出登录卡顿回归

macOS 26 原生复现确认：服务端退出接口约 6ms 返回，卡顿发生在 WebView 会话切换。普通退出改为先异步撤销 refresh token，再重载可见主 WebView，利用一期“会话仅驻留内存”的策略回到干净登录页；不再在退出过程中操作隐藏透明 WebView。原生 `.app` 复测约 1.1 秒回到登录页，进程 CPU 恢复为 0%，并新增“服务端撤销不返回时仍立即清理本地会话”回归测试。
