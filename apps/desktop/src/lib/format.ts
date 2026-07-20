export const money = (value: number) =>
  new Intl.NumberFormat("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);

export const percent = (value: number) => `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;

export const marketPhase: Record<string, string> = {
  PRE_OPEN: "盘前",
  AUCTION: "集合竞价",
  CONTINUOUS: "交易中",
  BREAK: "午间休市",
  CLOSED: "已收盘",
};

