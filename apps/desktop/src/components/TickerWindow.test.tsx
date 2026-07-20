// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TICKER_TEXT_OPACITY_STORAGE_KEY } from "../lib/tickerAppearance";
import { TickerWindow } from "./TickerWindow";

const { portfolio } = vi.hoisted(() => ({ portfolio: vi.fn() }));

vi.mock("../lib/api", () => ({
  api: {
    portfolio,
  },
}));

let resizeCallback: ResizeObserverCallback | undefined;

class ResizeObserverMock {
  constructor(callback: ResizeObserverCallback) {
    resizeCallback = callback;
  }
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe("TickerWindow", () => {
  afterEach(cleanup);

  beforeEach(() => {
    portfolio.mockReset();
    portfolio.mockResolvedValue({
      items: [],
      totalProfit: 0,
      totalMarketValue: 0,
    });
    window.localStorage.clear();
    resizeCallback = undefined;
    vi.stubGlobal("ResizeObserver", ResizeObserverMock);
  });

  it("按实际行情显示 AKSHARE 来源和延迟提示", async () => {
    portfolio.mockResolvedValue({
      items: [{
        id: "1", instrumentId: "SSE:600519", displayName: "贵州茅台",
        quantity: 100, costPrice: 1400, sortOrder: 0, marketValue: 145000,
        profit: 5000, returnPercent: 3.57,
        quote: {
          instrumentId: "SSE:600519", name: "贵州茅台", last: 1450,
          previousClose: 1440, open: 1441, high: 1460, low: 1430,
          change: 10, changePercent: 0.69, volume: 1000,
          marketPhase: "CONTINUOUS", source: "AKSHARE",
          sourceTimestamp: "2026-07-20T01:31:00Z",
          receivedAt: "2026-07-20T01:31:01Z",
          delayed: true, stale: false, demo: false,
        },
      }],
      totalProfit: 5000,
      totalMarketValue: 145000,
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <TickerWindow />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("隐线 · AKSHARE")).toBeInTheDocument();
    expect(screen.getByText(/公开延迟行情/)).toBeInTheDocument();
    expect(screen.queryByText(/演示行情/)).not.toBeInTheDocument();
  });

  it("恢复、即时渲染并持久化文字透明度，且不低于 20%", async () => {
    window.localStorage.setItem(TICKER_TEXT_OPACITY_STORAGE_KEY, "70");
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <TickerWindow />
      </QueryClientProvider>,
    );

    const ticker = container.querySelector<HTMLElement>(".ticker-window");
    expect(screen.getByText("70%")).toBeInTheDocument();
    expect(ticker?.style.getPropertyValue("--ticker-text-opacity")).toBe("0.7");

    const decrease = screen.getByRole("button", { name: "降低文字透明度" });
    for (let index = 0; index < 8; index += 1) fireEvent.click(decrease);

    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(ticker?.style.getPropertyValue("--ticker-text-opacity")).toBe("0.2");
    await waitFor(() => {
      expect(window.localStorage.getItem(TICKER_TEXT_OPACITY_STORAGE_KEY)).toBe("20");
    });
  });

  it("根据容器宽高即时更新缩放变量和响应式布局", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <TickerWindow />
      </QueryClientProvider>,
    );
    const ticker = container.querySelector<HTMLElement>(".ticker-window");

    act(() => {
      resizeCallback?.([
        { contentRect: { width: 720, height: 340 } } as ResizeObserverEntry,
      ], {} as ResizeObserver);
    });
    expect(ticker?.style.getPropertyValue("--ticker-ui-scale")).toBe("1");
    expect(ticker).toHaveAttribute("data-layout", "wide");

    act(() => {
      resizeCallback?.([
        { contentRect: { width: 430, height: 210 } } as ResizeObserverEntry,
      ], {} as ResizeObserver);
    });
    expect(ticker?.style.getPropertyValue("--ticker-ui-scale")).toBe("0.7");
    expect(ticker).toHaveAttribute("data-layout", "narrow");
  });
});
