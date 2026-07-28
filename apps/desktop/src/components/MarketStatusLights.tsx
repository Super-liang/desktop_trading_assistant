import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { MarketDataComponentStatus } from "../types";
import type { MarketDataConfig } from "../types";
import { statusTime } from "../lib/format";

const statusText: Record<MarketDataComponentStatus["status"], string> = {
  UP: "正常",
  DEGRADED: "延迟",
  DOWN: "故障",
  UNKNOWN: "待检测",
  NOT_APPLICABLE: "不适用",
};

const componentLabels: Record<string, string> = {
  SPRING_API: "后端服务",
  AKSHARE_GATEWAY: "AKShare 行情服务",
  REDIS_SNAPSHOT: "全市场缓存",
  REDIS_SNAPSHOT_EASTMONEY: "东方财富行情缓存",
  REDIS_SNAPSHOT_SINA: "新浪行情缓存",
  UPSTREAM_SNAPSHOT_EASTMONEY: "东方财富全市场行情",
  UPSTREAM_SNAPSHOT_SINA: "新浪全市场行情",
  UPSTREAM_SINGLE_EASTMONEY: "东方财富单股行情",
  UPSTREAM_SINGLE_XUEQIU: "雪球单股行情",
  SNAPSHOT_EASTMONEY: "东方财富全市场行情",
  SNAPSHOT_SINA: "新浪全市场行情",
  SINGLE_EASTMONEY: "东方财富单股行情",
  SINGLE_XUEQIU: "雪球单股行情",
};

function componentLabel(component: MarketDataComponentStatus) {
  return componentLabels[component.id] ?? componentLabels[component.label] ?? component.label;
}

function statusSuffix(component: MarketDataComponentStatus) {
  if (component.ageSeconds != null) return ` · ${component.ageSeconds}s`;
  if (component.lastSuccessAt) {
    return ` · ${statusTime(component.lastSuccessAt)}`;
  }
  return "";
}

export function MarketStatusLights({ compact = false, mode, singleSource, enabled = true }: {
  compact?: boolean;
  mode?: MarketDataConfig["mode"];
  singleSource?: MarketDataConfig["singleSource"];
  enabled?: boolean;
}) {
  const status = useQuery({
    queryKey: ["market-data-status", mode, singleSource],
    queryFn: () => api.marketDataStatus(mode, singleSource),
    refetchInterval: 15_000,
    enabled,
    retry: false,
  });
  if (status.isLoading) return <div className="source-lights muted">正在检测行情链路…</div>;
  if (status.isError) {
    return <div className="source-lights"><div className="source-light down-state">
      <i /><span><strong>后端服务</strong><small>无法连接</small></span>
    </div></div>;
  }
  return <div className={`source-lights ${compact ? "compact" : ""}`}>
    {status.data?.components.map((component) => (
      <div className={`source-light ${component.status.toLowerCase()}`} key={component.id}
        title={component.detail ?? componentLabel(component)}>
        <i /><span><strong>{componentLabel(component)}</strong>
          <small>{statusText[component.status]}{statusSuffix(component)}</small></span>
      </div>
    ))}
  </div>;
}
