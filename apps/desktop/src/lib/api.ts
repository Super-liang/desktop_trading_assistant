import type {
  AdminHolding, AdminUser, AdminUserOverview, AuthResponse, MarketDataConfig, MarketDataStatus,
  MarketIndexQuote, MarketStatus, Page, PerformanceSummary, PortfolioReturns, PortfolioSummary, SearchResult, UserOperationAudit,
} from "../types";
import type { Market } from "../types";
import { useAuth } from "../store/auth";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const IS_TICKER = new URLSearchParams(window.location.search).get("view") === "ticker";

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string,
    readonly details: Record<string, unknown> = {}) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init: RequestInit = {}, retry = true,
  timeoutMs = 12_000): Promise<T> {
  const session = useAuth.getState().session;
  const controller = new AbortController();
  let timedOut = false;
  const timeout = window.setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
  const abort = () => controller.abort(init.signal?.reason);
  if (init.signal?.aborted) abort();
  else init.signal?.addEventListener("abort", abort, { once: true });
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...init,
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        ...(session?.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}),
        ...init.headers,
      },
    });
  } catch (reason) {
    if (timedOut) throw new Error("请求超时，请检查网络后重试");
    if (init.signal?.aborted) throw reason;
    throw new Error("无法连接 API，请检查网络或服务状态");
  } finally {
    window.clearTimeout(timeout);
    init.signal?.removeEventListener("abort", abort);
  }
  if (response.status === 401 && retry && session?.refreshToken && !path.includes("/auth/refresh")) {
    // 刷新令牌只能由主窗轮换；小窗等待主窗广播，避免双 WebView 并发触发重放保护。
    if (IS_TICKER) {
      await new Promise((resolve) => window.setTimeout(resolve, 800));
      const coordinated = useAuth.getState().session;
      if (coordinated?.accessToken && coordinated.accessToken !== session.accessToken) {
        return request<T>(path, init, false);
      }
      throw new Error("会话正在刷新，请稍后重试");
    }
    try {
      const refreshed = await request<AuthResponse>("/api/v1/auth/refresh", {
        method: "POST",
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      }, false);
      useAuth.getState().setSession(refreshed);
      return request<T>(path, init, false);
    } catch {
      useAuth.getState().clear();
    }
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ detail: "服务暂时不可用" }));
    throw new ApiError(problem.detail ?? `请求失败 (${response.status})`, response.status,
      typeof problem.code === "string" ? problem.code : undefined, problem);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  register: (body: { email: string; displayName: string; password: string }) =>
    request<AuthResponse>("/api/v1/auth/register", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    request<AuthResponse>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(body) }),
  logout: (refreshToken: string) =>
    request<void>("/api/v1/auth/logout", {
      method: "POST", body: JSON.stringify({ refreshToken }),
    }),
  logoutAll: () => request<void>("/api/v1/me/logout-all", { method: "POST" }),
  deleteAccount: (password: string) =>
    request<void>("/api/v1/me", { method: "DELETE", body: JSON.stringify({ password }) }),
  changePassword: (body: { currentPassword: string; newPassword: string; confirmPassword: string }) =>
    request<void>("/api/v1/me/change-password", { method: "POST", body: JSON.stringify(body) }),
  performance: () => request<PerformanceSummary>("/api/v1/me/performance"),
  portfolioReturns: () => request<PortfolioReturns>("/api/v1/me/returns"),
  portfolio: (mode?: MarketDataConfig["mode"], snapshotSource?: MarketDataConfig["snapshotSource"],
    singleSource?: MarketDataConfig["singleSource"], market?: Market) => {
    const query = new URLSearchParams();
    if (mode) query.set("mode", mode);
    if (snapshotSource) query.set("snapshotSource", snapshotSource);
    if (singleSource) query.set("singleSource", singleSource);
    if (market) query.set("market", market);
    const suffix = query.size ? `?${query.toString()}` : "";
    return request<PortfolioSummary>(`/api/v1/portfolio/items${suffix}`);
  },
  search: (query: string, signal?: AbortSignal, market: Market = "A_SHARE") =>
    request<SearchResult[]>(`/api/v1/instruments/search?market=${market}&query=${encodeURIComponent(query)}`,
      { signal }, true, 8_000),
  addItem: (body: {
    instrumentId: string; displayName: string; market?: Market; openedOn?: string;
    quantity: number; costPrice: number | null; sortOrder: number;
  }) => request("/api/v1/portfolio/items", { method: "POST", body: JSON.stringify(body) }),
  accumulateItem: (id: string, body: { quantity: number; costPrice: number }) =>
    request(`/api/v1/portfolio/items/${encodeURIComponent(id)}/accumulate`, {
      method: "POST", body: JSON.stringify(body),
    }),
  updateItem: (id: string, body: {
    instrumentId: string; displayName: string; market?: Market; openedOn?: string;
    quantity: number; costPrice: number | null; sortOrder: number;
  }) => request(`/api/v1/portfolio/items/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteItem: (id: string) => request<void>(`/api/v1/portfolio/items/${id}`, { method: "DELETE" }),
  users: (query = "") =>
    request<{ content: AdminUser[] }>(`/api/v1/admin/users?query=${encodeURIComponent(query)}`),
  setUserStatus: (id: string, status: "ACTIVE" | "DISABLED") =>
    request<AdminUser>(`/api/v1/admin/users/${id}/status`, {
      method: "PATCH", body: JSON.stringify({ status }),
    }),
  audits: () => request<{ content: Array<Record<string, string>> }>("/api/v1/admin/audits"),
  adminUserOverview: (userId: string) =>
    request<AdminUserOverview>(`/api/v1/admin/users/${encodeURIComponent(userId)}/overview`),
  adminUserHoldings: (userId: string) =>
    request<{ content: AdminHolding[] }>(
      `/api/v1/admin/users/${encodeURIComponent(userId)}/holdings`),
  adminUserAudits: (userId: string, filters: {
    action?: string; from?: string; to?: string; page?: number; size?: number;
  } = {}) => {
    const query = new URLSearchParams();
    if (filters.action) query.set("action", filters.action);
    if (filters.from) query.set("from", filters.from);
    if (filters.to) query.set("to", filters.to);
    query.set("page", String(filters.page ?? 0));
    query.set("size", String(filters.size ?? 20));
    return request<Page<UserOperationAudit>>(
      `/api/v1/admin/users/${encodeURIComponent(userId)}/audits?${query.toString()}`);
  },
  marketDataConfig: () => request<MarketDataConfig>("/api/v1/market-data/config"),
  updateMarketDataConfig: (body: Pick<MarketDataConfig,
    "provider" | "mode" | "snapshotSource" | "singleSource" | "refreshSeconds">) =>
    request<MarketDataConfig>("/api/v1/admin/market-data/config", {
      method: "PUT", body: JSON.stringify(body),
    }),
  marketDataStatus: (mode?: MarketDataConfig["mode"],
    singleSource?: MarketDataConfig["singleSource"]) => {
    const query = new URLSearchParams();
    if (mode) query.set("mode", mode);
    if (singleSource) query.set("singleSource", singleSource);
    const suffix = query.size ? `?${query.toString()}` : "";
    return request<MarketDataStatus>(`/api/v1/market-data/status${suffix}`);
  },
  marketOverview: (source: MarketDataConfig["snapshotSource"]) =>
    request<MarketIndexQuote[]>(`/api/v1/market-overview/a-share?source=${encodeURIComponent(source)}`),
  marketStatuses: () => request<MarketStatus[]>("/api/v1/markets/status"),
};
