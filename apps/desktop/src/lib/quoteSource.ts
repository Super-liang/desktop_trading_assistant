import type { PortfolioItem } from "../types";

export type QuoteSourceSummary = {
  badge: string;
  notice: string;
  status: string;
  estimate: string;
};

export function summarizeQuoteSource(
  items: PortfolioItem[],
  connectionError = false,
): QuoteSourceSummary {
  if (connectionError) {
    return {
      badge: "OFFLINE",
      notice: "行情连接中断 · 请检查服务端和数据源",
      status: "连接中断",
      estimate: "按最后可用行情估算",
    };
  }
  if (!items.length) {
    return {
      badge: "WAIT",
      notice: "添加自选后显示实际行情来源 · 不构成投资建议",
      status: "等待添加标的",
      estimate: "等待行情数据",
    };
  }

  const quotes = items.map((item) => item.quote).filter((quote) => quote !== null);
  if (!quotes.length) {
    return {
      badge: "WAIT",
      notice: "自选已保存 · 当前行情暂不可用，将自动重试",
      status: "等待行情",
      estimate: "暂无可用行情",
    };
  }
  if (quotes.length < items.length) {
    return {
      badge: "DEGRADED",
      notice: `${items.length - quotes.length} 只证券行情暂不可用 · 其余行情继续展示`,
      status: "部分行情降级",
      estimate: "仅汇总可用行情",
    };
  }
  const sources = [...new Set(quotes.map((quote) => quote.source || "UNKNOWN"))];
  const stale = quotes.some((quote) => quote.stale);
  const demo = quotes.every((quote) => quote.demo || quote.source === "DEMO");

  if (demo) {
    return {
      badge: "DEMO",
      notice: "当前为可复现演示行情 · 数据源 DEMO · 不构成投资建议",
      status: stale ? "演示行情已过期" : "演示流正常",
      estimate: "按最新演示价格估算",
    };
  }

  if (sources.length > 1) {
    return {
      badge: "MIXED",
      notice: `混合行情来源 ${sources.join(" / ")} · 请逐条核对来源和时间`,
      status: stale ? "部分行情已过期" : "混合行情正常",
      estimate: "按各来源最新行情估算",
    };
  }

  const source = sources[0];
  const delayed = quotes.some((quote) => quote.delayed);
  if (source.startsWith("AKSHARE")) {
    return {
      badge: source,
      notice: `AKShare 公开${delayed ? "延迟" : ""}行情 · 仅供非商业研究参考 · 不构成投资建议`,
      status: stale ? "行情已过期" : "公开行情正常",
      estimate: "按最新公开行情估算",
    };
  }
  return {
    badge: source,
    notice: `行情来源 ${source}${delayed ? " · 延迟行情" : ""} · 不构成投资建议`,
    status: stale ? "行情已过期" : "行情连接正常",
    estimate: delayed ? "按最新延迟行情估算" : "按最新行情估算",
  };
}
