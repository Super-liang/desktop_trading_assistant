## ADDED Requirements

### Requirement: 一键本地启动
项目 SHALL 提供环境变量样例、数据库迁移、Docker Compose 和文档化命令，使开发者能启动 PostgreSQL、API、Mock 行情和桌面 Web 开发界面。

#### Scenario: 全新环境启动
- **WHEN** 开发者按 README 在满足 Java 17、Node 和 Rust 前置条件的环境中执行启动命令
- **THEN** API 健康检查成功且桌面端可完成注册、登录、添加持仓和查看演示行情

### Requirement: 可重复构建与测试
后端、前端和 Rust 壳 SHALL 使用锁定依赖并提供自动化测试；CI SHALL 在 Linux 验证 API/前端，在 Windows 和 macOS 验证桌面构建。

#### Scenario: 提交变更
- **WHEN** CI 处理一次代码提交
- **THEN** 它执行后端测试、前端测试、静态检查和相应平台桌面编译，并保留失败证据

### Requirement: 可观测与安全配置
服务 SHALL 暴露不含敏感信息的存活/就绪检查，密钥 SHALL 从环境变量读取，生产配置 SHALL 禁止默认口令和演示管理员密码。

#### Scenario: 生产缺少 JWT 密钥
- **WHEN** API 以生产配置启动且没有合格 JWT 密钥
- **THEN** 服务快速失败并输出不含密钥内容的配置错误

### Requirement: 签名发布门禁
公开分发前 Windows 构建 SHALL 完成代码签名，macOS 构建 SHALL 完成签名与公证，并分别验证透明窗口、托盘、快捷键和自动恢复。

#### Scenario: 未配置签名证书
- **WHEN** 发布流水线缺少目标平台签名凭据
- **THEN** 流水线不得把构建产物标记为可公开发布

