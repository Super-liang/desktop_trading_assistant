// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MarketStatusLights } from "./MarketStatusLights";

const { marketDataStatus } = vi.hoisted(() => ({ marketDataStatus: vi.fn() }));

vi.mock("../lib/api", () => ({ api: { marketDataStatus } }));

function renderLights(mode: "MARKET_SNAPSHOT" | "SINGLE_STOCK", singleSource: "EASTMONEY" | "XUEQIU") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>
    <MarketStatusLights mode={mode} singleSource={singleSource} />
  </QueryClientProvider>);
}

describe("MarketStatusLights", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("全市场模式将服务、缓存和上游链路名称显示为中文", async () => {
    marketDataStatus.mockResolvedValue({
      mode: "MARKET_SNAPSHOT", checkedAt: "2026-07-28T01:00:00Z", components: [
        { id: "SPRING_API", label: "Spring API", status: "UP" },
        { id: "AKSHARE_GATEWAY", label: "AKShare Gateway", status: "UP" },
        { id: "REDIS_SNAPSHOT_A_SHARE_SINA", label: "A Share", status: "UP", ageSeconds: 3 },
        { id: "REDIS_SNAPSHOT_HK_STOCK_SINA", label: "HK", status: "UP", ageSeconds: 4 },
        { id: "REDIS_SNAPSHOT_US_STOCK_SINA", label: "US", status: "UP", ageSeconds: 5 },
        { id: "UPSTREAM_A_SHARE:SNAPSHOT:SINA", label: "A_SHARE:SNAPSHOT:SINA", status: "UP" },
        { id: "UPSTREAM_HK_STOCK:SNAPSHOT:SINA", label: "HK_STOCK:SNAPSHOT:SINA", status: "UP" },
        { id: "UPSTREAM_US_STOCK:POSITION:SINA", label: "US_STOCK:POSITION:SINA", status: "UP" },
      ],
    });

    renderLights("MARKET_SNAPSHOT", "EASTMONEY");

    expect(await screen.findByText("后端服务")).toBeInTheDocument();
    expect(screen.getByText("AKShare 行情服务")).toBeInTheDocument();
    expect(screen.getByText("A股新浪缓存")).toBeInTheDocument();
    expect(screen.getByText("港股新浪缓存")).toBeInTheDocument();
    expect(screen.getByText("美股新浪缓存")).toBeInTheDocument();
    expect(screen.getByText("A股新浪行情")).toBeInTheDocument();
    expect(screen.getByText("港股新浪行情")).toBeInTheDocument();
    expect(screen.getByText("美股新浪行情")).toBeInTheDocument();
    expect(screen.queryByText(/Spring API|EASTMONEY/)).not.toBeInTheDocument();
  });

  it("单股模式将东方财富和雪球上游名称显示为中文", async () => {
    marketDataStatus.mockResolvedValue({
      mode: "SINGLE_STOCK", checkedAt: "2026-07-28T01:00:00Z", components: [
        { id: "UPSTREAM_SINGLE_EASTMONEY", label: "SINGLE_EASTMONEY", status: "UP" },
        { id: "UPSTREAM_SINGLE_XUEQIU", label: "SINGLE_XUEQIU", status: "UP" },
      ],
    });

    renderLights("SINGLE_STOCK", "XUEQIU");

    expect(await screen.findByText("东方财富单股行情")).toBeInTheDocument();
    expect(screen.getByText("雪球单股行情")).toBeInTheDocument();
  });

  it("未知链路保留后端标签作为兼容回退", async () => {
    marketDataStatus.mockResolvedValue({
      mode: "MARKET_SNAPSHOT", checkedAt: "2026-07-28T01:00:00Z",
      components: [{ id: "FUTURE_SOURCE", label: "未来行情服务", status: "UNKNOWN" }],
    });

    renderLights("MARKET_SNAPSHOT", "EASTMONEY");

    expect(await screen.findByText("未来行情服务")).toBeInTheDocument();
  });
});
