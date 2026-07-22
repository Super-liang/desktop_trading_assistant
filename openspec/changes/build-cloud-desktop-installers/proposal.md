## Why

云端 API 已部署到 `https://211.159.158.165`，但现有桌面安装包默认连接本机 `localhost:8080`，用户无法直接使用云端账号、行情和持仓服务。现在需要生成可安装的 macOS 与 Windows 客户端，并让构建产物具备可追溯版本、哈希与平台验收证据。

## What Changes

- 桌面发布构建显式注入云端 HTTPS API 地址，禁止发布包回退到 localhost。
- 生成 macOS Apple Silicon 安装包，并在 macOS 真机验证启动、登录和云 API 连接。
- 在 Windows runner 生成 Windows x64 安装包；构建流程上传安装包与 SHA-256 清单。
- 区分内部测试包与公开发行包：缺少 Apple Developer ID、公证凭据或 Windows 代码签名证书时，只交付未签名测试包并明确系统警告与安装限制。
- 增加可重复的跨平台 release workflow、产物命名、完整性校验与交付说明。

## Capabilities

### New Capabilities

- `desktop-installer-distribution`: 覆盖云 API 桌面构建、macOS/Windows 安装包、平台原生 runner、签名边界、产物哈希和交付验收。

### Modified Capabilities

无。

## Impact

- 影响 `apps/desktop` 的 Tauri bundle 配置、根目录构建脚本和 GitHub Actions。
- 新增本地发布脚本、安装包输出目录与验证文档。
- macOS 构建依赖本机 Xcode Command Line Tools；Windows 安装包依赖 Windows runner。
- 公开分发仍需要用户提供 Apple Developer ID/公证凭据和 Windows 代码签名证书，本次可先生成未签名内部测试包。
