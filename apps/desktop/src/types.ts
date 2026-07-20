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
  quote: Quote;
  marketValue: number;
  profit: number;
  returnPercent: number;
};

export type PortfolioSummary = {
  items: PortfolioItem[];
  totalMarketValue: number;
  totalProfit: number;
  calculationNotice: string;
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

