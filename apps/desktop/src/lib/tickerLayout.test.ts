import { describe, expect, it } from "vitest";
import { calculateTickerScale, getTickerLayoutMode } from "./tickerLayout";

describe("tickerLayout", () => {
  it("根据宽高较小的一侧连续计算缩放并限制在 0.7–1.15", () => {
    expect(calculateTickerScale(720, 340)).toBe(1);
    expect(calculateTickerScale(420, 200)).toBe(0.7);
    expect(calculateTickerScale(1440, 680)).toBe(1.15);
    expect(calculateTickerScale(720, 170)).toBe(0.7);
    expect(calculateTickerScale(Number.NaN, 340)).toBe(1);
  });

  it("按可用宽度选择宽、中、窄三种布局", () => {
    expect(getTickerLayoutMode(700)).toBe("wide");
    expect(getTickerLayoutMode(560)).toBe("medium");
    expect(getTickerLayoutMode(420)).toBe("narrow");
  });
});
