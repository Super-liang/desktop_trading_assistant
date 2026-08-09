import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useRef } from "react";
import { api } from "../lib/api";
import type { MarketDataConfig } from "../types";

function number(value?: number | null) {
  return value == null ? "--" : value.toLocaleString("zh-CN", { maximumFractionDigits: 2 });
}

export function MarketIndexCarousel({ source, enabled }: {
  source: MarketDataConfig["snapshotSource"];
  enabled: boolean;
}) {
  const viewport = useRef<HTMLDivElement>(null);
  const query = useQuery({
    queryKey: ["a-share-market-overview", source],
    queryFn: () => api.marketOverview(source),
    enabled,
    refetchInterval: enabled ? 30_000 : false,
  });

  function move(direction: -1 | 1) {
    viewport.current?.scrollBy({
      left: direction * Math.max(260, viewport.current.clientWidth * 0.92),
      behavior: "smooth",
    });
  }

  return <section className="index-overview" aria-label="A 股大盘指数">
    <div className="index-overview-title">
      <div><p className="eyebrow">MARKET OVERVIEW</p><h2>大盘行情</h2></div>
      <div className="index-controls">
        <button type="button" aria-label="查看前一组指数" onClick={() => move(-1)}><ChevronLeft size={17} /></button>
        <button type="button" aria-label="查看后一组指数" onClick={() => move(1)}><ChevronRight size={17} /></button>
      </div>
    </div>
    {query.isError && <div className="index-empty">指数行情暂不可用，服务恢复后会自动刷新</div>}
    {query.isLoading && <div className="index-empty">正在读取指数行情…</div>}
    {query.data && <div className="index-viewport" ref={viewport} tabIndex={0}>
      {query.data.map((item) => {
        const changeClass = item.change == null ? "" : item.change >= 0 ? "up" : "down";
        return <article className={`index-card ${item.stale || !item.available ? "stale" : ""}`}
          key={item.instrumentId}>
          <div><strong>{item.name}</strong><small>{item.code}</small></div>
          <b>{number(item.price)}</b>
          <span className={changeClass}>{item.change == null ? "--" : `${item.change >= 0 ? "+" : ""}${number(item.change)}`}
            <em>{item.changePercent == null ? "--" : `${item.changePercent >= 0 ? "+" : ""}${number(item.changePercent)}%`}</em></span>
          <small>{item.available ? `更新 ${new Date(item.quoteAsOf).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}`
            : item.lastSuccessAt ? `暂不可用 · 最后成功 ${new Date(item.lastSuccessAt).toLocaleString("zh-CN")}` : "暂不可用 · 尚无成功行情"}</small>
        </article>;
      })}
    </div>}
  </section>;
}
