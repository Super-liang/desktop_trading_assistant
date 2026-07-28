## Why

macOS 当前 `/Applications` 中仍运行修复前的 0.1.4，持续触发高频 IPC 与 WebKit 渲染循环；修复构建虽然已生成，但因版本号相同且未覆盖安装，用户无法辨别实际运行版本。需要发布可识别的 0.1.5 并完成覆盖安装与现场验证。

## What Changes

- 将桌面应用版本统一提升至 0.1.5，确保应用元数据、Rust 包及前端包版本一致。
- 使用已完成的 macOS 能耗修复生成新的 0.1.5 `.app`。
- 在保留应用数据的前提下退出旧进程并覆盖 `/Applications/股票盯盘助手.app`。
- 验证安装版本、登录、按钮响应、透明小窗以及持续运行时 CPU/WebKit 占用。

## Capabilities

### New Capabilities

- `desktop-release-integrity`: 约束桌面版本标识、安装包与实际运行二进制一致，并要求升级后执行版本和能耗验收。

### Modified Capabilities

无。

## Impact

- 影响 `apps/desktop/package.json`、锁文件、Tauri/Rust 版本元数据与 macOS 应用包。
- 不修改服务端 API、数据库、用户持仓或认证数据。
- 本机 `/Applications/股票盯盘助手.app` 将由 0.1.5 覆盖，旧进程会先退出。
