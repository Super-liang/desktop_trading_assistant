import { useEffect, useRef, useState, type CSSProperties } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { money } from "../lib/format";
import { summarizeQuoteSource } from "../lib/quoteSource";
import {
  normalizeTickerTextOpacity,
  readTickerTextOpacity,
  saveTickerTextOpacity,
  TICKER_TEXT_OPACITY_STEP,
} from "../lib/tickerAppearance";
import {
  calculateTickerScale,
  getTickerLayoutMode,
  type TickerLayoutMode,
} from "../lib/tickerLayout";
import { PortfolioTable } from "./PortfolioTable";

async function drag() {
  const { getCurrentWindow } = await import("@tauri-apps/api/window");
  await getCurrentWindow().startDragging();
}
async function hide() {
  const { getCurrentWindow } = await import("@tauri-apps/api/window");
  await getCurrentWindow().hide();
}

export function TickerWindow() {
  const portfolio = useQuery({ queryKey: ["portfolio"], queryFn: api.portfolio, refetchInterval: 2000 });
  const [textOpacity, setTextOpacity] = useState(readTickerTextOpacity);
  const [layout, setLayout] = useState<{ scale: number; mode: TickerLayoutMode }>({
    scale: 1,
    mode: "wide",
  });
  const tickerRef = useRef<HTMLElement>(null);

  useEffect(() => {
    saveTickerTextOpacity(textOpacity);
  }, [textOpacity]);

  useEffect(() => {
    const ticker = tickerRef.current;
    if (!ticker) return;
    const observer = new ResizeObserver(([entry]) => {
      if (!entry) return;
      const { width, height } = entry.contentRect;
      setLayout({
        scale: calculateTickerScale(width, height),
        mode: getTickerLayoutMode(width),
      });
    });
    observer.observe(ticker);
    return () => observer.disconnect();
  }, []);

  const adjustTextOpacity = (delta: number) => {
    setTextOpacity((current) => normalizeTickerTextOpacity(current + delta));
  };

  const tickerStyle = {
    "--ticker-text-opacity": textOpacity / 100,
    "--ticker-ui-scale": layout.scale,
    fontSize: `${10 * layout.scale}px`,
  } as CSSProperties;
  const quoteSource = summarizeQuoteSource(
    portfolio.data?.items ?? [],
    portfolio.isError,
  );
  const noValuation = Boolean(portfolio.data?.items.length
    && portfolio.data.unavailableQuoteCount === portfolio.data.items.length);

  return (
    <main ref={tickerRef} className="ticker-window" data-layout={layout.mode} style={tickerStyle}>
      <header onMouseDown={drag}>
        <span className="ticker-brand">隐线 · {quoteSource.badge}</span>
        <span className="ticker-drag-hint">拖动</span>
        <span className="ticker-controls" onMouseDown={(event) => event.stopPropagation()}>
          <button type="button" aria-label="降低文字透明度"
            onClick={() => adjustTextOpacity(-TICKER_TEXT_OPACITY_STEP)}>−</button>
          <output aria-label="当前文字透明度" aria-live="polite">{textOpacity}%</output>
          <button type="button" aria-label="提高文字透明度"
            onClick={() => adjustTextOpacity(TICKER_TEXT_OPACITY_STEP)}>＋</button>
          <button type="button" onClick={hide}>隐藏</button>
        </span>
      </header>
      <section className="ticker-content">
        <div className="ticker-total"><span>浮动盈亏</span><strong className={(portfolio.data?.totalProfit ?? 0) >= 0 ? "up" : "down"}>
          {noValuation ? "--" : `${(portfolio.data?.totalProfit ?? 0) >= 0 ? "+" : ""}¥ ${money(portfolio.data?.totalProfit ?? 0)}`}</strong>
          <small>{noValuation ? "总市值 --" : `总市值 ¥ ${money(portfolio.data?.totalMarketValue ?? 0)}`}</small></div>
        <PortfolioTable items={portfolio.data?.items ?? []} compact />
        <footer>{quoteSource.notice} · 不含费用税费 · 老板键 ⌘/Ctrl+Shift+H</footer>
      </section>
    </main>
  );
}
