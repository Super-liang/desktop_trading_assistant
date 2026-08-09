import type { Market } from "../types";

const MARKET_TIMEZONES: Record<Market, string> = {
  A_SHARE: "Asia/Shanghai",
  HK_STOCK: "Asia/Hong_Kong",
  US_STOCK: "America/New_York",
  PUBLIC_FUND: "Asia/Shanghai",
};

/** 返回指定市场所在地的 YYYY-MM-DD，避免跨时区时把建仓日期误判为未来。 */
export function marketLocalDate(market: Market, instant: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: MARKET_TIMEZONES[market],
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(instant);
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}
