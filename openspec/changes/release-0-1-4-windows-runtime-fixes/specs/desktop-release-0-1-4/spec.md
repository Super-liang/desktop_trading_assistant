## ADDED Requirements

### Requirement: 0.1.4 版本一致性
发布流程 MUST 确保 npm、Tauri、Cargo、Git 标签和安装资产均使用版本 0.1.4。

#### Scenario: 发布前版本检查
- **WHEN** 系统准备创建 v0.1.4 标签
- **THEN** 所有桌面版本来源和生成资产名称均为 0.1.4

### Requirement: 服务端修复先行部署
发布流程 MUST 在公开 0.1.4 安装包前部署服务端 CORS 修复，并保留发布前数据库备份和自动回滚能力。

#### Scenario: Windows Origin 公网预检
- **WHEN** 部署后的公网登录接口收到 `Origin: http://tauri.localhost` 的预检请求
- **THEN** 服务端返回成功并包含对应的 `Access-Control-Allow-Origin`

#### Scenario: 部署健康检查失败
- **WHEN** 新 API release 无法通过健康检查
- **THEN** 部署脚本恢复上一应用 release 且不执行数据库破坏性恢复

### Requirement: 原生跨平台安装资产
私有发布流水线 SHALL 在原生 macOS ARM64 和 Windows x64 runner 上构建安装资产，并 MUST 验证云端 HTTPS API 地址和目标架构。

#### Scenario: 私有 v0.1.4 Release 成功
- **WHEN** v0.1.4 标签工作流完成
- **THEN** Release 包含两个 macOS 资产、一个 Windows 安装程序和两份 SHA-256 清单

### Requirement: 最小公开分发范围
公开仓库 MUST 只在 Git 中跟踪 Markdown 介绍，并 MUST 仅通过 v0.1.4 Release 分发预期的安装资产和校验文件。

#### Scenario: 公开发布完成
- **WHEN** 0.1.4 资产同步到公开仓库
- **THEN** 匿名用户可读取 README、下载五个预期资产并通过 SHA-256 校验

#### Scenario: 公开内容检查
- **WHEN** 发布工具扫描公开仓库和 Release
- **THEN** 不得发现源代码、服务器配置、凭据、私有仓库路径或用户数据
