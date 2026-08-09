import { Plus } from "lucide-react";
import type { Market, PortfolioItem, PortfolioReturns, PortfolioSummary } from "../types";
import { PortfolioTable } from "./PortfolioTable";

export function PortfolioPage({ data, returns, loading, error, onAdd, onEdit, onDelete, market,
  onMarketChange, marketStatus }: {
  data?: PortfolioSummary;
  returns?: PortfolioReturns;
  loading: boolean;
  error?: Error | null;
  onAdd: () => void;
  onEdit: (item: PortfolioItem) => void;
  onDelete: (id: string) => void;
  market?: Market;
  onMarketChange?: (market: Market) => void;
  marketStatus?: string;
}) {
  const selectedMarket = market ?? "A_SHARE";
  const labels: Record<Market, string> = {
    A_SHARE: "A股持仓", HK_STOCK: "港股持仓", US_STOCK: "美股持仓", PUBLIC_FUND: "公募基金持仓",
  };
  const visibleItems = (data?.items ?? []).filter((item) =>
    (item.market ?? "A_SHARE") === selectedMarket);
  const itemReturns = new Map(returns?.groups.flatMap((group) => group.items)
    .map((item) => [item.positionId, item]) ?? []);
  return <main className="workspace portfolio-page">
    <header className="workspace-header"><div><p className="eyebrow">PORTFOLIO</p><h1>我的持仓</h1>
      <p>查看行情、持仓成本与当前浮动盈亏</p></div>
      <button className="primary-button small" onClick={onAdd}><Plus size={17} /> 添加持仓</button>
    </header>
    <nav className="portfolio-market-tabs" aria-label="持仓市场">
      {(Object.keys(labels) as Market[]).map((market) => <button key={market}
        className={selectedMarket === market ? "active" : ""}
        onClick={() => onMarketChange?.(market)}>{labels[market]}</button>)}
    </nav>
    <section className="list-card">
      <div className="section-title"><div><p className="eyebrow">WATCHLIST</p><h2>自选与持仓</h2></div>
        <span>{marketStatus ?? "状态未知"} · {visibleItems.length} 个标的</span></div>
      {loading ? <div className="empty-state">正在连接行情网关…</div>
        : error ? <div className="empty-state error">{error.message || "行情列表加载失败，请稍后重试"}</div>
          : <PortfolioTable items={visibleItems} itemReturns={itemReturns}
            onDelete={onDelete} onEdit={onEdit} />}
    </section>
    <footer>{data?.calculationNotice ?? "浮盈亏仅供参考"} · 最后行情时间会随每条数据展示</footer>
  </main>;
}
