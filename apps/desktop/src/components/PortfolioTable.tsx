import { Pencil, Trash2 } from "lucide-react";
import { memo } from "react";
import { marketPhase, money, percent, quoteTime } from "../lib/format";
import type { PortfolioItem } from "../types";

function PortfolioTableComponent({ items, compact = false, onDelete, onEdit }: {
  items: PortfolioItem[]; compact?: boolean; onDelete?: (id: string) => void;
  onEdit?: (item: PortfolioItem) => void;
}) {
  if (!items.length) return <div className="empty-state">还没有自选。添加一只股票，行情会在这里呼吸。</div>;

  if (!compact) {
    return <div className="quote-table portfolio-quote-list">
      {items.map((item) => {
        const positive = (item.profit ?? 0) >= 0;
        const quote = item.quote;
        const code = item.instrumentId.split(":").at(-1) ?? item.instrumentId;
        return <article className={`portfolio-quote-row ${quote?.stale ? "stale" : ""}`} key={item.id}>
          <div className="portfolio-security-line quote-security">
            <strong>{item.displayName}</strong>
            <span className="portfolio-code">{code}</span>
            <small>{quote ? `最后刷新 ${quoteTime(quote.sourceTimestamp)}` : "暂无行情时间"}</small>
          </div>
          <div className="portfolio-metrics">
            <span className="portfolio-metric quote-price">
              <small>现价</small><strong>{quote ? money(quote.last) : "--"}</strong>
              {quote ? <em className={quote.change >= 0 ? "up" : "down"}>
                {quote.change >= 0 ? "+" : ""}{money(quote.change)} · {percent(quote.changePercent)}
              </em> : <em>行情暂不可用</em>}
            </span>
            <span className="portfolio-metric quote-position">
              <small>持仓</small><strong>{money(item.quantity)}</strong><em>成本 {money(item.costPrice)}</em>
            </span>
            <span className="portfolio-metric quote-market-value">
              <small>市值</small><strong>{item.marketValue == null ? "--" : `¥ ${money(item.marketValue)}`}</strong>
              <em>{!quote ? "等待行情" : quote.stale ? "最后数据 · 已陈旧" : quote.demo
                ? "演示估算" : quote.delayed ? "延迟估算" : "实时估算"}</em>
            </span>
            <span className="portfolio-metric quote-profit">
              <small>浮动盈亏</small>
              <strong className={item.profit == null ? "" : positive ? "up" : "down"}>
                {item.profit == null ? "--" : `${positive ? "+" : ""}¥ ${money(item.profit)}`}
              </strong>
              <em className={item.returnPercent == null ? "" : positive ? "up" : "down"}>
                {item.returnPercent == null ? "等待行情" : percent(item.returnPercent)}
              </em>
            </span>
          </div>
          <div className="portfolio-row-actions">
            <button className="icon-button" aria-label={`编辑 ${item.displayName}`} onClick={() => onEdit?.(item)}>
              <Pencil size={15} /></button>
            <button className="icon-button danger" aria-label={`删除 ${item.displayName}`} onClick={() => onDelete?.(item.id)}>
              <Trash2 size={15} /></button>
          </div>
        </article>;
      })}
    </div>;
  }

  return (
    <div className="quote-table compact">
      <div className="quote-row quote-head">
        <span className="quote-security">证券</span>
        <span className="quote-price">现价 / 涨跌</span>
        <span className="quote-position">持仓 / 成本</span>
        <span className="quote-market-value">市值</span>
        <span className="quote-profit">浮动盈亏</span>
      </div>
      {items.map((item) => {
        const positive = (item.profit ?? 0) >= 0;
        const quote = item.quote;
        return (
          <div className={`quote-row ${quote?.stale ? "stale" : ""}`} key={item.id}>
            <span className="security-cell quote-security"><strong>{item.displayName}</strong>
              <small>{item.instrumentId}{quote ? ` · ${marketPhase[quote.marketPhase] ?? quote.marketPhase}` : ""}</small>
              <small>{quote
                ? `最后刷新 ${quoteTime(quote.sourceTimestamp)}`
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
          </div>
        );
      })}
    </div>
  );
}

export const PortfolioTable = memo(PortfolioTableComponent);
