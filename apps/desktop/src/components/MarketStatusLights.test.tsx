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
        { id: "REDIS_SNAPSHOT_EASTMONEY", label: "Redis Eastmoney", status: "UP", ageSeconds: 3 },
        { id: "REDIS_SNAPSHOT_SINA", label: "Redis Sina", status: "UP", ageSeconds: 4 },
        { id: "UPSTREAM_SNAPSHOT_EASTMONEY", label: "SNAPSHOT_EASTMONEY", status: "UP" },
        { id: "UPSTREAM_SNAPSHOT_SINA", label: "SNAPSHOT_SINA", status: "UP" },
      ],
    });

    renderLights("MARKET_SNAPSHOT", "EASTMONEY");

    expect(await screen.findByText("后端服务")).toBeInTheDocument();
    expect(screen.getByText("AKShare 行情服务")).toBeInTheDocument();
    expect(screen.getByText("东方财富行情缓存")).toBeInTheDocument();
    expect(screen.getByText("新浪行情缓存")).toBeInTheDocument();
    expect(screen.getByText("东方财富全市场行情")).toBeInTheDocument();
    expect(screen.getByText("新浪全市场行情")).toBeInTheDocument();
    expect(screen.queryByText(/Spring API|SNAPSHOT_/)).not.toBeInTheDocument();
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
