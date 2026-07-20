## Context

产品当前在 Tauri `productName`、窗口标题、HTML 标题、托盘提示、README 和验证报告中使用“股票定盘助手”。用户已确认当前产品与发布产物应统一改为“股票盯盘助手”，历史 OpenSpec 记录保留原文。

`Dashboard` 的 `showTicker()` 每次都同步会话并调用 `show()`，没有查询窗口是否已经可见，因此重复点击不会隐藏。ticker 还可能通过自身“隐藏”按钮、老板键和托盘改变可见状态，React 本地布尔状态无法可靠代表原生窗口状态。

## Goals / Non-Goals

**Goals:**

- 当前产品界面、发布元数据和交付入口统一使用“股票盯盘助手”。
- “透明小窗”按钮每次点击都依据原生实际状态切换显示或隐藏。
- 小窗通过自身按钮、老板键或托盘改变状态后，主界面下一次点击仍能做出正确动作。
- 保留会话同步、置顶、拖动、透明和响应式行为。

**Non-Goals:**

- 不修改应用 identifier、数据库、API 或历史 OpenSpec 文档。
- 不增加持久化的 React 可见状态。
- 不新增 Rust IPC 命令。

## Decisions

1. 对当前用户可见和发布相关文件做精确命名替换，包括 Tauri 配置、HTML 标题、Cargo 描述、托盘提示、README 与验证报告。历史 `openspec/changes` 不批量改写。
2. 将 `showTicker()` 改为 `toggleTicker()`：通过 `WebviewWindow.getByLabel("ticker")` 获取窗口，调用 `isVisible()` 查询真实状态。可见时只调用 `hide()`；不可见时先 `emitTo` 同步会话，再调用 `show()` 和 `setFocus()`。
3. 不在 React state 中缓存窗口可见性。该状态可能被老板键、ticker 自身隐藏按钮或托盘异步修改，每次查询原生窗口是最小且正确的事实来源。
4. 使用 Vitest mock Tauri 动态模块，覆盖可见→隐藏、隐藏→同步并显示，以及外部隐藏后再次显示。命名通过配置断言和文本扫描验证。

## Risks / Trade-offs

- [历史文档仍包含旧名称] → 仅保留历史 OpenSpec 记录，当前 README、配置和交付报告必须无旧名称。
- [Tauri 状态查询失败] → 保留现有容错，浏览器开发模式不因缺少原生窗口而崩溃。
- [显示后未获得焦点] → `show()` 后显式调用 `setFocus()`。

## Migration Plan

重新构建后 macOS `.app` 和 DMG 文件名变为“股票盯盘助手”；旧安装包不会自动改名，用户需用新构建覆盖。应用 identifier 不变，因此本机应用数据可继续复用。

## Open Questions

无。
