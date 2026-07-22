import { useQuery } from "@tanstack/react-query";
import { Activity, Database, EyeOff, LogOut, Plus, Settings, Shield, Sparkles, UserX } from "lucide-react";
import { useState } from "react";
import { api } from "../lib/api";
import { money } from "../lib/format";
import { summarizeQuoteSource } from "../lib/quoteSource";
import { useAuth } from "../store/auth";
import { AddPositionDialog } from "./AddPositionDialog";
import { PortfolioTable } from "./PortfolioTable";
import { AdminPanel } from "./AdminPanel";
import { EditPositionDialog } from "./EditPositionDialog";
import { MarketDataSettings } from "./MarketDataSettings";
import { MarketStatusLights } from "./MarketStatusLights";
import type { PortfolioItem } from "../types";

async function toggleTicker() {
  try {
    const { WebviewWindow } = await import("@tauri-apps/api/webviewWindow");
    const ticker = await WebviewWindow.getByLabel("ticker");
    if (!ticker) return;
    if (await ticker.isVisible()) {
      await ticker.hide();
      return;
    }
    const { emitTo } = await import("@tauri-apps/api/event");
    await emitTo("ticker", "session-sync", useAuth.getState().session);
    await ticker.show();
    await ticker.setFocus();
  } catch {
    // 浏览器开发模式没有原生窗口，保持主界面可用。
  }
}

function resetMainSession(clear: () => void) {
  if ("__TAURI_INTERNALS__" in window) {
    window.location.reload();
  } else {
    clear();
  }
}

export function Dashboard() {
  const session = useAuth((state) => state.session);
  const clear = useAuth((state) => state.clear);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<PortfolioItem | null>(null);
  const [admin, setAdmin] = useState(false);
  const [marketDataSettings, setMarketDataSettings] = useState(false);
  const portfolio = useQuery({
    queryKey: ["portfolio"],
    queryFn: api.portfolio,
    refetchInterval: 2000,
  });

  // App 会在会话清空后切回登录页；子组件可能先收到 store 更新，需安全结束本帧。
  if (!session) return null;
  const refreshToken = session.refreshToken;

  function logout() {
    // 原生端重载可见主 WebView，彻底清除内存会话并规避 macOS 隐藏 WebView 抖动。
    const revokeRequest = api.logout(refreshToken).catch(() => undefined);
    resetMainSession(clear);
    void revokeRequest;
  }

  async function logoutAll() {
    if (!window.confirm("确认退出全部设备？所有刷新会话都会失效。")) return;
    await api.logoutAll();
    resetMainSession(clear);
  }

  async function deleteAccount() {
    const password = window.prompt("注销账号会删除自选与持仓且不可恢复。请输入当前密码：");
    if (!password || !window.confirm("最后确认：永久注销当前账号？")) return;
    try {
      await api.deleteAccount(password);
      resetMainSession(clear);
    } catch (reason) {
      window.alert(reason instanceof Error ? reason.message : "注销失败");
    }
  }

  async function remove(id: string) {
    await api.deleteItem(id);
    await portfolio.refetch();
  }

  if (admin) return <AdminPanel onBack={() => setAdmin(false)} />;
  if (marketDataSettings) return <MarketDataSettings isAdmin={session.role === "ADMIN"}
    onBack={() => setMarketDataSettings(false)} />;
  const data = portfolio.data;
  const quoteSource = summarizeQuoteSource(data?.items ?? [], portfolio.isError);
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><Activity size={20} /> 隐线</div>
        <nav>
          <button className="active"><Activity size={18} /> 盯盘工作台</button>
          <button onClick={toggleTicker}><EyeOff size={18} /> 透明小窗</button>
          <button onClick={() => setMarketDataSettings(true)}><Database size={18} /> 实时行情源</button>
          {session.role === "ADMIN" && <button onClick={() => setAdmin(true)}><Shield size={18} /> 用户管理</button>}
          <button disabled><Settings size={18} /> 个性设置 <span className="phase">二期</span></button>
          <button disabled><Sparkles size={18} /> AI 分析 <span className="phase">三期</span></button>
        </nav>
        <div className="sidebar-bottom">
          <div className="shortcut-card"><small>老板键</small><strong>⌘ / Ctrl + Shift + H</strong>
            <span>全局隐藏全部行情窗口</span></div>
          <button onClick={logout}><LogOut size={17} /> 退出登录</button>
          <button onClick={logoutAll}><Shield size={17} /> 退出全部设备</button>
          <button className="danger-text" onClick={deleteAccount}><UserX size={17} /> 注销账号</button>
        </div>
      </aside>
      <main className="workspace">
        <header className="workspace-header">
          <div><p className="eyebrow">MARKET DESK · A SHARE</p><h1>我的盯盘</h1>
            <p>{new Date().toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "long" })}</p></div>
          <button className="primary-button small" onClick={() => setAdding(true)}><Plus size={17} /> 添加自选</button>
        </header>
        <div className="demo-banner"><span>{quoteSource.badge}</span>
          {quoteSource.notice}</div>
        <MarketStatusLights />
        <section className="summary-grid">
          <article className="summary-card featured"><small>持仓总市值</small>
            <strong>¥ {money(data?.totalMarketValue ?? 0)}</strong><span>{quoteSource.estimate}</span></article>
          <article className="summary-card"><small>累计浮盈亏</small>
            <strong className={(data?.totalProfit ?? 0) >= 0 ? "up" : "down"}>
              {(data?.totalProfit ?? 0) >= 0 ? "+" : ""}¥ {money(data?.totalProfit ?? 0)}</strong>
            <span>不含费用与税费</span></article>
          <article className="summary-card"><small>数据状态</small><strong className="status-live">
            <i /> {quoteSource.status}</strong>
            <span>每 2 秒刷新 · 来源可追溯</span></article>
        </section>
        <section className="list-card">
          <div className="section-title"><div><p className="eyebrow">WATCHLIST</p><h2>自选与持仓</h2></div>
            <span>{data?.items.length ?? 0} 个标的</span></div>
          {portfolio.isLoading ? <div className="empty-state">正在连接行情网关…</div>
            : portfolio.isError ? <div className="empty-state error">无法连接 API，请确认服务端已启动。</div>
            : <PortfolioTable items={data?.items ?? []} onDelete={remove} onEdit={setEditing} />}
        </section>
        <footer>{data?.calculationNotice ?? "浮盈亏仅供参考"} · 最后行情时间会随每条数据展示</footer>
      </main>
      {adding && <AddPositionDialog onClose={() => setAdding(false)}
        onAdded={() => { setAdding(false); void portfolio.refetch(); }} />}
      {editing && <EditPositionDialog item={editing} onClose={() => setEditing(null)}
        onSaved={() => { setEditing(null); void portfolio.refetch(); }} />}
    </div>
  );
}
