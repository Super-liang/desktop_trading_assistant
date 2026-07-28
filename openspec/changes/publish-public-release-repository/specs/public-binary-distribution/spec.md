## ADDED Requirements

### Requirement: 公开仓库采用最小内容允许列表
公开仓库默认分支 MUST 只包含面向最终用户的 Markdown 产品介绍，不得包含业务源码、环境配置、部署脚本、OpenSpec、数据库内容或凭证。

#### Scenario: 检查公开仓库文件树
- **WHEN** 发布者或外部用户查看默认分支
- **THEN** Git 跟踪文件只有 `README.md`

### Requirement: 公开 Release 完整分发双平台安装包
公开仓库 MUST 使用正式 Release 分发 macOS arm64 DMG、APP ZIP、Windows x64 NSIS 安装器和两个平台的 SHA-256 清单。

#### Scenario: 查看 v0.1.3 Release
- **WHEN** 外部用户打开公开仓库 v0.1.3 Release
- **THEN** 能看到且只能看到五个预期资产，并能使用清单验证三个安装资产

### Requirement: 公开介绍不得依赖私有资源
公开 README MUST NOT 链接私有仓库、私有文档、内部 CI 或服务器管理入口，并 MUST 明确平台、版本、签名状态、行情限制和下载校验方式。

#### Scenario: 未登录用户访问公开仓库
- **WHEN** 用户没有私有源码仓库权限
- **THEN** 仍能阅读完整产品介绍并从同一公开仓库下载和验证安装包

### Requirement: 发布前执行敏感信息检查
公开内容 MUST 在推送前检查已知凭证、邮箱、服务器 IP、localhost 开发地址、环境变量密钥和私有路径，检查失败时不得发布。

#### Scenario: README 意外包含内部信息
- **WHEN** 允许列表或敏感信息扫描发现非公开内容
- **THEN** 发布流程停止且不得创建或更新公开 Release
