# Native Transparent Responsive Ticker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 macOS 盯盘小窗真实穿透系统背景，并在手动缩放时连续调整字号、间距和信息布局。

**Architecture:** Tauri 配置负责启用 macOS 原生透明能力，React 的 `ResizeObserver` 负责把实时宽高转换为有上下限的 CSS 缩放变量，CSS container query 负责在五列、三列多行和两列多行之间重排。缩放计算保持为纯函数，原生兜底只在官方配置经验证不足时引入。

**Tech Stack:** Tauri 2.10、Rust、React 19、TypeScript、CSS Container Queries、Vitest、Testing Library

---

### Task 1: 启用 macOS 原生透明与更小窗口尺寸

**Files:**
- Modify: `apps/desktop/src-tauri/tauri.conf.json`
- Verify: `apps/desktop/src-tauri/gen/schemas/desktop-schema.json`

- [ ] **Step 1: 写配置断言并确认当前失败**

Run:

```bash
node -e 'const c=require("./apps/desktop/src-tauri/tauri.conf.json"); const t=c.app.windows.find(w=>w.label==="ticker"); if(c.app.macOSPrivateApi!==true||t.minWidth!==420||t.minHeight!==200) process.exit(1)'
```

Expected: exit code 1，因为 `macOSPrivateApi` 缺失且最小尺寸仍为 `560×240`。

- [ ] **Step 2: 写入最小配置**

在 `app` 下增加：

```json
"macOSPrivateApi": true
```

并把 ticker 调整为：

```json
"minWidth": 420,
"minHeight": 200,
"transparent": true
```

- [ ] **Step 3: 验证配置与 Rust schema**

Run:

```bash
node -e 'const c=require("./apps/desktop/src-tauri/tauri.conf.json"); const t=c.app.windows.find(w=>w.label==="ticker"); if(c.app.macOSPrivateApi!==true||t.transparent!==true||t.minWidth!==420||t.minHeight!==200) process.exit(1)'
cargo check --manifest-path apps/desktop/src-tauri/Cargo.toml
```

Expected: 两个命令均退出 0。

### Task 2: 实现宽高驱动的连续缩放

**Files:**
- Create: `apps/desktop/src/lib/tickerLayout.ts`
- Create: `apps/desktop/src/lib/tickerLayout.test.ts`
- Modify: `apps/desktop/src/components/TickerWindow.tsx`
- Modify: `apps/desktop/src/components/TickerWindow.test.tsx`

- [ ] **Step 1: 编写失败的纯函数测试**

测试必须覆盖：

```ts
expect(calculateTickerScale(720, 340)).toBe(1);
expect(calculateTickerScale(420, 200)).toBe(0.7);
expect(calculateTickerScale(1440, 680)).toBe(1.15);
expect(calculateTickerScale(720, 170)).toBe(0.7);
expect(getTickerLayoutMode(700)).toBe("wide");
expect(getTickerLayoutMode(560)).toBe("medium");
expect(getTickerLayoutMode(420)).toBe("narrow");
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```bash
npm test -- --run src/lib/tickerLayout.test.ts
```

Expected: FAIL，模块或导出函数不存在。

- [ ] **Step 3: 实现纯函数与常量**

`tickerLayout.ts` 导出：

```ts
export type TickerLayoutMode = "wide" | "medium" | "narrow";

export function calculateTickerScale(width: number, height: number): number {
  const raw = Math.min(width / 720, height / 340);
  return Math.min(1.15, Math.max(0.7, Number.isFinite(raw) ? raw : 1));
}

