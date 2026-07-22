// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppErrorBoundary } from "./AppErrorBoundary";

function BrokenView(): never {
  throw new Error("render failed");
}

describe("AppErrorBoundary", () => {
  afterEach(cleanup);

  it("子组件异常时展示恢复界面并可重新加载", () => {
    const onReload = vi.fn();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    render(<AppErrorBoundary onReload={onReload}><BrokenView /></AppErrorBoundary>);

    expect(screen.getByRole("alert")).toHaveTextContent("界面暂时无法显示");
    fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
    expect(onReload).toHaveBeenCalledOnce();
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });
});
