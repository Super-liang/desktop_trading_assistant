# Product Rename and Ticker Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前产品统一更名为“股票盯盘助手”，并让主界面“透明小窗”按钮可靠切换 ticker 的显示与隐藏。

**Architecture:** 产品名在当前界面、Tauri 发布元数据和交付文档中精确替换，历史 OpenSpec 保持不变。按钮每次点击查询 Tauri 原生窗口 `isVisible()`，以原生状态而非 React 缓存决定 `hide()` 或“会话同步 → `show()` → `setFocus()`”。

**Tech Stack:** React 19、TypeScript、Tauri 2、Vitest、Testing Library、Rust

---

### Task 1: 为原生窗口切换编写失败测试

**Files:**
- Modify: `apps/desktop/src/components/Dashboard.test.tsx`
- Test: `apps/desktop/src/components/Dashboard.test.tsx`

- [ ] **Step 1: Mock Tauri 窗口与事件模块**

使用 `vi.hoisted` 定义：

```ts
const native = vi.hoisted(() => ({
  emitTo: vi.fn(),
  getByLabel: vi.fn(),
  ticker: {
    isVisible: vi.fn(),
    hide: vi.fn(),
    show: vi.fn(),
    setFocus: vi.fn(),
  },
}));

vi.mock("@tauri-apps/api/webviewWindow", () => ({
  WebviewWindow: { getByLabel: native.getByLabel },
}));
vi.mock("@tauri-apps/api/event", () => ({ emitTo: native.emitTo }));
```

- [ ] **Step 2: 增加可见时隐藏测试**

```ts
native.ticker.isVisible.mockResolvedValue(true);
native.getByLabel.mockResolvedValue(native.ticker);
fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));
await waitFor(() => expect(native.ticker.hide).toHaveBeenCalledOnce());
expect(native.ticker.show).not.toHaveBeenCalled();
```

- [ ] **Step 3: 增加隐藏时显示测试**

```ts
native.ticker.isVisible.mockResolvedValue(false);
fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));
await waitFor(() => expect(native.emitTo).toHaveBeenCalledWith("ticker", "session-sync", useAuth.getState().session));
expect(native.ticker.show).toHaveBeenCalledOnce();
expect(native.ticker.setFocus).toHaveBeenCalledOnce();
```

- [ ] **Step 4: 运行测试并确认失败**

Run:

```bash
cd apps/desktop
npm test -- --run src/components/Dashboard.test.tsx
```

Expected: 隐藏分支或 `isVisible` 调用断言失败，因为当前实现只调用 `show()`。

### Task 2: 实现基于真实状态的 toggle

**Files:**
- Modify: `apps/desktop/src/components/Dashboard.tsx`
- Test: `apps/desktop/src/components/Dashboard.test.tsx`

- [ ] **Step 1: 将 `showTicker` 改为 `toggleTicker`**

```ts
async function toggleTicker() {
  try {
    const { WebviewWindow } = await import("@tauri-apps/api/webviewWindow");
    const ticker = await WebviewWindow.getByLabel("ticker");
    if (!ticker) return;
    if (await ticker.isVisible()) {
      await ticker.hide();
      return;
    }
    const { emitTo } = await import("@tauri-apps/api/event");
    await emitTo("ticker", "session-sync", useAuth.getState().session);
    await ticker.show();
    await ticker.setFocus();
  } catch {
    // 浏览器开发模式没有原生窗口，保持主界面可用。
  }
}
```

按钮改为 `onClick={toggleTicker}`。

- [ ] **Step 2: 增加外部隐藏后重新显示测试**

让 `isVisible` 连续返回 `false`、`false`，点击两次并断言 `show()` 和 `emitTo()` 均调用两次，证明逻辑不缓存本地状态。

- [ ] **Step 3: 运行定向测试**

Run:

```bash
cd apps/desktop
npm test -- --run src/components/Dashboard.test.tsx
```

Expected: Dashboard 所有测试通过。

### Task 3: 统一当前产品名称

**Files:**
- Modify: `apps/desktop/src-tauri/tauri.conf.json`
- Modify: `apps/desktop/src-tauri/src/lib.rs`
- Modify: `apps/desktop/src-tauri/Cargo.toml`
- Modify: `apps/desktop/index.html`
- Modify: `README.md`
- Modify: `docs/verification-report.md`

- [ ] **Step 1: 更新应用与窗口元数据**

写入精确名称：

```json
"productName": "股票盯盘助手"
```

主窗口 title、HTML `<title>`、托盘 tooltip 和 Cargo description 同步使用“股票盯盘助手”。

- [ ] **Step 2: 更新当前交付文档**

README 标题改为：

```md
# 股票盯盘助手
```

验证报告中的产物路径改为：

```text
apps/desktop/src-tauri/target/release/bundle/macos/股票盯盘助手.app
```

- [ ] **Step 3: 扫描命名范围**

Run:

```bash
rg -n "股票定盘助手" README.md docs apps --glob '!**/target/**' --glob '!**/tsbuildinfo'
```

Expected: 无匹配。不要对 `openspec/changes` 执行批量替换。

### Task 4: 完整验证与原生构建

**Files:**
- Modify: `openspec/changes/rename-product-and-toggle-ticker/tasks.md`
- Verify: `apps/desktop/src-tauri/target/release/bundle/macos/股票盯盘助手.app`

- [ ] **Step 1: 运行完整门禁**

Run:

```bash
cd apps/desktop && npm test && npm run build
cd src-tauri && cargo check
cd ../../../.. && openspec validate rename-product-and-toggle-ticker --strict
git diff --check
```

Expected: 全部退出 0。

- [ ] **Step 2: 构建 macOS 产物**

Run:

```bash
cd apps/desktop
npm run tauri build
```

Expected: 生成 `股票盯盘助手.app` 和 `股票盯盘助手_0.1.0_aarch64.dmg`。

- [ ] **Step 3: 原生按钮验证**

登录新 `.app`，第一次点击“透明小窗”显示 ticker，第二次点击隐藏；再次点击显示，并确认托盘与窗口标题使用新名称。

- [ ] **Step 4: 更新 OpenSpec**

每项验证完成后立即把对应任务改为 `[x]`，最后确认 `openspec instructions apply` 返回 `all_done`。
