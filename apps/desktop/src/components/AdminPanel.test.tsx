// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../lib/api";
import { AdminPanel } from "./AdminPanel";

vi.mock("../lib/api", () => ({ api: {
  users: vi.fn(), setUserStatus: vi.fn(), adminUserOverview: vi.fn(),
  adminUserHoldings: vi.fn(), adminUserAudits: vi.fn(),
} }));

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><AdminPanel onBack={vi.fn()} /></QueryClientProvider>);
}

describe("AdminPanel", () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.users).mockResolvedValue({ content: [{
      id: "user-1", email: "never@example.com", displayName: "新用户", role: "USER",
      status: "ACTIVE", createdAt: "2026-07-01T00:00:00Z",
    }] });
    vi.mocked(api.adminUserOverview).mockResolvedValue({
      user: { id: "user-1", email: "never@example.com", displayName: "新用户", role: "USER",
        status: "ACTIVE", createdAt: "2026-07-01T00:00:00Z" },
      holdingCount: 1,
      performance: { dailyProfit: 10, dailyReturnPercent: 0.5, yearProfit: 20,
        yearReturnPercent: 1, annualizedReturnPercent: null, statisticsStartDate: "2026-07-01",
        calculatedAt: "2026-07-27T01:00:00Z", status: "ACCUMULATING", missingQuoteCount: 0,
        referenceNotice: "参考收益" },
    });
    vi.mocked(api.adminUserHoldings).mockResolvedValue({ content: [{
      instrumentId: "SSE:600519", displayName: "贵州茅台", exchange: "SSE", quoteAvailable: true,
    }] });
    vi.mocked(api.adminUserAudits).mockResolvedValue({ content: [{
      id: "audit-1", action: "PORTFOLIO_CREATED", instrumentId: "SSE:600519",
      instrumentName: "贵州茅台", result: "SUCCESS", createdAt: "2026-07-27T01:00:00Z",
    }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it("从未登录用户显示明确空状态", async () => {
    renderPanel();
    expect(await screen.findByText("尚未登录")).toBeInTheDocument();
  });

  it("进入详情后所有请求都绑定选中 userId 且不展示隐私字段", async () => {
    renderPanel();
    fireEvent.click(await screen.findByRole("button", { name: "查看 新用户" }));

    await waitFor(() => expect(api.adminUserOverview).toHaveBeenCalledWith("user-1"));
    expect(api.adminUserHoldings).toHaveBeenCalledWith("user-1");
    expect(api.adminUserAudits).toHaveBeenCalledWith("user-1", expect.objectContaining({ page: 0 }));
    expect(await screen.findByText("贵州茅台")).toBeInTheDocument();
    expect(screen.queryByText(/数量|成本|总市值/)).not.toBeInTheDocument();
  });

  it("审计动作筛选仍强制携带 userId", async () => {
    renderPanel();
    fireEvent.click(await screen.findByRole("button", { name: "查看 新用户" }));
    fireEvent.change(await screen.findByLabelText("操作类型"), { target: { value: "PORTFOLIO_DELETED" } });
    await waitFor(() => expect(api.adminUserAudits).toHaveBeenLastCalledWith("user-1",
      expect.objectContaining({ action: "PORTFOLIO_DELETED" })));
  });

  it("权限错误在详情页明确展示", async () => {
    vi.mocked(api.adminUserOverview).mockRejectedValueOnce(new Error("无权访问用户详情"));
    renderPanel();
    fireEvent.click(await screen.findByRole("button", { name: "查看 新用户" }));
    expect(await screen.findByText("无权访问用户详情")).toBeInTheDocument();
  });
});
