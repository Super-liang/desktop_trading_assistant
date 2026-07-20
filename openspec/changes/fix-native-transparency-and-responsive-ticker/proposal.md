## Why

盯盘小窗的 Web 内容虽已取消背景色，但 macOS 原生窗口仍被合成为白色，无法让系统壁纸真实穿透；同时现有固定五列表格在手动缩放时不会同步调整字号、间距和信息布局。需要补齐原生透明能力，并让小窗在不同尺寸下保持完整、可读和可操作。

## What Changes

- 启用 Tauri macOS 私有 API，使 ticker 原生窗口支持真实透明合成；接受不通过 Mac App Store 分发的约束。
- 保持主窗口与 Windows 端现有视觉和运行方式不变。
- 允许盯盘小窗缩小到约 `420×200`，并根据实时宽高连续调整字号、间距和控件尺寸。
- 宽窗口保持五列信息，中窄窗口对次要信息进行换行和重排，不隐藏股票、现价、持仓、成本及盈亏。
- 增加原生透明、动态缩放、范围限制和响应式布局验证。

## Capabilities

### New Capabilities

- `native-ticker-transparency`: 定义 macOS 盯盘窗口真实穿透系统桌面背景及其发布约束。
- `responsive-ticker-layout`: 定义小窗手动缩放时的连续视觉缩放、信息重排和可读性下限。

### Modified Capabilities

无。

## Impact

主要影响 Tauri 窗口配置、ticker 尺寸策略、`TickerWindow` 的尺寸观察逻辑、紧凑行情表格样式及前端测试；不改变后端 API、行情数据模型、认证、持仓数据或主窗口布局。macOS 分发采用 DMG、官网下载、Apple 公证或企业分发，不上架 Mac App Store。
