import { BriefcaseBusiness, Plus } from "lucide-react";
import { useState } from "react";
import { money, percent } from "../lib/format";
import type { Currency, Market, MarketDataConfig, PortfolioReturns, PortfolioSummary } from "../types";
import { MarketStatusLights } from "./MarketStatusLights";
import { MarketIndexCarousel } from "./MarketIndexCarousel";

const statusLabel = {
  COMPLETE: "数据完整",
  PARTIAL: "部分行情缺失",
  UNAVAILABLE: "暂不可计算",
  ACCUMULATING: "数据积累中",
} as const;

const marketLabel: Record<Market, string> = {
  A_SHARE: "A股", HK_STOCK: "港股", US_STOCK: "美股", PUBLIC_FUND: "公募基金",
};
const currencySymbol: Record<Currency, string> = { CNY: "¥", HKD: "HK$", USD: "$" };

function signedMoney(value: number | null, currency: Currency) {
  if (value == null) return "--";
  return `${value >= 0 ? "+" : "-"}${currencySymbol[currency]} ${money(Math.abs(value))}`;
}

function valueClass(value: number | null) {
  return value == null ? "" : value >= 0 ? "up" : "down";
}

export function HomePage({ data, returns, returnsLoading, returnsError, quoteBadge,
  quoteNotice, marketMode, snapshotSource, singleSource, visible, refreshSeconds, singleRefreshSeconds, onAdd,
  onOpenPortfolio }: {
  data?: PortfolioSummary;
  returns?: PortfolioReturns;
  returnsLoading: boolean;
  returnsError: boolean;
  quoteBadge: string;
  quoteNotice: string;
  marketMode: MarketDataConfig["mode"];
  snapshotSource: MarketDataConfig["snapshotSource"];
  singleSource: MarketDataConfig["singleSource"];
  visible: boolean;
  refreshSeconds?: number;
  singleRefreshSeconds: number;
  onAdd: () => void;
  onOpenPortfolio: () => void;
}) {
  const [display, setDisplay] = useState<"AMOUNT" | "RATE">("AMOUNT");
  const unavailable = returnsLoading || returnsError || !returns;

  return <main className="workspace home-page">
    <header className="workspace-header">
      <div><p className="eyebrow">MARKET DESK · A SHARE</p><h1>我的盯盘</h1>
        <p>{new Date().toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "long" })}</p></div>
      <div className="header-actions">
        <button className="secondary-button page-action" onClick={onOpenPortfolio}>
          <BriefcaseBusiness size={17} /> 我的持仓
        </button>
        <button className="primary-button small" onClick={onAdd}><Plus size={17} /> 添加持仓</button>
      </div>
    </header>
    <div className="demo-banner"><span>{quoteBadge}</span>{quoteNotice}</div>
    <MarketStatusLights collapsible mode={marketMode} singleSource={singleSource} enabled={visible} />
    <MarketIndexCarousel source={snapshotSource} enabled={visible} />
    <div className="performance-toolbar" role="group" aria-label="收益展示方式">
      <span>收益展示</span><button className={display === "AMOUNT" ? "active" : ""}
        onClick={() => setDisplay("AMOUNT")}>金额</button>
      <button className={display === "RATE" ? "active" : ""}
        onClick={() => setDisplay("RATE")}>比率</button>
    </div>
    <section className="summary-grid performance-grid multi-market-returns">
      {unavailable ? <article className="summary-card featured"><small>我的收益</small><strong>--</strong>
        <span>{returnsError ? "收益服务暂不可用" : "正在计算收益"}</span></article>
        : returns.groups.length === 0 ? <article className="summary-card"><small>我的收益</small>
          <strong>--</strong><span>添加持仓后开始计算</span></article>
          : returns.groups.map((group) => {
            const daily = display === "AMOUNT" ? group.dailyProfit : group.dailyReturnPercent;
            const holding = display === "AMOUNT" ? group.holdingProfit : group.holdingReturnPercent;
            return <article className="summary-card" key={group.market}>
              <small>{marketLabel[group.market]} · {group.currency}</small>
              <div><span>日收益</span><strong className={valueClass(daily)}>{display === "AMOUNT"
                ? signedMoney(daily, group.currency) : daily == null ? "--" : percent(daily)}</strong></div>
              <div><span>持有收益</span><strong className={valueClass(holding)}>{display === "AMOUNT"
                ? signedMoney(holding, group.currency) : holding == null ? "--" : percent(holding)}</strong></div>
              <span>{statusLabel[group.dailyStatus]}{group.unavailableDailyCount
                ? ` · ${group.unavailableDailyCount} 项日收益不可用` : ""}</span>
            </article>;
          })}
      <article className="summary-card"><small>行情刷新</small><strong className="status-live"><i />
        {marketMode === "MARKET_SNAPSHOT" ? "服务端快照" : "客户端查询"}</strong>
        <span>{marketMode === "MARKET_SNAPSHOT" ? `服务端快照刷新频率：${refreshSeconds ?? "--"} 秒`
          : `客户端查询频率：${singleRefreshSeconds} 秒`}</span></article>
    </section>
    <section className="home-callout">
      <div><p className="eyebrow">MY PORTFOLIO</p><h2>{data?.items.length ?? 0} 个盯盘标的</h2>
        <p>持仓明细、数量和成本仅在“我的持仓”页面展示。</p></div>
      <button className="secondary-button page-action" onClick={onOpenPortfolio}>查看我的持仓</button>
    </section>
    <footer>{returns?.calculationNotice ?? "收益基于手工持仓与行情计算，仅供参考，不构成投资建议"}</footer>
  </main>;
}
