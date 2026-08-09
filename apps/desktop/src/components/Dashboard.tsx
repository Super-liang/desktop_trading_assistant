import { useQuery } from "@tanstack/react-query";
import {
  Activity, BriefcaseBusiness, Database, EyeOff, Home, Settings, Shield,
  Sparkles, UserRound,
} from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { summarizeQuoteSource } from "../lib/quoteSource";
import { portfolioRefetchInterval, useMarketPreferences } from "../lib/marketPreferences";
import { useAuth } from "../store/auth";
import { AddPositionDialog } from "./AddPositionDialog";
import { AdminPanel } from "./AdminPanel";
import { AccountPage } from "./AccountPage";
import { ChangePasswordDialog } from "./ChangePasswordDialog";
import { EditPositionDialog } from "./EditPositionDialog";
import { HomePage } from "./HomePage";
import { MarketDataSettings } from "./MarketDataSettings";
import { PortfolioPage } from "./PortfolioPage";
import type { Market, PortfolioItem } from "../types";
import { isTauriRuntime, useWindowVisibility } from "../lib/windowVisibility";
import { PORTFOLIO_SYNC_EVENT, TICKER_DATA_READY_EVENT } from "../lib/desktopEvents";

type ActiveView = "HOME" | "PORTFOLIO" | "MARKET_DATA" | "ADMIN" | "ACCOUNT";

async function toggleTicker() {
  try {
    const { invoke } = await import("@tauri-apps/api/core");
    await invoke("toggle_ticker_window");
  } catch {
    // 浏览器开发模式没有原生窗口，保持主界面可用。
  }
}

function resetMainSession(clear: () => void) {
  if ("__TAURI_INTERNALS__" in window) window.location.reload();
  else clear();
}

