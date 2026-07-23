## Why

Windows 0.1.3 的登录 CORS 和未认证透明浮窗问题已经在本地修复并通过自动化验证，但旧线上服务与安装包尚未包含修复。需要发布 0.1.4 并部署服务端，才能让 Windows 用户实际恢复登录并获得正确的浮窗生命周期。

## What Changes

- 将桌面应用版本统一升级到 0.1.4。
- 提交并推送 Windows 运行时修复，合并到 `main`。
- 将包含 Windows Tauri Origin CORS 修复的 API 服务部署到现有腾讯云环境。
- 创建私有源码仓库的 v0.1.4 标签，触发 macOS ARM64 与 Windows x64 安装包构建。
- 验证安装资产、版本、架构、API 地址及 SHA-256 后，将安装包同步到公开下载仓库。
- 更新公开 README 的下载链接和版本说明，仅公开 Markdown 与 Release 安装资产。

## Capabilities

### New Capabilities

- `desktop-release-0-1-4`: 定义 0.1.4 的部署、跨平台安装包发布、公开分发和验收要求。

### Modified Capabilities

无。

## Impact

- 影响桌面端版本文件、私有 GitHub 源码仓库、腾讯云 API 服务和公开安装包仓库。
- 不修改数据库结构、Redis 数据、行情刷新配置和用户账户数据。
- 发布期间 API 容器需要滚动替换，可能出现短暂连接切换。
