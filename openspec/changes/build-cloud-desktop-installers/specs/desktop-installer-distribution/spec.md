## ADDED Requirements

### Requirement: 发布包连接生产云 API
系统 SHALL 在发布构建时显式注入 HTTPS 云 API 地址，并 SHALL 在产物生成前验证前端资源未使用 localhost 作为发布 API。

#### Scenario: 构建生产桌面客户端
- **WHEN** 发布人员执行 macOS 或 Windows 安装包构建
- **THEN** 客户端使用指定的 HTTPS 云 API 地址，且构建在发现 localhost API 回退时失败

### Requirement: 生成 macOS 安装包
系统 SHALL 支持在 macOS 原生环境生成 Apple Silicon DMG 和可归档 APP，并 SHALL 清晰标注版本与架构。

#### Scenario: macOS 构建成功
- **WHEN** 在满足依赖的 Apple Silicon Mac 上执行 macOS 发布脚本
- **THEN** 产物目录包含可验证的 DMG、APP ZIP 和 SHA-256 清单

### Requirement: 生成 Windows 安装包
系统 SHALL 支持在 Windows x64 原生 runner 生成 NSIS EXE 与 MSI 安装包，并 SHALL 将其作为 Windows 权威发布产物。

#### Scenario: Windows 原生构建成功
- **WHEN** 发布人员触发 Windows 安装包 workflow
- **THEN** workflow 上传版本化的 NSIS EXE、MSI 和 SHA-256 清单

#### Scenario: Windows 客户端启动
- **WHEN** 用户从安装包安装并启动 Windows release 客户端
- **THEN** 系统以 x64 GUI 应用启动且不额外显示控制台窗口

#### Scenario: Mac 执行 Windows 交叉构建
- **WHEN** Mac 主机具备 cargo-xwin、LLVM 和 NSIS 依赖并执行交叉构建脚本
- **THEN** 系统尝试生成 Windows x64 NSIS 包，并明确该包仍需 Windows 真机验收

### Requirement: 安装包可追溯与可校验
系统 SHALL 使用包含产品、版本和架构信息的文件名归档安装包，并 SHALL 为交付文件生成 SHA-256 完整性清单。

#### Scenario: 校验下载的安装包
- **WHEN** 用户依据同目录的 SHA-256 清单校验安装文件
- **THEN** 完整且未被修改的文件校验成功

### Requirement: 明确签名与公证边界
系统 SHALL 区分内部测试包和公开发行包；缺少平台证书时 SHALL 不把未签名或 ad-hoc 签名产物描述为已受信任的公开发行包。

#### Scenario: 未配置正式签名凭据
- **WHEN** 构建环境没有 Apple Developer ID、公证凭据或 Windows 代码签名证书
- **THEN** 构建仍可生成内部测试包，且交付说明包含可能出现的系统安全警告和后续签名要求

### Requirement: 构建流程可重复执行
系统 SHALL 提供仓库内的发布脚本和手动触发 CI workflow，使相同版本与 API 配置能够重新生成对应平台安装包。

#### Scenario: 重建指定版本
- **WHEN** 发布人员使用相同源码、版本和 API 地址重新执行构建
- **THEN** 流程使用相同的平台目标、bundle 类型、命名规则和验证步骤
