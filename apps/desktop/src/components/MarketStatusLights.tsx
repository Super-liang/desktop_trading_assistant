import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
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
  REDIS_SNAPSHOT_A_SHARE_SINA: "A股新浪缓存",
  REDIS_SNAPSHOT_HK_STOCK_SINA: "港股新浪缓存",
  REDIS_SNAPSHOT_US_STOCK_SINA: "美股新浪缓存",
  "UPSTREAM_A_SHARE:SNAPSHOT:SINA": "A股新浪行情",
  "UPSTREAM_HK_STOCK:SNAPSHOT:SINA": "港股新浪行情",
  "UPSTREAM_US_STOCK:POSITION:SINA": "美股新浪行情",
  "A_SHARE:SNAPSHOT:SINA": "A股新浪行情",
  "HK_STOCK:SNAPSHOT:SINA": "港股新浪行情",
  "US_STOCK:POSITION:SINA": "美股新浪行情",
  UPSTREAM_SINGLE_EASTMONEY: "东方财富单股行情",
  UPSTREAM_SINGLE_XUEQIU: "雪球单股行情",
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

export function MarketStatusLights({ compact = false, collapsible = false, mode, singleSource, enabled = true }: {
  compact?: boolean;
  collapsible?: boolean;
  mode?: MarketDataConfig["mode"];
  singleSource?: MarketDataConfig["singleSource"];
  enabled?: boolean;
}) {
  const [expanded, setExpanded] = useState(!collapsible);
  const trigger = useRef<HTMLButtonElement>(null);
  const status = useQuery({
    queryKey: ["market-data-status", mode, singleSource],
    queryFn: () => api.marketDataStatus(mode, singleSource),
    refetchInterval: 15_000,
    enabled,
    retry: false,
  });
  useEffect(() => {
    if (!expanded) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setExpanded(false); trigger.current?.focus(); }
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [expanded]);
  const components = status.data?.components ?? [];
  const abnormal = status.isError || components.some((item) =>
    item.status === "DOWN" || item.status === "DEGRADED");
  const content = status.isLoading ? <div className="source-lights muted">正在检测行情链路…</div>
    : status.isError ? <div className="source-lights"><div className="source-light down-state">
      <i /><span><strong>后端服务</strong><small>无法连接</small></span>
    </div></div> : <div className={`source-lights ${compact ? "compact" : ""}`}>
    {components.map((component) => (
      <div className={`source-light ${component.status.toLowerCase()}`} key={component.id}
        title={component.detail ?? componentLabel(component)}>
        <i /><span><strong>{componentLabel(component)}</strong>
          <small>{statusText[component.status]}{statusSuffix(component)}</small></span>
      </div>
    ))}
  </div>;
  if (!collapsible) return content;
  return <div className="connectivity-check">
    <button ref={trigger} className="secondary-button connectivity-trigger"
      aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>
      联通检测{abnormal && <span className="alert-dot" aria-label="存在异常" />}
    </button>
    {expanded && <div className="connectivity-panel">{content}</div>}
  </div>;
}
