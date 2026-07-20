export const TICKER_TEXT_OPACITY_MIN = 20;
export const TICKER_TEXT_OPACITY_MAX = 100;
export const TICKER_TEXT_OPACITY_STEP = 10;
export const TICKER_TEXT_OPACITY_STORAGE_KEY = "ticker-text-opacity";

export function normalizeTickerTextOpacity(value: number): number {
  if (!Number.isFinite(value)) return TICKER_TEXT_OPACITY_MAX;
  const stepped = Math.round(value / TICKER_TEXT_OPACITY_STEP) * TICKER_TEXT_OPACITY_STEP;
  return Math.min(TICKER_TEXT_OPACITY_MAX, Math.max(TICKER_TEXT_OPACITY_MIN, stepped));
}

export function readTickerTextOpacity(storage: Pick<Storage, "getItem"> = window.localStorage): number {
  try {
    const stored = storage.getItem(TICKER_TEXT_OPACITY_STORAGE_KEY);
    return stored === null ? TICKER_TEXT_OPACITY_MAX : normalizeTickerTextOpacity(Number(stored));
  } catch {
    return TICKER_TEXT_OPACITY_MAX;
  }
}

export function saveTickerTextOpacity(
  value: number,
  storage: Pick<Storage, "setItem"> = window.localStorage,
): number {
  const normalized = normalizeTickerTextOpacity(value);
  try {
    storage.setItem(TICKER_TEXT_OPACITY_STORAGE_KEY, String(normalized));
  } catch {
    // 本机显示偏好写入失败不应影响盯盘功能。
  }
  return normalized;
}
