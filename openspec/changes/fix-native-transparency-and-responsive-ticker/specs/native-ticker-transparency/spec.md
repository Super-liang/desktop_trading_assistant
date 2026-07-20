## ADDED Requirements

### Requirement: macOS 原生小窗真实透明
macOS 盯盘小窗 SHALL 在原生窗口、WebView、HTML 根节点和内容容器全链路保持透明，MUST 让系统壁纸或后方窗口内容真实穿透，且 MUST NOT 用白色、黑色或其他颜色模拟透明。

#### Scenario: 在系统壁纸上打开小窗
- **WHEN** 用户在 macOS 桌面显示盯盘小窗
- **THEN** 行情文字周围完整显示系统壁纸图案，不出现矩形背景

#### Scenario: 在其他窗口上方显示
- **WHEN** 用户将置顶盯盘小窗移动到其他应用窗口上方
- **THEN** 后方应用内容从小窗无文字区域真实透出

### Requirement: 透明能力不影响其他窗口
系统 SHALL 仅对 ticker 启用透明原生能力，主窗口 SHALL 保持原有背景与布局，Windows 端 SHALL 保持现有兼容行为。

#### Scenario: 打开主窗口
- **WHEN** 用户在 macOS 或 Windows 打开主窗口
- **THEN** 主窗口继续显示完整深色主题且不透明

### Requirement: macOS 分发约束明确
启用透明窗口的 macOS 构建 SHALL 使用允许私有 API 的非 Mac App Store 分发方式。

#### Scenario: 生成 macOS 安装包
- **WHEN** 项目构建 macOS 发布产物
- **THEN** 系统生成可用于 DMG、官网下载、Apple 公证或企业分发的应用包
