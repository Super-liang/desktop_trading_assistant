## 验证记录

日期：2026-07-27（Asia/Shanghai）

### 问题现场

- 修复前实际运行路径：`/Applications/股票盯盘助手.app/Contents/MacOS/trading-assistant`，版本 0.1.4，构建时间 2026-07-23 09:11:10。
- 旧版连续采样：主进程约 84%～90%，WebKit Networking 约 124%～142%，两个 WebContent 约 53%～95%。
- 进程 sample 显示主线程持续处理 Tauri/WebKit IPC，WebContent 重复执行 JavaScript 与 `Intl.DateTimeFormat` 初始化；按钮现场无法正常切换页面。

### 构建验证

- `npm test`：13 个测试文件、50 项测试全部通过。
- `npm run build`：TypeScript 与 Vite 生产构建成功。
- `cargo test --lib`：6 项 Rust 测试全部通过。
- `cargo check`：成功。
- `cargo clippy --all-targets -- -D warnings`：成功，无警告。
- `VITE_API_URL=https://211.159.158.165 npm run tauri build -- --bundles app`：0.1.5 macOS `.app` 构建成功。
- npm、Cargo、Tauri 与最终 `Info.plist` 版本均为 0.1.5。
- 构建二进制 SHA-256：`ec83742bbaf2c567a50fa0c6901161383dace14e8d7a9fdd84b1acf856608ecb`。

### 安装验证

- 旧应用备份：`/Applications/股票盯盘助手-0.1.4-backup-20260727-150327.app`。
- 正式安装路径：`/Applications/股票盯盘助手.app`。
- 安装后二进制 SHA-256 与构建产物完全一致，运行进程命令行指向正式安装路径。
- 测试账号登录成功；行情源页面约 153ms 打开；透明小窗约 48ms 创建并立即展示共享持仓。
- 老板键可隐藏并恢复主窗口与透明小窗。

### CPU 与响应验收

- 主窗口与透明小窗同时可见的 5 次采样：主进程 0%，WebKit 约 0%～0.3%。
- 老板键隐藏后的 5 次采样：主进程 0%，WebKit 通常 0%～0.2%，一次 Networking 瞬时 1.0%。
- 恢复后继续运行 30 秒并在启动约 4 分钟时复采：主进程持续 0%，WebKit 通常 0%～0.1%；Networking 随 5 秒行情请求偶发约 0.8%～2.7% 瞬时值并立即回落，未出现持续占用爬升、闪屏或按钮失效。
- 当前单股 `SSE:688825` 的数据源返回行情暂不可用，但 Spring API 和 AKShare 网关连接正常；该上游行情结果不影响本次客户端能耗与响应验收。

### 平台限制

本轮只在当前 macOS 设备完成覆盖安装和现场验证，未生成或验证 Windows 0.1.5 安装包，也未发布 GitHub Release。
