## ADDED Requirements

### Requirement: README 准确展示当前产品能力
README MUST 以 0.1.3 当前实现为事实来源，清晰区分已实现功能、规划功能和已知边界，不得继续使用过时的 Demo、缓存或发布状态描述。

#### Scenario: 访问者浏览项目首页
- **WHEN** 访问者打开仓库 README
- **THEN** 首屏能识别产品定位、支持平台、当前版本、核心功能和 Release 下载入口

### Requirement: README 提供可执行的快速开始路径
README MUST 分别为安装包用户和源码开发者提供快速开始，并且源码路径 MUST 覆盖 PostgreSQL、Redis、AKShare、Spring API 与桌面端的启动顺序和健康检查。

#### Scenario: macOS 开发者不使用 Docker
- **WHEN** macOS 开发者按照 Apple Container 快速开始执行命令
- **THEN** 能启动 PostgreSQL 与 Redis，并在独立终端启动 AKShare、Spring API 和 Tauri 桌面端

#### Scenario: 用户直接下载安装包
- **WHEN** Windows 或 macOS 用户希望直接体验 0.1.3
- **THEN** README 提供对应 Release 入口、平台资产说明及 macOS 未公证提示

### Requirement: README 提供可导航的技术与运维入口
README MUST 使用清晰章节、目录、项目结构、架构图和文档链接，将开发、测试、构建、云部署、发布与扩展说明连接到仓库现有资料。

#### Scenario: 维护者查找部署方式
- **WHEN** 维护者需要部署 OpenCloudOS 云服务器或构建安装包
- **THEN** README 能直接导航到对应手册、脚本和常用命令，而无需从源码猜测入口

### Requirement: README 保持安全与合规表达
README MUST NOT 包含真实密钥、用户凭证或误导性的投资/行情授权承诺，并 MUST 明确 AKShare 研究用途、非投资建议及生产行情授权要求。

#### Scenario: 开发者配置真实行情链路
- **WHEN** 开发者阅读 AKShare 和生产行情章节
- **THEN** 能理解公开源的稳定性与授权限制，并使用环境变量占位配置共享密钥
