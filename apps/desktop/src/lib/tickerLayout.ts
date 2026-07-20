export const TICKER_BASE_WIDTH = 720;
export const TICKER_BASE_HEIGHT = 340;
export const TICKER_UI_SCALE_MIN = 0.7;
export const TICKER_UI_SCALE_MAX = 1.15;

export type TickerLayoutMode = "wide" | "medium" | "narrow";

export function calculateTickerScale(width: number, height: number): number {
  const rawScale = Math.min(width / TICKER_BASE_WIDTH, height / TICKER_BASE_HEIGHT);
  if (!Number.isFinite(rawScale)) return 1;
  return Math.min(TICKER_UI_SCALE_MAX, Math.max(TICKER_UI_SCALE_MIN, rawScale));
}

export function getTickerLayoutMode(width: number): TickerLayoutMode {
  if (width <= 480) return "narrow";
  if (width <= 620) return "medium";
  return "wide";
}
