// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "../store/auth";
import { api } from "./api";

describe("API privacy contracts", () => {
  beforeEach(() => {
    useAuth.getState().setSession({
      accessToken: "access", refreshToken: "refresh", expiresAt: "2099-01-01T00:00:00Z", role: "ADMIN",
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  it("改密只提交三项约定字段", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);
    await api.changePassword({
      currentPassword: "OldPass123!", newPassword: "NewPass123!", confirmPassword: "NewPass123!",
    });

    expect(fetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/v1\/me\/change-password$/),
      expect.objectContaining({ method: "POST", body: JSON.stringify({
        currentPassword: "OldPass123!", newPassword: "NewPass123!", confirmPassword: "NewPass123!",
      }) }));
  });

  it("管理详情与持仓请求都把 userId 固定在路径", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] }), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetch);
    await api.adminUserHoldings("user/unsafe");
    expect(fetch.mock.calls[0][0]).toMatch(/\/admin\/users\/user%2Funsafe\/holdings$/);
  });

  it("用户审计筛选保留 userId 边界并编码过滤条件", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [], page: 1, size: 20, totalElements: 0, totalPages: 0,
    }), { status: 200, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetch);
    await api.adminUserAudits("user-1", {
      action: "PORTFOLIO_DELETED", from: "2026-07-01T00:00:00Z", page: 1, size: 20,
    });
    const url = String(fetch.mock.calls[0][0]);
    expect(url).toContain("/api/v1/admin/users/user-1/audits?");
    expect(url).toContain("action=PORTFOLIO_DELETED");
    expect(url).toContain("from=2026-07-01T00%3A00%3A00Z");
    expect(url).toContain("page=1");
  });
});
