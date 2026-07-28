import { describe, expect, it } from "vitest";
import { money, percent, quoteTime, statusTime } from "./format";

describe("行情格式化", () => {
  it("保留金额精度并显示涨跌符号", () => {
    expect(money(11100)).toBe("11,100.00");
    expect(percent(8.2927)).toBe("+8.29%");
    expect(percent(-1.2)).toBe("-1.20%");
  });

  it("复用日期格式化器输出行情与状态时间", () => {
    const timestamp = "2026-07-20T03:00:00Z";
    expect(quoteTime(timestamp)).toBe(quoteTime(timestamp));
    expect(quoteTime(timestamp)).not.toBe("");
    expect(statusTime(timestamp)).not.toBe("");
  });
});