export function Dashboard() {
  const session = useAuth((state) => state.session);
  const clear = useAuth((state) => state.clear);
  const [activeView, setActiveView] = useState<ActiveView>("HOME");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<PortfolioItem | null>(null);
  const [portfolioMarket, setPortfolioMarket] = useState<Market>("A_SHARE");
  const [changingPassword, setChangingPassword] = useState(false);
  const visible = useWindowVisibility("main");
  const marketConfig = useQuery({
    queryKey: ["market-data-config"], queryFn: api.marketDataConfig,
    refetchInterval: 30_000, enabled: visible,
  });
  const preferences = useMarketPreferences(
    marketConfig.data?.mode ?? "MARKET_SNAPSHOT",
    marketConfig.data?.snapshotSource ?? "SINA",
    marketConfig.data?.singleSource ?? "EASTMONEY",
  );
  const marketMode = preferences.mode;
  const portfolio = useQuery({
    queryKey: ["portfolio", preferences.snapshotSource, preferences.singleSource, marketMode],
    queryFn: () => api.portfolio(marketMode, preferences.snapshotSource, preferences.singleSource),
    refetchInterval: portfolioRefetchInterval(
      marketMode, preferences.singleRefreshSeconds, marketConfig.data?.refreshSeconds ?? 30,
    ),
    enabled: visible && marketConfig.isSuccess,
  });
  const returnsQuery = useQuery({
    queryKey: ["me-returns"], queryFn: api.portfolioReturns,
    refetchInterval: portfolioRefetchInterval(
      marketMode, preferences.singleRefreshSeconds, marketConfig.data?.refreshSeconds ?? 30,
    ),
    enabled: visible,
  });
  const marketStatuses = useQuery({
    queryKey: ["market-statuses"], queryFn: api.marketStatuses,
    refetchInterval: 60_000, enabled: visible,
  });

  useEffect(() => {
    if (!visible || !portfolio.data || !isTauriRuntime()) return;
    let unlisten: (() => void) | undefined;
    const payload = {
      mode: marketMode,
      snapshotSource: preferences.snapshotSource,
      singleSource: preferences.singleSource,
      data: portfolio.data,
    };
    import("@tauri-apps/api/event").then(async ({ emitTo, listen }) => {
      await emitTo("ticker", PORTFOLIO_SYNC_EVENT, payload).catch(() => undefined);
      unlisten = await listen(TICKER_DATA_READY_EVENT, () => {
        void emitTo("ticker", PORTFOLIO_SYNC_EVENT, payload).catch(() => undefined);
      });
    }).catch(() => undefined);
    return () => unlisten?.();
  }, [marketMode, portfolio.data, preferences.singleSource, preferences.snapshotSource, visible]);

  if (!session) return null;
  const refreshToken = session.refreshToken;

  function logout() {
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
    try {
      await api.deleteItem(id);
      await portfolio.refetch();
      await returnsQuery.refetch();
    } catch (reason) {
      window.alert(reason instanceof Error ? reason.message : "删除失败");
    }
  }

  const data = portfolio.data;
  const quoteSource = summarizeQuoteSource(data?.items ?? [], portfolio.isError);
  const navigate = (view: ActiveView) => {
    setActiveView(view);
  };
  const mainContent = activeView === "PORTFOLIO"
    ? <PortfolioPage data={data} returns={returnsQuery.data} loading={portfolio.isLoading}
      error={portfolio.error instanceof Error ? portfolio.error : null}
      market={portfolioMarket} onMarketChange={setPortfolioMarket}
      marketStatus={marketStatuses.data?.find((item) => item.market === portfolioMarket)?.phase}
      onAdd={() => setAdding(true)} onEdit={setEditing} onDelete={(id) => void remove(id)} />
    : activeView === "MARKET_DATA"
      ? <MarketDataSettings isAdmin={session.role === "ADMIN"} onBack={() => navigate("HOME")} />
      : activeView === "ADMIN"
        ? <AdminPanel onBack={() => navigate("HOME")} />
        : activeView === "ACCOUNT"
          ? <AccountPage isAdmin={session.role === "ADMIN"}
            onChangePassword={() => setChangingPassword(true)} onLogout={logout}
            onLogoutAll={() => void logoutAll()} onDeleteAccount={() => void deleteAccount()}
            onOpenAdmin={() => navigate("ADMIN")} />
        : <HomePage data={data} returns={returnsQuery.data}
          returnsLoading={returnsQuery.isLoading} returnsError={returnsQuery.isError}
          quoteBadge={quoteSource.badge} quoteNotice={quoteSource.notice}
          marketMode={marketMode} snapshotSource={preferences.snapshotSource}
          singleSource={preferences.singleSource} visible={visible}
          refreshSeconds={marketConfig.data?.refreshSeconds}
          singleRefreshSeconds={preferences.singleRefreshSeconds}
          onAdd={() => setAdding(true)} onOpenPortfolio={() => navigate("PORTFOLIO")} />;

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><Activity size={20} /> 隐线</div>
      <nav aria-label="桌面端导航">
        <button className={activeView === "HOME" ? "active" : ""} onClick={() => navigate("HOME")}>
          <Home size={18} /> 首页</button>
        <button className={activeView === "PORTFOLIO" ? "active" : ""} onClick={() => navigate("PORTFOLIO")}>
          <BriefcaseBusiness size={18} /> 我的持仓</button>
        <button onClick={toggleTicker}><EyeOff size={18} /> 透明小窗</button>
        <button className={activeView === "MARKET_DATA" ? "active" : ""} onClick={() => navigate("MARKET_DATA")}>
          <Database size={18} /> 实时行情源</button>
        {session.role === "ADMIN" && <button className={activeView === "ADMIN" ? "active" : ""}
          onClick={() => navigate("ADMIN")}><Shield size={18} /> 用户管理</button>}
        <button className={activeView === "ACCOUNT" ? "active" : ""}
          onClick={() => navigate("ACCOUNT")}><UserRound size={18} /> 我的</button>
        <button disabled><Settings size={18} /> 个性设置 <span className="phase">二期</span></button>
        <button disabled><Sparkles size={18} /> AI 分析 <span className="phase">三期</span></button>
      </nav>
      <div className="sidebar-bottom">
        <div className="shortcut-card"><small>老板键</small><strong>⌘ / Ctrl + Shift + H</strong>
          <span>全局隐藏全部行情窗口</span></div>
      </div>
    </aside>
    <nav className="mobile-nav" aria-label="移动端导航">
      <button className={activeView === "HOME" ? "active" : ""} onClick={() => navigate("HOME")}>
        <Home size={19} /><span>首页</span></button>
      <button className={activeView === "PORTFOLIO" ? "active" : ""} onClick={() => navigate("PORTFOLIO")}>
        <BriefcaseBusiness size={19} /><span>我的持仓</span></button>
      <button className={activeView === "MARKET_DATA" ? "active" : ""} onClick={() => navigate("MARKET_DATA")}>
        <Database size={19} /><span>实时行情源</span></button>
      <button className={activeView === "ACCOUNT" ? "active" : ""}
        onClick={() => navigate("ACCOUNT")}><UserRound size={19} /><span>我的</span></button>
    </nav>
    <div className="view-host">{mainContent}</div>
    {adding && <AddPositionDialog onClose={() => setAdding(false)} onAdded={(market) => {
      setAdding(false);
      setPortfolioMarket(market);
      setActiveView("PORTFOLIO");
      void portfolio.refetch();
      void returnsQuery.refetch();
    }} />}
    {editing && <EditPositionDialog item={editing} onClose={() => setEditing(null)} onSaved={() => {
      setEditing(null);
      void portfolio.refetch();
      void returnsQuery.refetch();
    }} />}
    {changingPassword && <ChangePasswordDialog onClose={() => setChangingPassword(false)} />}
  </div>;
}
