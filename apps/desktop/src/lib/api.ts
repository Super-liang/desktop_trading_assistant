import type {
  AdminUser, AuthResponse, MarketDataConfig, MarketDataStatus, PortfolioSummary, SearchResult,
} from "../types";
import { useAuth } from "../store/auth";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const IS_TICKER = new URLSearchParams(window.location.search).get("view") === "ticker";

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
    throw new Error(problem.detail ?? `请求失败 (${response.status})`);
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
  portfolio: (mode?: MarketDataConfig["mode"], snapshotSource?: MarketDataConfig["snapshotSource"],
    singleSource?: MarketDataConfig["singleSource"]) => {
    const query = new URLSearchParams();
    if (mode) query.set("mode", mode);
    if (snapshotSource) query.set("snapshotSource", snapshotSource);
    if (singleSource) query.set("singleSource", singleSource);
    const suffix = query.size ? `?${query.toString()}` : "";
    return request<PortfolioSummary>(`/api/v1/portfolio/items${suffix}`);
  },
  search: (query: string, signal?: AbortSignal) =>
    request<SearchResult[]>(`/api/v1/instruments/search?query=${encodeURIComponent(query)}`,
      { signal }, true, 8_000),
  addItem: (body: {
    instrumentId: string; displayName: string; quantity: number; costPrice: number | null; sortOrder: number;
  }) => request("/api/v1/portfolio/items", { method: "POST", body: JSON.stringify(body) }),
  updateItem: (id: string, body: {
    instrumentId: string; displayName: string; quantity: number; costPrice: number | null; sortOrder: number;
  }) => request(`/api/v1/portfolio/items/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteItem: (id: string) => request<void>(`/api/v1/portfolio/items/${id}`, { method: "DELETE" }),
  users: (query = "") =>
    request<{ content: AdminUser[] }>(`/api/v1/admin/users?query=${encodeURIComponent(query)}`),
  setUserStatus: (id: string, status: "ACTIVE" | "DISABLED") =>
    request<AdminUser>(`/api/v1/admin/users/${id}/status`, {
      method: "PATCH", body: JSON.stringify({ status }),
    }),
  audits: () => request<{ content: Array<Record<string, string>> }>("/api/v1/admin/audits"),
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
};
