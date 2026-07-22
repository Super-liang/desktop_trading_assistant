## Why

当前桌面安装包工作流只支持手动构建并保留短期 Artifact，无法把 Windows 与 macOS 安装包作为面向用户的稳定下载版本发布。需要建立基于版本标签的自动发布能力，让生产 API 地址、安装包、校验和与版本说明统一进入 GitHub Release。

## What Changes

- 支持推送 `v*` 版本标签后自动触发 macOS ARM64 与 Windows x64 原生构建。
- 手动触发和标签触发均默认嵌入生产 HTTPS API 地址，并继续允许手动覆盖。
- 两个平台构建成功后，自动汇总 DMG、APP ZIP、EXE、MSI 与 SHA-256 校验文件。
- 自动创建对应标签的 GitHub Release，并上传全部安装包和校验文件。
- Release 仅在两个平台均构建并验证成功后发布，任何平台失败都不得产生不完整版本。

## Capabilities

### New Capabilities

- `desktop-release-publishing`: 规定版本标签触发、多平台安装包构建、完整性校验与 GitHub Release 发布行为。

### Modified Capabilities

无。

## Impact

- 修改 `.github/workflows/desktop-installers.yml` 的触发器、权限与发布任务。
- 使用 GitHub Actions 自带的 `GITHUB_TOKEN` 创建 Release，不引入长期个人令牌。
- 现有桌面应用代码、后端 API 与数据库结构不变。
