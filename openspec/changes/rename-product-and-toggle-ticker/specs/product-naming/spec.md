## ADDED Requirements

### Requirement: 当前产品名称统一
系统当前用户界面、窗口标题、托盘提示、发布产物和交付入口 SHALL 统一使用“股票盯盘助手”，MUST NOT 在这些当前入口中继续显示“股票定盘助手”。

#### Scenario: 启动桌面应用
- **WHEN** 用户启动当前桌面构建
- **THEN** 应用名称、主窗口标题和托盘提示均显示“股票盯盘助手”

#### Scenario: 构建发布产物
- **WHEN** 项目构建 macOS 发布包
- **THEN** `.app` 和 DMG 使用包含“股票盯盘助手”的文件名

### Requirement: 历史记录保持可追溯
系统 SHALL 保留历史 OpenSpec 变更记录中的原始名称，不将本次更名伪装成历史事实。

#### Scenario: 查看历史变更
- **WHEN** 开发者阅读本次变更之前的 OpenSpec 记录
- **THEN** 历史文档保持原始内容和上下文
