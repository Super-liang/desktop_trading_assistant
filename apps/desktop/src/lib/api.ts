import type { AdminUser, AuthResponse, PortfolioSummary, SearchResult } from "../types";
import { useAuth } from "../store/auth";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const IS_TICKER = new URLSearchParams(window.location.search).get("view") === "ticker";

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const session = useAuth.getState().session;
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(session?.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}),
      ...init.headers,
    },
  });
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
  portfolio: () => request<PortfolioSummary>("/api/v1/portfolio/items"),
  search: (query: string) =>
    request<SearchResult[]>(`/api/v1/quotes/search?query=${encodeURIComponent(query)}`),
  addItem: (body: {
    instrumentId: string; displayName: string; quantity: number; costPrice: number; sortOrder: number;
  }) => request("/api/v1/portfolio/items", { method: "POST", body: JSON.stringify(body) }),
  updateItem: (id: string, body: {
    instrumentId: string; displayName: string; quantity: number; costPrice: number; sortOrder: number;
  }) => request(`/api/v1/portfolio/items/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  deleteItem: (id: string) => request<void>(`/api/v1/portfolio/items/${id}`, { method: "DELETE" }),
  users: (query = "") =>
    request<{ content: AdminUser[] }>(`/api/v1/admin/users?query=${encodeURIComponent(query)}`),
  setUserStatus: (id: string, status: "ACTIVE" | "DISABLED") =>
    request<AdminUser>(`/api/v1/admin/users/${id}/status`, {
      method: "PATCH", body: JSON.stringify({ status }),
    }),
  audits: () => request<{ content: Array<Record<string, string>> }>("/api/v1/admin/audits"),
};
