import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { MarketDataComponentStatus } from "../types";
import type { MarketDataConfig } from "../types";

const statusText: Record<MarketDataComponentStatus["status"], string> = {
  UP: "正常",
  DEGRADED: "延迟",
  DOWN: "故障",
  UNKNOWN: "待检测",
  NOT_APPLICABLE: "不适用",
};

function statusSuffix(component: MarketDataComponentStatus) {
  if (component.ageSeconds != null) return ` · ${component.ageSeconds}s`;
  if (component.lastSuccessAt) {
    return ` · ${new Date(component.lastSuccessAt).toLocaleTimeString("zh-CN", {
      hour: "2-digit", minute: "2-digit", second: "2-digit",
    })}`;
  }
  return "";
}

export function MarketStatusLights({ compact = false, mode, singleSource }: {
  compact?: boolean;
  mode?: MarketDataConfig["mode"];
  singleSource?: MarketDataConfig["singleSource"];
}) {
  const status = useQuery({
    queryKey: ["market-data-status", mode, singleSource],
    queryFn: () => api.marketDataStatus(mode, singleSource),
    refetchInterval: 5000,
    retry: false,
  });
  if (status.isLoading) return <div className="source-lights muted">正在检测行情链路…</div>;
  if (status.isError) {
    return <div className="source-lights"><div className="source-light down-state">
      <i /><span><strong>Spring API</strong><small>无法连接</small></span>
    </div></div>;
  }
  return <div className={`source-lights ${compact ? "compact" : ""}`}>
    {status.data?.components.map((component) => (
      <div className={`source-light ${component.status.toLowerCase()}`} key={component.id}
        title={component.detail ?? component.label}>
        <i /><span><strong>{component.label}</strong>
          <small>{statusText[component.status]}{statusSuffix(component)}</small></span>
      </div>
    ))}
  </div>;
}
