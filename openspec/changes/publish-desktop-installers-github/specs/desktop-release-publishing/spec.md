## ADDED Requirements

### Requirement: 版本标签触发多平台构建
系统 MUST 在主仓库推送符合 `v*` 的版本标签时，分别在 macOS ARM64 与 Windows x64 原生 Runner 上构建桌面安装包。

#### Scenario: 推送正式版本标签
- **WHEN** 维护者向主仓库推送与应用版本一致的 `v<version>` 标签
- **THEN** 系统启动 macOS ARM64 和 Windows x64 两个平台的安装包构建任务

### Requirement: 安装包使用生产 API
系统 MUST 为正式标签构建嵌入生产 HTTPS API 地址，且不得回退为 localhost 或空地址。

#### Scenario: 标签触发没有手动输入
- **WHEN** workflow 由版本标签触发且不存在 `workflow_dispatch` 输入
- **THEN** macOS 和 Windows 安装包均嵌入 `https://211.159.158.165`

### Requirement: 发布完整平台资产
系统 MUST 在两个平台构建和验证全部成功后创建对应标签的 GitHub Release，并上传 DMG、APP ZIP、Windows EXE、Windows MSI 及平台校验和文件。

#### Scenario: 两个平台构建成功
- **WHEN** macOS 与 Windows 构建、架构检查、API 地址检查和哈希生成全部成功
- **THEN** 系统创建对应标签的 GitHub Release 并上传所有规定资产

#### Scenario: 任一平台构建失败
- **WHEN** 任一平台构建或验证任务失败
- **THEN** 系统不得创建包含不完整资产的 GitHub Release

#### Scenario: Release 资产上传中断
- **WHEN** 创建 Release 后任一资产上传或远端资产核对失败
- **THEN** Release 保持草稿状态且后续任务可覆盖上传并继续发布

### Requirement: 标签与应用版本一致
系统 MUST 在发布前验证标签版本与桌面应用版本一致。

#### Scenario: 标签版本不一致
- **WHEN** 标签去除 `v` 前缀后的版本与 `apps/desktop/package.json` 不一致
- **THEN** 发布任务失败且不创建 GitHub Release

### Requirement: 发布权限最小化
系统 MUST 使用 GitHub Actions 临时令牌发布，并将内容写权限限制在发布任务。

#### Scenario: 发布任务创建 Release
- **WHEN** 发布任务调用 GitHub Release API
- **THEN** 它使用当前 workflow 的临时 `GITHUB_TOKEN`，不依赖仓库中的长期个人访问令牌
