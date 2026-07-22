// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../store/auth";
import { Dashboard } from "./Dashboard";

const { logout, portfolio, marketDataConfig, marketDataStatus } = vi.hoisted(() => ({
  logout: vi.fn(() => new Promise<void>(() => undefined)),
  portfolio: vi.fn().mockResolvedValue({
    items: [], totalMarketValue: 0, totalProfit: 0, unavailableQuoteCount: 0,
    calculationNotice: "测试",
  }),
  marketDataConfig: vi.fn(),
  marketDataStatus: vi.fn().mockResolvedValue({
    mode: "MARKET_SNAPSHOT", checkedAt: "2026-07-22T01:00:00Z", components: [],
  }),
}));

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

vi.mock("../lib/api", () => ({
  api: {
    logout,
    portfolio,
    marketDataConfig,
    marketDataStatus,
  },
}));

vi.mock("@tauri-apps/api/webviewWindow", () => ({
  WebviewWindow: { getByLabel: native.getByLabel },
}));

vi.mock("@tauri-apps/api/event", () => ({
  emitTo: native.emitTo,
}));

function renderDashboard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <Dashboard />
    </QueryClientProvider>,
  );
}

describe("Dashboard", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    portfolio.mockResolvedValue({
      items: [], totalMarketValue: 0, totalProfit: 0, unavailableQuoteCount: 0,
      calculationNotice: "测试",
    });
    marketDataConfig.mockResolvedValue({
      provider: "AKSHARE", mode: "MARKET_SNAPSHOT", snapshotSource: "EASTMONEY",
      singleSource: "EASTMONEY", refreshSeconds: 30, updatedAt: "", providers: [],
    });
    marketDataStatus.mockResolvedValue({
      mode: "MARKET_SNAPSHOT", checkedAt: "2026-07-22T01:00:00Z",
      components: [
        { id: "SPRING_API", label: "Spring API", status: "UP", detail: "服务正常" },
        { id: "AKSHARE_GATEWAY", label: "AKShare 网关", status: "UP", detail: "UP" },
        { id: "REDIS_SNAPSHOT", label: "Redis 快照", status: "UP", ageSeconds: 3 },
        { id: "UPSTREAM", label: "SNAPSHOT_EASTMONEY", status: "UP", detail: "120 ms" },
      ],
    });
    native.getByLabel.mockResolvedValue(native.ticker);
    useAuth.getState().setSession({
      accessToken: "access",
      refreshToken: "refresh",
      expiresAt: "2099-01-01T00:00:00Z",
      role: "USER",
    });
  });

  it("服务端撤销请求不返回时也立即清除本地会话", () => {
    renderDashboard();

    fireEvent.click(screen.getByRole("button", { name: "退出登录" }));

    expect(logout).toHaveBeenCalledWith("refresh");
    expect(useAuth.getState().session).toBeNull();
  });

  it("ticker 已显示时点击透明小窗会隐藏", async () => {
    native.ticker.isVisible.mockResolvedValue(true);
    renderDashboard();

    fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));

    await waitFor(() => expect(native.ticker.hide).toHaveBeenCalledOnce());
    expect(native.ticker.show).not.toHaveBeenCalled();
    expect(native.emitTo).not.toHaveBeenCalled();
  });

  it("ticker 已隐藏时同步会话、显示并聚焦", async () => {
    native.ticker.isVisible.mockResolvedValue(false);
    renderDashboard();
    const session = useAuth.getState().session;

    fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));

    await waitFor(() => {
      expect(native.emitTo).toHaveBeenCalledWith("ticker", "session-sync", session);
    });
    expect(native.ticker.show).toHaveBeenCalledOnce();
    expect(native.ticker.setFocus).toHaveBeenCalledOnce();
    expect(native.ticker.hide).not.toHaveBeenCalled();
  });

  it("ticker 被外部隐藏后再次点击仍会重新查询并显示", async () => {
    native.ticker.isVisible.mockResolvedValue(false);
    renderDashboard();
    const button = screen.getByRole("button", { name: "透明小窗" });

    fireEvent.click(button);
    await waitFor(() => expect(native.ticker.show).toHaveBeenCalledTimes(1));

    fireEvent.click(button);
    await waitFor(() => expect(native.ticker.show).toHaveBeenCalledTimes(2));
    expect(native.ticker.isVisible).toHaveBeenCalledTimes(2);
    expect(native.emitTo).toHaveBeenCalledTimes(2);
  });

  it("没有 Tauri 原生窗口时点击不会破坏主界面", async () => {
    native.getByLabel.mockRejectedValue(new Error("browser mode"));
    renderDashboard();

    fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));

    await waitFor(() => expect(native.getByLabel).toHaveBeenCalledWith("ticker"));
    expect(screen.getByRole("heading", { name: "我的盯盘" })).toBeInTheDocument();
  });

  it("展示各行情链路的独立联通指示", async () => {
    renderDashboard();

    expect(await screen.findByText("AKShare 网关")).toBeInTheDocument();
    expect(screen.getByText("Redis 快照")).toBeInTheDocument();
    expect(screen.getByText("SNAPSHOT_EASTMONEY")).toBeInTheDocument();
    expect(screen.queryByText("DEMO")).not.toBeInTheDocument();
  });

  it("部分响应省略 quote 字段时仍保持首页可见", async () => {
    portfolio.mockResolvedValue({
      items: [{
        id: "1", instrumentId: "SSE:600519", displayName: "贵州茅台",
        quantity: 1, costPrice: 1000, sortOrder: 0, marketValue: null,
        profit: null, returnPercent: null,
      }],
      totalMarketValue: 0, totalProfit: 0, unavailableQuoteCount: 1,
      calculationNotice: "测试",
    });
    renderDashboard();

    expect(await screen.findByText("贵州茅台")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "我的盯盘" })).toBeInTheDocument();
    expect(screen.getByText("服务端快照刷新频率：30 秒")).toBeInTheDocument();
  });

  it("单股模式显示并采用本机客户端查询频率", async () => {
    window.localStorage.setItem("market.singleRefreshSeconds", "20");
    window.localStorage.setItem("market.singleSource", "XUEQIU");
    window.localStorage.setItem("market.mode", "SINGLE_STOCK");
    marketDataConfig.mockResolvedValue({
      provider: "AKSHARE", mode: "SINGLE_STOCK", snapshotSource: "EASTMONEY",
      singleSource: "EASTMONEY", refreshSeconds: 30, updatedAt: "", providers: [],
    });
    renderDashboard();

    expect(await screen.findByText("客户端查询频率：20 秒")).toBeInTheDocument();
    await waitFor(() => expect(portfolio).toHaveBeenCalledWith(
      "SINGLE_STOCK", "EASTMONEY", "XUEQIU"));
    await waitFor(() => expect(marketDataStatus).toHaveBeenCalledWith("SINGLE_STOCK", "XUEQIU"));
  });

  it("本机全市场模式覆盖服务端单股默认模式", async () => {
    window.localStorage.setItem("market.mode", "MARKET_SNAPSHOT");
    marketDataConfig.mockResolvedValue({
      provider: "AKSHARE", mode: "SINGLE_STOCK", snapshotSource: "SINA",
      singleSource: "XUEQIU", refreshSeconds: 30, updatedAt: "", providers: [],
    });
    renderDashboard();

    expect(await screen.findByText("服务端快照刷新频率：30 秒")).toBeInTheDocument();
    await waitFor(() => expect(portfolio).toHaveBeenCalledWith(
      "MARKET_SNAPSHOT", "SINA", "XUEQIU"));
  });
});
