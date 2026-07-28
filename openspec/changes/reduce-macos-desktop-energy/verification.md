## 验证记录

日期：2026-07-23（Asia/Shanghai）

### 自动化验证

- `npm test`：13 个测试文件、50 项测试全部通过。
- `npm run build`：TypeScript 与 Vite 生产构建成功。
- `cargo test --manifest-path src-tauri/Cargo.toml --lib`：6 项 Rust 测试全部通过。
- `cargo check --manifest-path src-tauri/Cargo.toml`：成功。
- `cargo clippy --all-targets -- -D warnings`：成功，无警告。
- `VITE_API_URL=https://211.159.158.165 npm run tauri build -- --bundles app`：macOS `.app` 生产包构建成功。
- `git diff --check`：通过。

### macOS 实机能耗采样

设备：MacBook Air M4、24 GB；macOS 26.5.2。

- 修复前 0.1.4：主进程约 55%～60% CPU，WebKit Networking 约 54%，两个 WebContent 分别约 95% 和 74%；隐藏窗口后未下降。
- 修复后新构建登录页：主进程连续 5 次采样为 0%（一次界面采样出现 1.5% 瞬时值），仅创建一个 WebContent。
- 使用测试账号登录后发现并修复 `session-sync` 在主窗口内回流导致的 IPC 自循环；诊断计数由约 14 万次调用降至稳定的初始化调用，未再增长。
- 修复后主窗口可见：主进程约 0%～0.3% CPU。
- 修复后主窗口与透明小窗同时可见：主进程约 0%～0.6% CPU；WebKit GPU、Networking、WebContent 约 0%～0.2%。
- 老板键隐藏全部窗口后连续采样：短暂处理后主进程回落至 0.1%，WebKit WebContent 回落至 0.1%，GPU 与 Networking 约 0%。

### macOS 人工场景

- 测试账号登录成功，登录后主界面和真实持仓行情正常展示。
- 未登录时不创建透明小窗；登录后点击“透明小窗”可按需创建，再次点击或点击小窗“隐藏”会销毁小窗。
- 透明小窗创建后立即收到会话和主窗口共享的持仓行情，没有空白等待。
- `⌘ + Shift + H` 可同时隐藏主窗口与透明小窗；再次触发可恢复隐藏前的两个窗口。
- 托盘恢复与老板键复用同一组 Rust 恢复函数，并由窗口生命周期单元测试覆盖；本轮未通过自动化 UI 点击 macOS 菜单栏托盘图标。

### 平台限制

当前环境为 macOS，未执行 Windows 安装包和 Windows WebView2 实机验证；本次 Rust/TypeScript 代码保持跨平台 API，生产编译已通过。
