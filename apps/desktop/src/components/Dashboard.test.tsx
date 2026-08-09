// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../store/auth";
import { Dashboard } from "./Dashboard";

const { logout, portfolio, performance, portfolioReturns, marketDataConfig, marketDataStatus, search, addItem } = vi.hoisted(() => ({
  logout: vi.fn(() => new Promise<void>(() => undefined)),
  portfolio: vi.fn().mockResolvedValue({
    items: [], totalMarketValue: 0, totalProfit: 0, unavailableQuoteCount: 0,
    calculationNotice: "测试",
  }),
  performance: vi.fn(),
  portfolioReturns: vi.fn(),
  marketDataConfig: vi.fn(),
  marketDataStatus: vi.fn().mockResolvedValue({
    mode: "MARKET_SNAPSHOT", checkedAt: "2026-07-22T01:00:00Z", components: [],
  }),
  search: vi.fn(),
  addItem: vi.fn(),
}));

const native = vi.hoisted(() => ({
  emitTo: vi.fn(),
  listen: vi.fn().mockResolvedValue(vi.fn()),
  invoke: vi.fn(),
}));

vi.mock("../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/api")>();
  return {
    ...actual,
    api: {
    logout,
    portfolio,
    performance,
    portfolioReturns,
    marketDataConfig,
    marketDataStatus,
    logoutAll: vi.fn(),
    deleteAccount: vi.fn(),
    deleteItem: vi.fn(),
    changePassword: vi.fn(),
    search,
    addItem,
    accumulateItem: vi.fn(),
    updateItem: vi.fn(),
    },
  };
});

vi.mock("@tauri-apps/api/event", () => ({
  emitTo: native.emitTo,
  listen: native.listen,
}));

