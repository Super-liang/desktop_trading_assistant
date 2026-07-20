// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import {
  normalizeTickerTextOpacity,
  readTickerTextOpacity,
  saveTickerTextOpacity,
  TICKER_TEXT_OPACITY_STORAGE_KEY,
} from "./tickerAppearance";

describe("tickerAppearance", () => {
  it("将透明度限制在 20%–100% 且对齐到 10% 步长", () => {
    expect(normalizeTickerTextOpacity(-5)).toBe(20);
    expect(normalizeTickerTextOpacity(74)).toBe(70);
    expect(normalizeTickerTextOpacity(106)).toBe(100);
    expect(normalizeTickerTextOpacity(Number.NaN)).toBe(100);
  });

  it("读取并保存经过校验的本机透明度偏好", () => {
    const getItem = vi.fn(() => "76");
    const setItem = vi.fn();

    expect(readTickerTextOpacity({ getItem })).toBe(80);
    expect(saveTickerTextOpacity(15, { setItem })).toBe(20);
    expect(setItem).toHaveBeenCalledWith(TICKER_TEXT_OPACITY_STORAGE_KEY, "20");
  });
});
