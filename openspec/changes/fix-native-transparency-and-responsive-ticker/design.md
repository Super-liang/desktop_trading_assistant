## Context

当前 ticker 已配置 `transparent: true`，前端根节点也使用透明背景，但 macOS 构建未启用 Tauri `macOSPrivateApi`，因此原生窗口仍可能由 WKWebView/NSWindow 以白色背景合成。Tauri 2 官方配置说明指出，macOS 透明窗口需要 `macos-private-api` 能力，代价是无法通过 Mac App Store 审核；用户已接受 DMG、官网下载、Apple 公证或企业分发。

现有紧凑行情表固定为五列，字号和间距也固定。窗口虽然可调整尺寸，但最小宽高较大，内容不会根据实时宽高连续变化。

## Goals / Non-Goals

**Goals:**

- 让 macOS ticker 窗口真实穿透系统壁纸和窗口后方内容，不再显示白色或黑色填充。
- 在约 `420×200` 至放大尺寸之间连续调整字体、行高、间距和控制文字。
- 宽屏保持五列；中窄屏将次要信息换行和重排，且不丢失股票、现价、持仓、成本及盈亏。
- 保留文字透明度、拖动、隐藏、老板键、置顶和行情刷新。
- 主窗口及 Windows 端现有行为不受影响。

**Non-Goals:**

- 不提供用户自定义缩放倍率。
- 不改变持仓、行情或认证接口。
- 不为本期兼容 Mac App Store。

## Decisions

1. 在 Tauri 配置的 `app` 下启用 `macOSPrivateApi: true`，并保留 ticker 的 `transparent: true`。这是 Tauri 官方支持 macOS 透明窗口的最短路径。只有原生构建验证仍失败时，才增加仅 macOS 生效的 `NSWindow` clear-color 兜底，避免提前引入 AppKit 绑定依赖。
2. 使用 `ResizeObserver` 观察 ticker 根容器，按 `clamp(min(width / 720, height / 340), 0.7, 1.15)` 计算连续缩放值。相比 `transform: scale()`，CSS 自定义属性驱动字号和间距不会造成布局尺寸与视觉尺寸不一致，也更容易测试。
3. 使用 CSS container query 完成信息重排。宽窗口保留五列；中等宽度改为三列并把持仓、市值放到第二行；窄窗口改为两列多行。所有核心字段始终保留。
4. 将最小窗口尺寸调整为约 `420×200`。达到 `0.7` 可读性下限后不继续缩小文字；高度仍不足时允许内容区内部滚动并隐藏滚动条。
5. 缩放计算封装为纯函数，组件只负责观察尺寸并写入 `--ticker-ui-scale`。通过单元测试验证边界，通过组件测试验证尺寸更新与 CSS 变量，通过原生 `.app` 验证真实桌面合成。

## Risks / Trade-offs

- [启用私有 API 后无法上架 Mac App Store] → 采用 DMG、官网下载、Apple 公证或企业分发，用户已接受。
- [复杂壁纸降低文字可读性] → 保留现有文字阴影和 20%–100% 文字透明度控制。
- [极小窗口无法同时保持原布局和可读性] → 在 `0.7` 停止缩小并重排信息，最后以无可见滚动条的内部滚动兜底。
- [不同 WebView 对 container query 支持差异] → 保留基础五列布局，并用组件尺寸标记提供可测试的降级选择器。

## Migration Plan

1. 更新配置和前端实现后运行测试、构建与 Rust 检查。
2. 构建 macOS `.app`，在有明显图案的窗口或桌面背景上验证穿透。
3. 如透明回归，可回退 `macOSPrivateApi` 与 ticker 响应式改动；主窗口和后端数据无迁移需求。

## Open Questions

无。