export function getTickerLayoutMode(width: number): TickerLayoutMode {
  if (width <= 480) return "narrow";
  if (width <= 620) return "medium";
  return "wide";
}
```

- [ ] **Step 4: 在组件中观察尺寸**

为 ticker 根节点增加 `ref`，使用 `ResizeObserver` 读取 `contentRect`，更新：

```ts
{
  "--ticker-ui-scale": scale,
} as CSSProperties
```

并写入 `data-layout={layoutMode}`。清理函数必须 `disconnect()`。

- [ ] **Step 5: 增加组件尺寸变化测试**

在 jsdom 中提供可触发的 `ResizeObserver` mock，先发送 `720×340`，再发送 `430×210`，断言：

```ts
expect(ticker).toHaveStyle("--ticker-ui-scale: 1");
expect(ticker).toHaveAttribute("data-layout", "wide");
expect(ticker).toHaveStyle("--ticker-ui-scale: 0.7");
expect(ticker).toHaveAttribute("data-layout", "narrow");
```

- [ ] **Step 6: 运行定向测试**

Run:

```bash
npm test -- --run src/lib/tickerLayout.test.ts src/components/TickerWindow.test.tsx
```

Expected: 两个测试文件全部通过。

### Task 3: 实现混合响应式行情布局

**Files:**
- Modify: `apps/desktop/src/components/PortfolioTable.tsx`
- Modify: `apps/desktop/src/components/PortfolioTable.test.tsx`
- Modify: `apps/desktop/src/styles.css`

- [ ] **Step 1: 为行情字段增加稳定语义类**

紧凑表格中的字段使用：

```tsx
<span className="quote-security"><strong>{item.displayName}</strong></span>
<span className="quote-price"><strong>{money(item.quote.last)}</strong></span>
<span className="quote-position"><strong>{money(item.quantity)}</strong><small>成本 {money(item.costPrice)}</small></span>
<span className="quote-market-value"><strong>¥ {money(item.marketValue)}</strong></span>
<span className="quote-profit"><strong>{positive ? "+" : ""}¥ {money(item.profit)}</strong></span>
```

表头使用相同 grid area 类，非紧凑主窗口仍沿用现有六列。

- [ ] **Step 2: 编写紧凑表格语义测试**

渲染 compact 表格后断言六个核心值仍存在，并检查字段类：

```ts
expect(container.querySelector(".quote-security")).toHaveTextContent("贵州茅台");
expect(container.querySelector(".quote-price")).toHaveTextContent("1,450.00");
expect(container.querySelector(".quote-position")).toHaveTextContent("成本 1,400.00");
expect(container.querySelector(".quote-market-value")).toHaveTextContent("¥ 145,000.00");
expect(container.querySelector(".quote-profit")).toHaveTextContent("+¥ 5,000.00");
```

- [ ] **Step 3: 用 CSS 变量实现连续缩放**

`.ticker-window` 设置 `container-type:inline-size`，字号和间距基于：

```css
.ticker-window > header { padding:calc(8px * var(--ticker-ui-scale, 1)) calc(12px * var(--ticker-ui-scale, 1)); }
.ticker-total strong { font-size:calc(20px * var(--ticker-ui-scale, 1)); }
.quote-table.compact .quote-row { gap:calc(14px * var(--ticker-ui-scale, 1)); }
```

内容区达到最小缩放后允许滚动，并隐藏 WebKit/标准滚动条。

- [ ] **Step 4: 增加三种 grid 重排**

宽布局：

```css
grid-template-areas: "security price position market profit";
```

中布局：

```css
grid-template-areas:
  "security price profit"
  "security position market";
```

窄布局：

```css
grid-template-areas:
  "security price"
  "position profit"
  "market market";
```

所有规则限定在 `.ticker-window .quote-table.compact`，不影响主窗口。

- [ ] **Step 5: 运行组件与完整前端测试**

Run:

```bash
npm test -- --run src/components/PortfolioTable.test.tsx src/components/TickerWindow.test.tsx
npm test
```

Expected: 所有测试通过。

### Task 4: 完整构建与原生验证

**Files:**
- Modify: `openspec/changes/fix-native-transparency-and-responsive-ticker/tasks.md`
- Verify: `apps/desktop/src-tauri/target/release/bundle/macos/隐线股票定盘助手.app`

- [ ] **Step 1: 执行静态与规范验证**

Run:

```bash
npm run build --workspace @trading-assistant/desktop
cargo check --manifest-path apps/desktop/src-tauri/Cargo.toml
openspec validate fix-native-transparency-and-responsive-ticker --strict
git diff --check
```

Expected: 全部退出 0。

- [ ] **Step 2: 构建 macOS 发布产物**

Run:

```bash
npm run tauri build --workspace @trading-assistant/desktop
```

Expected: 生成 `.app` 和 `.dmg`。

- [ ] **Step 3: 原生目视验证**

启动 `.app` 后将 ticker 放在有明显颜色或图案的桌面/窗口上方，确认无文字区域真实显示后方内容；依次调整到约 `720×340`、`560×260`、`420×200`，确认缩放连续、核心字段完整、控制可用。

- [ ] **Step 4: 更新 OpenSpec 任务**

每完成一项即把 `tasks.md` 对应 `- [ ]` 改为 `- [x]`，最后确认 `openspec instructions apply` 返回 `all_done`。
