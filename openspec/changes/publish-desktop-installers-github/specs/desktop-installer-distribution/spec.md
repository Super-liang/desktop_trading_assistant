## MODIFIED Requirements

### Requirement: 生成 Windows 安装包
系统 SHALL 支持在 Windows x64 原生 runner 生成经过 PE x64 架构验证的 NSIS EXE 安装包，并 SHALL 将其作为 Windows 权威发布产物。系统不要求生成 MSI，WiX MSI 失败不得阻断已验证 NSIS EXE 的发布。

#### Scenario: Windows 原生构建成功
- **WHEN** 发布人员触发 Windows 安装包 workflow
- **THEN** workflow 上传版本化的 NSIS EXE 和 SHA-256 清单

#### Scenario: Windows 客户端启动
- **WHEN** 用户从安装包安装并启动 Windows release 客户端
- **THEN** 系统以 x64 GUI 应用启动且不额外显示控制台窗口

#### Scenario: Mac 执行 Windows 交叉构建
- **WHEN** Mac 主机具备 cargo-xwin、LLVM 和 NSIS 依赖并执行交叉构建脚本
- **THEN** 系统尝试生成 Windows x64 NSIS 包，并明确该包仍需 Windows 真机验收
