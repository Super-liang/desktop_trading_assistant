# 原生透明与响应式盯盘小窗设计

## 背景

当前小窗的网页背景已设置为透明，但 macOS 原生窗口未启用 Tauri 私有透明能力，实际仍可能合成为白色。固定五列布局也不会随用户手动缩放窗口同步调整。

## 已确认目标

- macOS 小窗真实穿透系统壁纸和后方窗口。
- 接受启用 `macOSPrivateApi` 后无法上架 Mac App Store，采用 DMG、官网下载、Apple 公证或企业分发。
- 采用混合自适应：字号和间距连续缩放，空间不足时重排次要信息。
- 最小尺寸约 `420×200`，缩放范围 `0.7–1.15`。
- 所有尺寸下保留股票、现价、持仓、成本、市值、盈亏及控制能力。

## 技术设计

Tauri 配置启用 `app.macOSPrivateApi: true`，ticker 保留 `transparent: true`。前端继续保证 `html/body/#root/ticker` 全链路透明。只有官方配置仍不足时，才增加 macOS `NSWindow` clear-color 兜底。

`TickerWindow` 通过 `ResizeObserver` 获取实际宽高，调用纯函数计算：

```text
scale = clamp(min(width / 720, height / 340), 0.7, 1.15)
```

缩放值写入 CSS 变量，驱动字号、行高、间距和控制尺寸；不使用 `transform: scale()`。布局通过 container query 分为五列、三列多行和两列多行三个阶段，不隐藏核心行情字段。

## 验证

- 单元测试覆盖缩放上下限和宽高共同参与计算。
- 组件测试模拟尺寸变化并检查 CSS 变量与布局模式。
- 运行完整前端测试、生产构建、Rust 检查和 OpenSpec 严格校验。
- 在 macOS 原生 `.app` 中将小窗置于有明显图案的桌面或应用上方，验证真实穿透，并检查默认、中等、最小尺寸。

## 非目标

- 不提供用户自定义缩放倍率。
- 不改变后端、行情、持仓或认证接口。
- 本期不兼容 Mac App Store 分发。
