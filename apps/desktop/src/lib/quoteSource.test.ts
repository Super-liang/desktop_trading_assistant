import { describe, expect, it } from "vitest";
import type { PortfolioItem } from "../types";
import { summarizeQuoteSource } from "./quoteSource";

function item(overrides: Partial<PortfolioItem["quote"]> = {}): PortfolioItem {
  return {
    id: "1",
    instrumentId: "SSE:600519",
    displayName: "贵州茅台",
    quantity: 100,
    costPrice: 1400,
    sortOrder: 0,
    marketValue: 145000,
    profit: 5000,
    returnPercent: 3.57,
    quote: {
      instrumentId: "SSE:600519",
      name: "贵州茅台",
      last: 1450,
      previousClose: 1440,
      open: 1441,
      high: 1460,
      low: 1430,
      change: 10,
      changePercent: 0.69,
      volume: 1000,
      marketPhase: "CONTINUOUS",
      source: "AKSHARE",
      sourceTimestamp: "2026-07-20T01:31:00Z",
      receivedAt: "2026-07-20T01:31:01Z",
      delayed: true,
      stale: false,
      demo: false,
      ...overrides,
    },
  };
}

describe("summarizeQuoteSource", () => {
  it("明确展示 AKShare 公开延迟行情而非授权实时行情", () => {
    expect(summarizeQuoteSource([item()])).toMatchObject({
      badge: "AKSHARE",
      status: "公开行情正常",
      estimate: "按最新公开行情估算",
    });
    expect(summarizeQuoteSource([item()]).notice).toContain("延迟");
  });

  it("任一行情陈旧时显示过期状态", () => {
    expect(summarizeQuoteSource([item({ stale: true })])).toMatchObject({
      badge: "AKSHARE",
      status: "行情已过期",
    });
  });

  it("降级为 DEMO 时绝不描述成真实行情", () => {
    expect(summarizeQuoteSource([
      item({ source: "DEMO", delayed: false, demo: true }),
    ])).toMatchObject({
      badge: "DEMO",
      status: "演示流正常",
      estimate: "按最新演示价格估算",
    });
  });

  it("没有持仓时不猜测当前 Provider", () => {
    expect(summarizeQuoteSource([])).toMatchObject({
      badge: "WAIT",
      status: "等待添加标的",
    });
  });
});
