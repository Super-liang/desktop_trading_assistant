import { useEffect, useRef, useState, type CSSProperties } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { money } from "../lib/format";
import { summarizeQuoteSource } from "../lib/quoteSource";
import { portfolioRefetchInterval, useMarketPreferences } from "../lib/marketPreferences";
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
import type { PortfolioSummary } from "../types";
import { isTauriRuntime, useWindowVisibility } from "../lib/windowVisibility";
import { PORTFOLIO_SYNC_EVENT, TICKER_DATA_READY_EVENT } from "../lib/desktopEvents";

async function drag() {
  const { getCurrentWindow } = await import("@tauri-apps/api/window");
  await getCurrentWindow().startDragging();
}
async function close() {
  const { getCurrentWindow } = await import("@tauri-apps/api/window");
  await getCurrentWindow().close();
}

type PortfolioSyncPayload = {
  mode: "MARKET_SNAPSHOT" | "SINGLE_STOCK";
  snapshotSource: "EASTMONEY" | "SINA";
  singleSource: "EASTMONEY" | "XUEQIU";
  data: PortfolioSummary;
};

export function TickerWindow() {
  const native = isTauriRuntime();
  const mainVisible = useWindowVisibility("main");
  const marketConfig = useQuery({
    queryKey: ["market-data-config"], queryFn: api.marketDataConfig,
    refetchInterval: mainVisible ? false : 30_000,
  });
  const preferences = useMarketPreferences(
    marketConfig.data?.mode ?? "MARKET_SNAPSHOT",
    marketConfig.data?.snapshotSource ?? "EASTMONEY",
    marketConfig.data?.singleSource ?? "EASTMONEY",
  );
  const marketMode = preferences.mode;
  const ownsPortfolioQuery = !native || !mainVisible;
  const portfolio = useQuery({
    queryKey: ["portfolio", preferences.snapshotSource, preferences.singleSource, marketMode],
    queryFn: () => api.portfolio(marketMode,
      preferences.snapshotSource, preferences.singleSource),
    refetchInterval: portfolioRefetchInterval(
      marketMode,
      preferences.singleRefreshSeconds,
      marketConfig.data?.refreshSeconds ?? 30,
    ),
    enabled: marketConfig.isSuccess && ownsPortfolioQuery,
  });
  const [sharedPortfolio, setSharedPortfolio] = useState<PortfolioSyncPayload | null>(null);
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
    if (!native) return;
    let unlisten: (() => void) | undefined;
    import("@tauri-apps/api/event").then(async ({ emitTo, listen }) => {
      unlisten = await listen<PortfolioSyncPayload>(PORTFOLIO_SYNC_EVENT, (event) => {
        setSharedPortfolio(event.payload);
      });
      await emitTo("main", TICKER_DATA_READY_EVENT);
    }).catch(() => undefined);
    return () => unlisten?.();
  }, [native]);

  useEffect(() => {
    const ticker = tickerRef.current;
    if (!ticker) return;
    const observer = new ResizeObserver(([entry]) => {
      if (!entry) return;
      const { width, height } = entry.contentRect;
      const next = {
        scale: calculateTickerScale(width, height),
        mode: getTickerLayoutMode(width),
      };
      setLayout((current) => current.scale === next.scale && current.mode === next.mode
        ? current : next);
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
  const sharedMatchesPreferences = sharedPortfolio
    && sharedPortfolio.mode === marketMode
    && sharedPortfolio.snapshotSource === preferences.snapshotSource
    && sharedPortfolio.singleSource === preferences.singleSource;
  const data = mainVisible && sharedMatchesPreferences ? sharedPortfolio.data : portfolio.data;
  const quoteSource = summarizeQuoteSource(
    data?.items ?? [],
    ownsPortfolioQuery && portfolio.isError,
  );
  const noValuation = Boolean(data?.items.length
    && data.unavailableQuoteCount === data.items.length);

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
          <button type="button" onClick={close}>隐藏</button>
        </span>
      </header>
      <section className="ticker-content">
        <div className="ticker-total"><span>浮动盈亏</span><strong className={(data?.totalProfit ?? 0) >= 0 ? "up" : "down"}>
          {noValuation ? "--" : `${(data?.totalProfit ?? 0) >= 0 ? "+" : ""}¥ ${money(data?.totalProfit ?? 0)}`}</strong>
          <small>{noValuation ? "总市值 --" : `总市值 ¥ ${money(data?.totalMarketValue ?? 0)}`}</small></div>
        <PortfolioTable items={data?.items ?? []} compact />
        <footer>{quoteSource.notice} · 不含费用税费 · 老板键 ⌘/Ctrl+Shift+H</footer>
      </section>
    </main>
  );
}
