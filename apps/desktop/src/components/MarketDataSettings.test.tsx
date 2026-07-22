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
    apiMocks.marketDataConfig.mockResolvedValue({
      provider: "AKSHARE",
      mode: "MARKET_SNAPSHOT",
      snapshotSource: "EASTMONEY",
      singleSource: "EASTMONEY",
      refreshSeconds: 10,
      updatedAt: "2026-07-22T01:00:00Z",
      providers: [{ id: "AKSHARE", name: "AKShare", modes: ["MARKET_SNAPSHOT", "SINGLE_STOCK"] }],
    });
    apiMocks.marketDataStatus.mockResolvedValue({ mode: "MARKET_SNAPSHOT", checkedAt: "", components: [] });
    apiMocks.updateMarketDataConfig.mockResolvedValue({});
  });

  it("普通用户只能查看不能保存", async () => {
    renderSettings(false);

    expect(await screen.findByText("AKShare 行情配置")).toBeInTheDocument();
    expect(screen.getByText("当前账号仅可查看配置")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /保存配置/ })).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /单只股票模式/ })).toBeDisabled();
  });

  it("管理员切换雪球单股模式并保存", async () => {
    renderSettings(true);
    fireEvent.click(await screen.findByRole("button", { name: /单只股票模式/ }));
    fireEvent.click(screen.getByLabelText(/雪球/));
    fireEvent.click(screen.getByRole("button", { name: /保存配置/ }));

    await waitFor(() => expect(apiMocks.updateMarketDataConfig).toHaveBeenCalledWith(expect.objectContaining({
      mode: "SINGLE_STOCK", singleSource: "XUEQIU",
    })));
  });

  it("选择新浪时自动提升为安全频率", async () => {
    renderSettings(true);
    fireEvent.click(await screen.findByLabelText(/新浪财经/));

    expect(screen.getByLabelText(/定时频率/)).toHaveValue(30);
  });
});
