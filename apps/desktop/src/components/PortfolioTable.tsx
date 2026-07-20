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
        const positive = item.profit >= 0;
        return (
          <div className={`quote-row ${item.quote.stale ? "stale" : ""}`} key={item.id}>
            <span className="security-cell quote-security"><strong>{item.displayName}</strong>
              <small>{item.instrumentId} · {marketPhase[item.quote.marketPhase] ?? item.quote.marketPhase}</small>
              <small>{item.quote.source} · {new Date(item.quote.sourceTimestamp).toLocaleTimeString("zh-CN")}</small></span>
            <span className="quote-price"><strong>{money(item.quote.last)}</strong>
              <small className={item.quote.change >= 0 ? "up" : "down"}>
                {item.quote.change >= 0 ? "+" : ""}{money(item.quote.change)} · {percent(item.quote.changePercent)}
              </small></span>
            <span className="quote-position"><strong>{money(item.quantity)}</strong><small>成本 {money(item.costPrice)}</small></span>
            <span className="quote-market-value"><strong>¥ {money(item.marketValue)}</strong><small>
              {item.quote.stale ? "行情已过期" : item.quote.demo ? "演示估算" : item.quote.delayed ? "延迟估算" : "实时估算"}
            </small></span>
            <span className="quote-profit"><strong className={positive ? "up" : "down"}>
              {positive ? "+" : ""}¥ {money(item.profit)}</strong>
              <small className={positive ? "up" : "down"}>{percent(item.returnPercent)}</small></span>
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
