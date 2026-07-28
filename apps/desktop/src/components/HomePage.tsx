import { BriefcaseBusiness, Plus } from "lucide-react";
import { useState } from "react";
import { money, percent } from "../lib/format";
import type { MarketDataConfig, PerformanceSummary, PortfolioSummary } from "../types";
import { MarketStatusLights } from "./MarketStatusLights";

const statusLabel = {
  COMPLETE: "数据完整",
  PARTIAL: "部分行情缺失",
  UNAVAILABLE: "暂不可计算",
  ACCUMULATING: "数据积累中",
} as const;

function signedMoney(value: number | null) {
  if (value == null) return "--";
  return `${value >= 0 ? "+" : "-"}¥ ${money(Math.abs(value))}`;
}

function valueClass(value: number | null) {
  return value == null ? "" : value >= 0 ? "up" : "down";
}

export function HomePage({ data, performance, performanceLoading, performanceError, quoteBadge,
  quoteNotice, marketMode, singleSource, visible, refreshSeconds, singleRefreshSeconds, onAdd,
  onOpenPortfolio }: {
  data?: PortfolioSummary;
  performance?: PerformanceSummary;
  performanceLoading: boolean;
  performanceError: boolean;
  quoteBadge: string;
  quoteNotice: string;
  marketMode: MarketDataConfig["mode"];
  singleSource: MarketDataConfig["singleSource"];
  visible: boolean;
  refreshSeconds?: number;
  singleRefreshSeconds: number;
  onAdd: () => void;
  onOpenPortfolio: () => void;
}) {
  const [display, setDisplay] = useState<"AMOUNT" | "RATE">("AMOUNT");
  const daily = display === "AMOUNT" ? performance?.dailyProfit ?? null
    : performance?.dailyReturnPercent ?? null;
  const year = display === "AMOUNT" ? performance?.yearProfit ?? null
    : performance?.yearReturnPercent ?? null;
  const unavailable = performanceLoading || performanceError || !performance;

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
    <MarketStatusLights mode={marketMode} singleSource={singleSource} enabled={visible} />
    <div className="performance-toolbar" role="group" aria-label="收益展示方式">
      <span>收益展示</span><button className={display === "AMOUNT" ? "active" : ""}
        onClick={() => setDisplay("AMOUNT")}>金额</button>
      <button className={display === "RATE" ? "active" : ""}
        onClick={() => setDisplay("RATE")}>比率</button>
    </div>
    <section className="summary-grid performance-grid">
      <article className="summary-card featured"><small>{display === "AMOUNT" ? "我的日收益" : "我的日收益率"}</small>
        <strong className={valueClass(daily)}>{unavailable ? "--" : display === "AMOUNT"
          ? signedMoney(daily) : daily == null ? "--" : percent(daily)}</strong>
        <span>{performance?.calculatedAt ? `计算于 ${new Date(performance.calculatedAt).toLocaleString("zh-CN")}` : "等待收益数据"}</span>
      </article>
      <article className="summary-card"><small>{display === "AMOUNT" ? "我的本年收益" : "我的本年收益率"}</small>
        <strong className={valueClass(year)}>{unavailable ? "--" : display === "AMOUNT"
          ? signedMoney(year) : year == null ? "--" : percent(year)}</strong>
        <span>{display === "RATE" && performance?.annualizedReturnPercent != null
          ? `年化收益率 ${percent(performance.annualizedReturnPercent)}`
          : performance?.status === "ACCUMULATING" ? "年化收益率尚在积累"
            : performance?.statisticsStartDate ? `统计始于 ${performance.statisticsStartDate}` : "尚无年度统计"}</span>
      </article>
      <article className="summary-card"><small>数据状态</small><strong className="status-live"><i />
        {performanceError ? "收益服务暂不可用" : statusLabel[performance?.status ?? "UNAVAILABLE"]}</strong>
        <span>{performance?.missingQuoteCount ? `${performance.missingQuoteCount} 只证券行情缺失` : marketMode === "MARKET_SNAPSHOT"
          ? `服务端快照刷新频率：${refreshSeconds ?? "--"} 秒`
          : `客户端查询频率：${singleRefreshSeconds} 秒`}</span></article>
    </section>
    <section className="home-callout">
      <div><p className="eyebrow">MY PORTFOLIO</p><h2>{data?.items.length ?? 0} 个盯盘标的</h2>
        <p>持仓明细、数量和成本仅在“我的持仓”页面展示。</p></div>
      <button className="secondary-button page-action" onClick={onOpenPortfolio}>查看我的持仓</button>
    </section>
    <footer>{performance?.referenceNotice ?? "收益基于手工持仓与行情计算，仅供参考，不构成投资建议"}</footer>
  </main>;
}
