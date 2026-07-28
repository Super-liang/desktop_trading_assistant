export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  role: "USER" | "ADMIN";
};

export type Quote = {
  instrumentId: string;
  name: string;
  last: number;
  previousClose: number;
  open: number;
  high: number;
  low: number;
  change: number;
  changePercent: number;
  volume: number;
  marketPhase: string;
  source: string;
  sourceTimestamp: string;
  receivedAt: string;
  delayed: boolean;
  stale: boolean;
  demo: boolean;
};

export type PortfolioItem = {
  id: string;
  instrumentId: string;
  displayName: string;
  quantity: number;
  costPrice: number;
  sortOrder: number;
  // 后端启用 NON_NULL 序列化后，无行情时字段可能被直接省略。
  quote?: Quote | null;
  marketValue: number | null;
  profit: number | null;
  returnPercent: number | null;
};

export type PortfolioSummary = {
  items: PortfolioItem[];
  totalMarketValue: number;
  totalProfit: number;
  unavailableQuoteCount: number;
  calculationNotice: string;
};

export type PerformanceStatus = "COMPLETE" | "PARTIAL" | "UNAVAILABLE" | "ACCUMULATING";

export type PerformanceSummary = {
  dailyProfit: number | null;
  dailyReturnPercent: number | null;
  yearProfit: number | null;
  yearReturnPercent: number | null;
  annualizedReturnPercent: number | null;
  statisticsStartDate: string | null;
  calculatedAt: string;
  status: PerformanceStatus;
  missingQuoteCount: number;
  referenceNotice: string;
};

export type SearchResult = {
  instrumentId: string;
  code: string;
  name: string;
  exchange: string;
  assetType: string;
};

export type AdminUser = {
  id: string;
  email: string;
  displayName: string;
  role: "USER" | "ADMIN";
  status: "ACTIVE" | "DISABLED";
  createdAt: string;
  lastLoginAt?: string;
};

export type AdminUserOverview = {
  user: AdminUser;
  holdingCount: number;
  performance: PerformanceSummary;
};

// 管理端持仓类型刻意不包含数量、成本、总市值和单只收益字段。
export type AdminHolding = {
  instrumentId: string;
  displayName: string;
  exchange: string;
  quoteAvailable: boolean;
};

export type UserOperationAudit = {
  id: string;
  action: "PORTFOLIO_CREATED" | "PORTFOLIO_UPDATED" | "PORTFOLIO_DELETED" | "PASSWORD_CHANGED";
  instrumentId?: string | null;
  instrumentName?: string | null;
  result: string;
  createdAt: string;
};

export type Page<T> = {
  content: T[];
  number?: number;
  // 测试替身和非 Spring 网关可使用 page；组件分页以本地请求页码为准。
  page?: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type MarketDataConfig = {
  provider: "AKSHARE";
  mode: "MARKET_SNAPSHOT" | "SINGLE_STOCK";
  snapshotSource: "EASTMONEY" | "SINA";
  singleSource: "EASTMONEY" | "XUEQIU";
  refreshSeconds: number;
  updatedAt: string;
  providers: Array<{ id: string; name: string; modes: string[] }>;
};

export type MarketDataComponentStatus = {
  id: string;
  label: string;
  status: "UP" | "DEGRADED" | "DOWN" | "UNKNOWN" | "NOT_APPLICABLE";
  lastSuccessAt?: string;
  ageSeconds?: number;
  detail?: string;
};

export type MarketDataStatus = {
  mode: "MARKET_SNAPSHOT" | "SINGLE_STOCK";
  components: MarketDataComponentStatus[];
  checkedAt: string;
};
