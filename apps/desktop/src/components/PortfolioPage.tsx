import { Plus } from "lucide-react";
import type { PortfolioItem, PortfolioSummary } from "../types";
import { PortfolioTable } from "./PortfolioTable";

export function PortfolioPage({ data, loading, error, onAdd, onEdit, onDelete }: {
  data?: PortfolioSummary;
  loading: boolean;
  error?: Error | null;
  onAdd: () => void;
  onEdit: (item: PortfolioItem) => void;
  onDelete: (id: string) => void;
}) {
  return <main className="workspace portfolio-page">
    <header className="workspace-header"><div><p className="eyebrow">PORTFOLIO</p><h1>我的持仓</h1>
      <p>查看行情、持仓成本与当前浮动盈亏</p></div>
      <button className="primary-button small" onClick={onAdd}><Plus size={17} /> 添加持仓</button>
    </header>
    <section className="list-card">
      <div className="section-title"><div><p className="eyebrow">WATCHLIST</p><h2>自选与持仓</h2></div>
        <span>{data?.items.length ?? 0} 个标的</span></div>
      {loading ? <div className="empty-state">正在连接行情网关…</div>
        : error ? <div className="empty-state error">{error.message || "行情列表加载失败，请稍后重试"}</div>
          : <PortfolioTable items={data?.items ?? []} onDelete={onDelete} onEdit={onEdit} />}
    </section>
    <footer>{data?.calculationNotice ?? "浮盈亏仅供参考"} · 最后行情时间会随每条数据展示</footer>
  </main>;
}
