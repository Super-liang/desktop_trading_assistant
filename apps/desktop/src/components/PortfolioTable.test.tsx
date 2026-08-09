// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { PortfolioTable } from "./PortfolioTable";
import type { PortfolioItem, PositionReturn } from "../types";

const item: PortfolioItem = {
  id: "1",
  instrumentId: "SSE:600519",
  displayName: "贵州茅台",
  quantity: 100,
  costPrice: 1400,
  sortOrder: 0,
  marketValue: 145000,
  profit: 5000,
  returnPercent: 3.5714,
  quote: {
    instrumentId: "SSE:600519",
    name: "贵州茅台",
    last: 1450,
    previousClose: 1440,
    open: 1441,
    high: 1460,
    low: 1430,
    change: 10,
    changePercent: 0.6944,
    volume: 1000,
    marketPhase: "CONTINUOUS",
    source: "DEMO",
    sourceTimestamp: "2026-07-20T03:00:00Z",
    receivedAt: "2026-07-20T03:00:01Z",
    delayed: false,
    stale: true,
    demo: true,
  },
};
const itemReturn: PositionReturn = {
  positionId: "1", instrumentId: "SSE:600519", displayName: "贵州茅台",
  market: "A_SHARE", currency: "CNY", currentPrice: 1450,
  valueDate: "2026-07-20", quoteAsOf: "2026-07-20T03:00:00Z",
  delayed: false, stale: true, dailyProfit: 1000, dailyReturnPercent: 0.6944,
  holdingProfit: 5000, holdingReturnPercent: 3.5714, dailyStatus: "COMPLETE",
  unavailableReason: null,
};

describe("PortfolioTable", () => {
  afterEach(cleanup);
  it("首行展示名称、代码与刷新时间，不展示行情来源", () => {
    const { container } = render(<PortfolioTable items={[item]}
      itemReturns={new Map([["1", itemReturn]])} />);
    expect(screen.getByText("贵州茅台")).toBeInTheDocument();
    expect(screen.getByText("600519")).toBeInTheDocument();
    expect(screen.queryByText(/DEMO/)).not.toBeInTheDocument();
    expect(screen.getByText("最后数据 · 已陈旧")).toBeInTheDocument();
    expect(screen.getByText(/最后刷新/)).toBeInTheDocument();
    expect(screen.getByText(/\+¥ 5,000.00/)).toBeInTheDocument();
    const quote = container.querySelector(".portfolio-quote-row");
    expect(quote?.querySelector(".portfolio-security-line")).toHaveTextContent(
      /贵州茅台.*600519.*最后刷新/,
    );
    expect(quote?.querySelector(".quote-security")).toHaveTextContent("贵州茅台");
    expect(quote?.querySelector(".quote-price")).toHaveTextContent("1,450.00");
    expect(quote?.querySelector(".quote-position")).toHaveTextContent("成本 ¥ 1,400.00");
    expect(quote?.querySelector(".quote-market-value")).toHaveTextContent("¥ 145,000.00");
    expect(quote?.querySelector(".quote-profit")).toHaveTextContent("+¥ 1,000.00");
    expect(quote?.querySelector(".quote-profit")).toHaveTextContent("+¥ 5,000.00");
  });

  it("无行情时显示未知而不是零市值和零盈亏", () => {
    render(<PortfolioTable items={[{
      ...item, quote: null, marketValue: null, profit: null, returnPercent: null,
    }]} />);

    expect(screen.getByText("暂无行情时间")).toBeInTheDocument();
    expect(screen.getByText("行情暂不可用")).toBeInTheDocument();
    expect(screen.getAllByText("--")).toHaveLength(3);
  });

  it("基金展示单位净值、净值日期、日收益和持有收益", () => {
    const fund = { ...item, id: "fund-1", instrumentId: "CN_FUND:000001",
      displayName: "测试开放式基金", market: "PUBLIC_FUND" as const,
      currency: "CNY" as const, assetType: "OPEN_END_FUND" as const, quote: null };
    const fundReturn = { ...itemReturn, positionId: "fund-1", instrumentId: "CN_FUND:000001",
      displayName: "测试开放式基金", market: "PUBLIC_FUND" as const, currentPrice: 1.2345,
      valueDate: "2026-07-28", quoteAsOf: null, delayed: true };

    render(<PortfolioTable items={[fund]} itemReturns={new Map([["fund-1", fundReturn]])} />);

    expect(screen.getByText("单位净值")).toBeInTheDocument();
    expect(screen.getByText("净值日期 2026-07-28")).toBeInTheDocument();
    expect(screen.getByText(/日收益 \/ 持有收益/)).toBeInTheDocument();
  });

  it("透明小窗继续使用 compact 行布局且不展示行情来源", () => {
    const { container } = render(<PortfolioTable items={[item]} compact />);

    expect(container.querySelector(".quote-table.compact .quote-row")).toBeInTheDocument();
    expect(screen.queryByText(/DEMO/)).not.toBeInTheDocument();
    expect(screen.getByText(/最后刷新/)).toBeInTheDocument();
  });
});