vi.mock("@tauri-apps/api/core", () => ({ invoke: native.invoke }));

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
        { id: "REDIS_SNAPSHOT_A_SHARE_SINA", label: "A股新浪缓存", status: "UP", ageSeconds: 3 },
        { id: "UPSTREAM_A_SHARE:SNAPSHOT:SINA", label: "A_SHARE:SNAPSHOT:SINA", status: "UP", detail: "120 ms" },
      ],
    });
    performance.mockResolvedValue({
      dailyProfit: 123.45,
      dailyReturnPercent: 1.23,
      yearProfit: -88.5,
      yearReturnPercent: -0.75,
      annualizedReturnPercent: null,
      statisticsStartDate: "2026-07-01",
      calculatedAt: "2026-07-27T01:00:00Z",
      status: "ACCUMULATING",
      missingQuoteCount: 0,
      referenceNotice: "基于手工持仓和行情计算的参考收益",
    });
    portfolioReturns.mockResolvedValue({
      groups: [{ market: "A_SHARE", currency: "CNY", dailyProfit: 123.45,
        dailyReturnPercent: 1.23, holdingProfit: -88.5, holdingReturnPercent: -0.75,
        dailyStatus: "PARTIAL", unavailableDailyCount: 1, items: [] }],
      calculatedAt: "2026-07-27T01:00:00Z",
      calculationNotice: "按市场和币种分别计算的参考收益",
    });
    search.mockResolvedValue([{
      instrumentId: "SSE:600519", code: "600519", name: "贵州茅台",
      exchange: "SSE", assetType: "STOCK",
    }]);
    addItem.mockResolvedValue({});
    native.invoke.mockResolvedValue(undefined);
    useAuth.getState().setSession({
      accessToken: "access",
      refreshToken: "refresh",
      expiresAt: "2099-01-01T00:00:00Z",
      role: "USER",
    });
  });

  it("服务端撤销请求不返回时也立即清除本地会话", () => {
    renderDashboard();
    const desktopNavigation = screen.getByRole("navigation", { name: "桌面端导航" });
    fireEvent.click(within(desktopNavigation).getByRole("button", { name: "我的" }));
    fireEvent.click(screen.getByRole("button", { name: /退出登录/ }));

    expect(logout).toHaveBeenCalledWith("refresh");
    expect(useAuth.getState().session).toBeNull();
  });

  it("点击透明小窗交由原生命令按需创建或销毁", async () => {
    renderDashboard();

    fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));

    await waitFor(() => expect(native.invoke).toHaveBeenCalledWith("toggle_ticker_window"));
  });

  it("原生小窗命令失败时仍保持主界面可用", async () => {
    native.invoke.mockRejectedValue(new Error("browser mode"));
    renderDashboard();

    fireEvent.click(screen.getByRole("button", { name: "透明小窗" }));

    await waitFor(() => expect(native.invoke).toHaveBeenCalledWith("toggle_ticker_window"));
    expect(screen.getByRole("heading", { name: "我的盯盘" })).toBeInTheDocument();
  });

  it("展示各行情链路的独立联通指示", async () => {
    renderDashboard();

    fireEvent.click(await screen.findByRole("button", { name: /联通检测/ }));
    expect(await screen.findByText("AKShare 行情服务")).toBeInTheDocument();
    expect(screen.getByText("A股新浪缓存")).toBeInTheDocument();
    expect(screen.getByText("A股新浪行情")).toBeInTheDocument();
    expect(screen.queryByText(/Spring API|EASTMONEY/)).not.toBeInTheDocument();
    expect(screen.queryByText("DEMO")).not.toBeInTheDocument();
  });

  it("首页不展示持仓证券、数量与成本，进入我的持仓后才展示", async () => {
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

    expect(await screen.findByRole("heading", { name: "我的盯盘" })).toBeInTheDocument();
    expect(screen.queryByText("贵州茅台")).not.toBeInTheDocument();
    expect(await screen.findByText("服务端快照刷新频率：30 秒")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: /我的持仓/ })[0]);
    expect(await screen.findByText("贵州茅台")).toBeInTheDocument();
  });

  it("金额与比率统一切换，并按市场展示日收益和持有收益", async () => {
    renderDashboard();

    expect(await screen.findByText("+¥ 123.45")).toBeInTheDocument();
    expect(screen.getByText("-¥ 88.50")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "比率" }));

    expect(screen.getByText("+1.23%")).toBeInTheDocument();
    expect(screen.getByText("-0.75%")).toBeInTheDocument();
    expect(screen.getByText(/部分行情缺失/)).toBeInTheDocument();
    expect(screen.getByText(/参考收益/)).toBeInTheDocument();
  });

  it("手机导航保持四个主入口并直接进入我的页面", async () => {
    renderDashboard();

    const navigation = await screen.findByRole("navigation", { name: "移动端导航" });
    expect(within(navigation).getAllByRole("button")).toHaveLength(4);
    expect(within(navigation).getByRole("button", { name: "首页" })).toBeInTheDocument();
    expect(within(navigation).getByRole("button", { name: "我的持仓" })).toBeInTheDocument();
    expect(within(navigation).getByRole("button", { name: "实时行情源" })).toBeInTheDocument();
    fireEvent.click(within(navigation).getByRole("button", { name: "我的" }));

    expect(await screen.findByRole("heading", { name: "我的" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /修改密码/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /退出登录/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /退出全部设备/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /注销账号/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /用户管理/ })).not.toBeInTheDocument();
  });

  it("管理员可从手机端我的页面进入用户管理", async () => {
    useAuth.getState().setSession({
      accessToken: "access", refreshToken: "refresh", expiresAt: "2099-01-01T00:00:00Z", role: "ADMIN",
    });
    renderDashboard();

    const navigation = await screen.findByRole("navigation", { name: "移动端导航" });
    fireEvent.click(within(navigation).getByRole("button", { name: "我的" }));
    fireEvent.click(await within(screen.getByRole("main")).findByRole("button", { name: /用户管理/ }));

    expect(await screen.findByRole("heading", { name: "用户管理" })).toBeInTheDocument();
  });

  it("从首页新增成功后跳转我的持仓并刷新共享查询", async () => {
    renderDashboard();
    fireEvent.click(await screen.findByRole("button", { name: /添加持仓/ }));
    fireEvent.change(screen.getByPlaceholderText("输入代码或名称，例如 600519"), {
      target: { value: "600519" },
    });
    fireEvent.click(await screen.findByText("贵州茅台"));
    fireEvent.click(screen.getByRole("button", { name: "加入盯盘" }));

    expect(await screen.findByRole("heading", { name: "我的持仓" })).toBeInTheDocument();
    expect(addItem).toHaveBeenCalledWith(expect.objectContaining({ instrumentId: "SSE:600519" }));
    expect(portfolio.mock.calls.length).toBeGreaterThan(1);
  });

  it("新增失败时保留首页、对话框和已输入内容", async () => {
    addItem.mockRejectedValueOnce(new Error("保存失败，请重试"));
    renderDashboard();
    fireEvent.click(await screen.findByRole("button", { name: /添加持仓/ }));
    const searchInput = screen.getByPlaceholderText("输入代码或名称，例如 600519");
    fireEvent.change(searchInput, { target: { value: "600519" } });
    fireEvent.click(await screen.findByText("贵州茅台"));
    fireEvent.change(screen.getByLabelText("持仓数量"), { target: { value: "100" } });
    fireEvent.change(screen.getByLabelText("单位成本"), { target: { value: "1200.5" } });
    fireEvent.click(screen.getByRole("button", { name: "加入盯盘" }));

    expect(await screen.findByText("保存失败，请重试")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "我的盯盘" })).toBeInTheDocument();
    expect(searchInput).toHaveValue("600519");
    expect(screen.getByLabelText("持仓数量")).toHaveValue("100");
    expect(screen.getByLabelText("单位成本")).toHaveValue("1200.5");
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
      "SINGLE_STOCK", "SINA", "XUEQIU"));
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
