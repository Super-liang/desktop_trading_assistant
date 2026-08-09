// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MarketDataSettings } from "./MarketDataSettings";

const apiMocks = vi.hoisted(() => ({
  marketDataConfig: vi.fn(),
  marketDataStatus: vi.fn(),
  updateMarketDataConfig: vi.fn(),
}));

vi.mock("../lib/api", () => ({ api: apiMocks }));

function renderSettings(isAdmin: boolean) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>
    <MarketDataSettings isAdmin={isAdmin} onBack={vi.fn()} />
  </QueryClientProvider>);
}

describe("MarketDataSettings", () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    apiMocks.marketDataConfig.mockResolvedValue({
      provider: "AKSHARE",
      mode: "MARKET_SNAPSHOT",
      snapshotSource: "EASTMONEY",
      singleSource: "EASTMONEY",
      refreshSeconds: 30,
      updatedAt: "2026-07-22T01:00:00Z",
      providers: [{ id: "AKSHARE", name: "AKShare", modes: ["MARKET_SNAPSHOT", "SINGLE_STOCK"] }],
    });
    apiMocks.marketDataStatus.mockResolvedValue({ mode: "MARKET_SNAPSHOT", checkedAt: "", components: [] });
    apiMocks.updateMarketDataConfig.mockResolvedValue({});
  });

  it("普通用户看到固定新浪快照并可切换本机模式", async () => {
    renderSettings(false);

    expect(await screen.findByText("AKShare 行情配置")).toBeInTheDocument();
    expect(await screen.findByLabelText(/新浪财经全市场行情/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/东方财富.*全市场/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /单只股票模式/ }));
    fireEvent.click(screen.getByRole("button", { name: /保存本机设置/ }));
    expect(window.localStorage.getItem("market.snapshotSource")).toBe("SINA");
    expect(window.localStorage.getItem("market.mode")).toBe("SINGLE_STOCK");
    expect(apiMocks.updateMarketDataConfig).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /单只股票模式/ })).toBeEnabled();
  });

  it("普通用户在全市场模式只能查看服务端刷新频率", async () => {
    renderSettings(false);

    const refreshInput = await screen.findByLabelText(/服务端快照刷新频率/);
    expect(refreshInput).toBeDisabled();
    expect(refreshInput).toHaveValue(30);
  });

  it("管理员切换雪球单股模式并保存", async () => {
    renderSettings(true);
    fireEvent.click(await screen.findByRole("button", { name: /单只股票模式/ }));
    fireEvent.click(screen.getByLabelText(/雪球/));
    fireEvent.click(screen.getByRole("button", { name: /保存配置/ }));

    await waitFor(() => expect(apiMocks.updateMarketDataConfig).toHaveBeenCalledWith(expect.objectContaining({
      mode: "SINGLE_STOCK", snapshotSource: "SINA", singleSource: "XUEQIU",
    })));
  });

  it("全市场服务端刷新频率最小为 30 秒", async () => {
    renderSettings(true);

    expect(await screen.findByLabelText(/刷新频率/)).toHaveValue(30);
    expect(screen.getByLabelText(/刷新频率/)).toHaveAttribute("min", "30");
  });

  it("单股模式可选择 2 秒客户端查询频率", async () => {
    apiMocks.marketDataConfig.mockResolvedValue({
      provider: "AKSHARE", mode: "SINGLE_STOCK", snapshotSource: "EASTMONEY",
      singleSource: "EASTMONEY", refreshSeconds: 30, updatedAt: "", providers: [],
    });
    renderSettings(false);
    fireEvent.click(await screen.findByRole("button", { name: "2 秒" }));
    fireEvent.click(screen.getByRole("button", { name: /保存本机设置/ }));
    expect(window.localStorage.getItem("market.singleRefreshSeconds")).toBe("2");
  });

  it("普通用户可选择本机雪球单股源且不修改系统默认值", async () => {
    apiMocks.marketDataConfig.mockResolvedValue({
      provider: "AKSHARE", mode: "SINGLE_STOCK", snapshotSource: "EASTMONEY",
      singleSource: "EASTMONEY", refreshSeconds: 30, updatedAt: "", providers: [],
    });
    renderSettings(false);

    fireEvent.click(await screen.findByLabelText(/雪球/));
    fireEvent.click(screen.getByRole("button", { name: /保存本机设置/ }));

    expect(window.localStorage.getItem("market.singleSource")).toBe("XUEQIU");
    expect(apiMocks.updateMarketDataConfig).not.toHaveBeenCalled();
  });
});
