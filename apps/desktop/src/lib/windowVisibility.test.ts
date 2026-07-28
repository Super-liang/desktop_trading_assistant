// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { effectiveWindowVisibility, useWindowVisibility } from "./windowVisibility";

const native = vi.hoisted(() => ({
  listener: undefined as ((event: { payload: { label: string; visible: boolean } }) => void) | undefined,
  listen: vi.fn(async (_event: string, callback: typeof native.listener) => {
    native.listener = callback;
    return vi.fn();
  }),
  getByLabel: vi.fn(),
  window: { isVisible: vi.fn(), isMinimized: vi.fn() },
}));

vi.mock("@tauri-apps/api/event", () => ({ listen: native.listen }));
vi.mock("@tauri-apps/api/webviewWindow", () => ({
  WebviewWindow: { getByLabel: native.getByLabel },
}));

describe("windowVisibility", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    native.listener = undefined;
    native.window.isVisible.mockResolvedValue(true);
    native.window.isMinimized.mockResolvedValue(false);
    native.getByLabel.mockResolvedValue(native.window);
    delete (window as Window & { __TAURI_INTERNALS__?: unknown }).__TAURI_INTERNALS__;
  });

  it("最小化或隐藏时均视为不可见", () => {
    expect(effectiveWindowVisibility(true, false)).toBe(true);
    expect(effectiveWindowVisibility(true, true)).toBe(false);
    expect(effectiveWindowVisibility(false, false)).toBe(false);
  });

  it("浏览器开发模式保持查询可用", () => {
    const hook = renderHook(() => useWindowVisibility("main"));
    expect(hook.result.current).toBe(true);
    expect(native.listen).not.toHaveBeenCalled();
  });

  it("原生隐藏与恢复事件即时更新状态", async () => {
    (window as Window & { __TAURI_INTERNALS__?: unknown }).__TAURI_INTERNALS__ = {};
    const hook = renderHook(() => useWindowVisibility("main"));
    await waitFor(() => expect(native.listen).toHaveBeenCalled());
    expect(hook.result.current).toBe(true);

    act(() => native.listener?.({ payload: { label: "main", visible: false } }));
    expect(hook.result.current).toBe(false);
    act(() => native.listener?.({ payload: { label: "main", visible: true } }));
    expect(hook.result.current).toBe(true);
  });
});
