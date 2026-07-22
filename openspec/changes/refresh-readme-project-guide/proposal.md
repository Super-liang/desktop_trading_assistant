## Why

现有 README 仍以早期演示模式为主，未准确呈现 0.1.3 已具备的双模式真实行情、双来源缓存、用户级本机偏好、透明小窗、云端服务和正式安装包，也缺少面向首次使用者的清晰下载与快速启动路径。现在需要将 README 更新为项目首页级文档，让普通用户、开发者和部署维护者都能快速找到可信入口。

## What Changes

- 以成熟开源项目常见的标题、徽章、导航、功能卡片、架构图和分层章节重构 README 排版。
- 准确总结当前 0.1.3 已实现功能、行情模式、权限边界、透明小窗及已知限制。
- 增加普通用户下载安装、macOS 首次打开、开发者本地启动和 Apple Container 无 Docker启动路径。
- 补充真实行情所需的 PostgreSQL、Redis、AKShare、Spring API 和 Tauri 启动顺序及健康检查。
- 增加测试、构建、云部署、Release 下载、项目结构、路线图和合规免责声明入口。
- 删除或修正已过时的 Demo 降级、缓存时长和发布状态描述，所有命令以仓库当前脚本和配置为准。

## Capabilities

### New Capabilities

- `project-readme-guide`: 约束仓库首页对现有功能、快速开始、架构、部署、发布和限制的准确说明与可导航排版。

### Modified Capabilities

无。此次仅改进项目文档，不改变应用运行行为或现有接口契约。

## Impact

- 主要修改根目录 `README.md`。
- 新增本变更的 OpenSpec proposal、design、spec 和 tasks 文档。
- 不修改桌面端、Spring API、AKShare 网关、数据库或部署运行状态。
