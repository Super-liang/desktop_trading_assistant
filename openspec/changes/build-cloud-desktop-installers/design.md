## Context

桌面端基于 Tauri 2，当前开发构建会在未配置 `VITE_API_URL` 时回退到本机 API。云端 API 已通过 HTTPS 部署，但仓库缺少面向用户的发布构建入口、跨平台原生安装包流水线、统一产物目录和完整性清单。

构建主机为 Apple Silicon Mac。macOS 包可在本机原生生成；Windows MSI 必须在 Windows 环境使用 WiX 生成，NSIS 虽可借助 `cargo-xwin` 跨平台构建，但只作为本地验证补充。当前未提供 Apple Developer ID、公证凭据或 Windows 代码签名证书。

## Goals / Non-Goals

**Goals:**

- 生成连接云端 HTTPS API 的 macOS Apple Silicon 安装包。
- 提供可重复执行的 Windows x64 原生构建，生成 NSIS 和 MSI 安装包。
- 尝试在 Mac 上交叉生成 Windows x64 NSIS 包，作为无需等待 CI 的补充交付。
- 对安装包生成 SHA-256 清单，并验证发布前端不包含 localhost API 回退。
- 明确签名、公证和系统安全提示边界。

**Non-Goals:**

- 本次不申请或托管 Apple、Microsoft 代码签名证书。
- 本次不实现自动更新、应用商店上架或灰度发布。
- 本次不生成 Intel Mac 安装包；如需兼容旧 Intel Mac，后续增加 `x86_64-apple-darwin` 构建矩阵。
- 不修改云端服务、业务功能或数据库结构。

## Decisions

### 1. 发布 API 地址由构建入口显式注入并校验

发布脚本和 CI 使用 `VITE_API_URL=https://211.159.158.165` 构建。构建后扫描前端静态资源，必须包含目标云 API，且不得包含作为 API 地址的 `localhost:8080`。相比提交固定的 `.env.production`，显式参数更便于将来按测试、预发、生产环境复用同一代码。

### 2. Windows 原生 runner 是权威构建环境

GitHub Actions 在 `windows-latest` 上使用 Tauri CLI 生成 NSIS `setup.exe` 与 MSI。Mac 上的 `cargo-xwin` 交叉构建仅尝试生成 NSIS，并在成功时纳入交付；它不替代 Windows 原生安装和启动验收。这样既满足当前无 Windows 开发机的限制，也保留官方工具链支持最完整的发布路径。

### 3. macOS 生成 Apple Silicon DMG 和压缩 APP

本机构建目标固定为 `aarch64-apple-darwin`，生成 DMG，并将 `.app` 打包为 ZIP 便于备用交付。没有 Developer ID 时使用 ad-hoc 签名，使 bundle 结构可验证，但不宣称通过 Apple 公证或 Gatekeeper 信任。

### 4. 产物统一归档并生成完整性清单

最终文件复制到 `build/installers/<version>/<platform>/`，文件名包含产品名、版本和架构。每个平台生成 `SHA256SUMS.txt`。CI 使用同样的结构上传 artifacts，降低查找错误版本或误传损坏文件的风险。

### 5. 发布自动化与日常 CI 分离

新增手动触发的安装包 workflow，不改变现有快速 CI。发布 workflow 执行依赖安装、目标平台构建、静态资源检查、哈希生成和 artifact 上传。这样避免每次提交都承担完整 bundle 的时间成本。

### 6. Windows 发布二进制使用 GUI 子系统

Windows release 入口声明 `windows_subsystem = "windows"`，避免启动桌面应用时额外弹出控制台窗口。交叉构建通过 PE header 检查架构和 GUI subsystem，确保安装包中实际应用符合桌面体验。

## Risks / Trade-offs

- [未使用正式代码签名] → macOS 和 Windows 可能显示未知开发者或安全警告；交付说明明确仅供内部测试，公开发行前必须配置证书和公证。
- [直接使用 IP 的 HTTPS 证书兼容性] → 构建前及验收阶段检查 API health；未来绑定域名后只需修改构建参数并重建客户端。
- [Windows 交叉构建兼容性不足] → 将 Windows 原生 CI 产物定义为权威结果，交叉包仅在构建与静态验证成功时交付。
- [仅支持 Apple Silicon] → 在产物名称和文档标注 arm64；Intel 支持作为后续独立构建目标。
- [构建时环境地址固化] → 通过脚本参数和 CI input 保留可配置能力，不在源码中硬编码多个环境。

## Migration Plan

1. 新增发布脚本、CI workflow 和交付文档，不影响现有开发启动方式。
2. 在 Mac 本机执行生产构建并验证 DMG、APP 签名结构、云 API 地址与哈希。
3. 尝试 Mac 交叉生成 Windows NSIS；无论结果如何，保留 Windows 原生 workflow。
4. 在获得 GitHub 推送授权后触发 workflow，下载并在 Windows 真机完成安装、启动、登录验证。
5. 如需回滚，只删除新增 release workflow/脚本和 `build/installers` 产物；应用业务代码不受影响。

## Open Questions

- 公开发布前使用个人还是组织的 Apple Developer 与 Windows 代码签名证书？
- 是否需要补充 Intel Mac 版本和 Windows ARM64 版本？
- 未来绑定域名后，是否同步引入稳定的生产 API 域名与自动更新服务？
