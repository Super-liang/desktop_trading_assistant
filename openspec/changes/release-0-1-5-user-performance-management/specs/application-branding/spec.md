## ADDED Requirements

### Requirement: 桌面与 Web 使用统一应用图标
系统 SHALL 以 `assets/branding/yinxian-app-icon-v1.png` 为唯一品牌图标源，生成 macOS、Windows 和 Web 所需格式及尺寸。

#### Scenario: 构建 macOS 安装包
- **WHEN** 构建 0.1.5 macOS 应用和安装包
- **THEN** 应用包、Dock/Finder 和安装介质使用由新图标生成的 `.icns` 资源

#### Scenario: 构建 Windows 安装包
- **WHEN** 构建 0.1.5 Windows 应用和安装包
- **THEN** 可执行文件、快捷方式和安装程序使用由新图标生成的 `.ico` 及对应 PNG 资源

#### Scenario: 浏览器访问
- **WHEN** 用户在浏览器打开 Web 应用
- **THEN** 浏览器标签页加载由同一图标源生成的 favicon
