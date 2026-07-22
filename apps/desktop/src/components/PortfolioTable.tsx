import { Pencil, Trash2 } from "lucide-react";
import { marketPhase, money, percent } from "../lib/format";
import type { PortfolioItem } from "../types";

export function PortfolioTable({ items, compact = false, onDelete, onEdit }: {
  items: PortfolioItem[]; compact?: boolean; onDelete?: (id: string) => void;
  onEdit?: (item: PortfolioItem) => void;
}) {
  if (!items.length) return <div className="empty-state">还没有自选。添加一只股票，行情会在这里呼吸。</div>;
  return (
    <div className={`quote-table ${compact ? "compact" : ""}`}>
      <div className="quote-row quote-head">
        <span className="quote-security">证券</span>
        <span className="quote-price">现价 / 涨跌</span>
        <span className="quote-position">持仓 / 成本</span>
        <span className="quote-market-value">市值</span>
        <span className="quote-profit">浮动盈亏</span>
        {!compact && <span />}
      </div>
      {items.map((item) => {
        const positive = (item.profit ?? 0) >= 0;
        const quote = item.quote;
        return (
          <div className={`quote-row ${quote?.stale ? "stale" : ""}`} key={item.id}>
            <span className="security-cell quote-security"><strong>{item.displayName}</strong>
              <small>{item.instrumentId}{quote ? ` · ${marketPhase[quote.marketPhase] ?? quote.marketPhase}` : ""}</small>
              <small>{quote
                ? `${quote.source} · 最后刷新 ${new Date(quote.sourceTimestamp).toLocaleString("zh-CN", {
                  hour12: false, month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
                })}`
                : "暂无行情时间"}</small></span>
            <span className="quote-price"><strong>{quote ? money(quote.last) : "--"}</strong>
              {quote ? <small className={quote.change >= 0 ? "up" : "down"}>
                {quote.change >= 0 ? "+" : ""}{money(quote.change)} · {percent(quote.changePercent)}
              </small> : <small>行情暂不可用</small>}</span>
            <span className="quote-position"><strong>{money(item.quantity)}</strong><small>成本 {money(item.costPrice)}</small></span>
            <span className="quote-market-value"><strong>{item.marketValue == null ? "--" : `¥ ${money(item.marketValue)}`}</strong><small>
              {!quote ? "等待行情" : quote.stale ? "最后数据 · 已陈旧" : quote.demo ? "演示估算" : quote.delayed ? "延迟估算" : "实时估算"}
            </small></span>
            <span className="quote-profit"><strong className={item.profit == null ? "" : positive ? "up" : "down"}>
              {item.profit == null ? "--" : `${positive ? "+" : ""}¥ ${money(item.profit)}`}</strong>
              <small className={item.returnPercent == null ? "" : positive ? "up" : "down"}>
                {item.returnPercent == null ? "等待行情" : percent(item.returnPercent)}</small></span>
            {!compact && <span>
              <button className="icon-button" aria-label={`编辑 ${item.displayName}`} onClick={() => onEdit?.(item)}>
                <Pencil size={16} /></button>
              <button className="icon-button danger" aria-label={`删除 ${item.displayName}`} onClick={() => onDelete?.(item.id)}>
                <Trash2 size={16} /></button>
            </span>}
          </div>
        );
      })}
    </div>
  );
}
