import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Database, Gauge, RadioTower, Save } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { MarketDataConfig } from "../types";
import { MarketStatusLights } from "./MarketStatusLights";

type EditableConfig = Pick<MarketDataConfig,
  "provider" | "mode" | "snapshotSource" | "singleSource" | "refreshSeconds">;

export function MarketDataSettings({ isAdmin, onBack }: { isAdmin: boolean; onBack: () => void }) {
  const config = useQuery({ queryKey: ["market-data-config"], queryFn: api.marketDataConfig });
  const [form, setForm] = useState<EditableConfig | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => {
    if (config.data) setForm({
      provider: config.data.provider,
      mode: config.data.mode,
      snapshotSource: config.data.snapshotSource,
      singleSource: config.data.singleSource,
      refreshSeconds: config.data.refreshSeconds,
    });
  }, [config.data]);

  async function save() {
    if (!form || !isAdmin) return;
    setSaving(true);
    setMessage("");
    try {
      await api.updateMarketDataConfig(form);
      await Promise.all([config.refetch()]);
      setMessage("配置已生效");
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  return <main className="source-settings-page">
    <header><button className="icon-button" onClick={onBack}><ArrowLeft /></button>
      <div><p className="eyebrow">MARKET DATA</p><h1>实时行情源</h1>
        <p>系统级行情策略 · AKShare 仅供非商业研究验证</p></div></header>
    <div className="source-settings-layout">
      <aside className="provider-list"><p className="eyebrow">PROVIDERS</p>
        <button className="active"><Database size={18} /><span><strong>AKShare</strong>
          <small>当前已接入</small></span></button>
        <div className="provider-coming">其他行情源将在后续版本接入</div>
      </aside>
      <section className="source-config-panel">
        <div className="source-panel-head"><div><h2>AKShare 行情配置</h2>
          <p>{isAdmin ? "管理员可修改系统级策略" : "当前账号仅可查看配置"}</p></div>
          <MarketStatusLights compact /></div>
        {config.isError ? <div className="empty-state">
          行情源配置读取失败，请检查后端服务后重试。
          <button className="secondary-button small" onClick={() => config.refetch()}>重新读取</button>
        </div> : config.isLoading || !form ? <div className="empty-state">正在读取配置…</div> : <>
          <div className="mode-grid">
            <button className={form.mode === "MARKET_SNAPSHOT" ? "selected" : ""}
              disabled={!isAdmin} onClick={() => setForm({ ...form, mode: "MARKET_SNAPSHOT" })}>
              <RadioTower /><strong>全市场快照模式</strong><span>延迟较高 · Redis 共享缓存</span>
            </button>
            <button className={form.mode === "SINGLE_STOCK" ? "selected" : ""}
              disabled={!isAdmin} onClick={() => setForm({ ...form, mode: "SINGLE_STOCK" })}>
              <Gauge /><strong>单只股票模式</strong><span>低延迟 · 服务端按代码查询</span>
            </button>
          </div>
          {form.mode === "MARKET_SNAPSHOT" ? <div className="source-form-section">
            <div><h3>全市场快照来源</h3><p>后端在交易时段定时抓取，用户查询只读取 Redis。</p></div>
            <div className="source-options">
              <label><input type="radio" name="snapshot-source" value="EASTMONEY"
                disabled={!isAdmin} checked={form.snapshotSource === "EASTMONEY"}
                onChange={() => setForm({ ...form, snapshotSource: "EASTMONEY" })} />
                <span><strong>东方财富</strong><small>stock_zh_a_spot_em</small></span></label>
              <label><input type="radio" name="snapshot-source" value="SINA"
                disabled={!isAdmin} checked={form.snapshotSource === "SINA"}
                onChange={() => setForm({ ...form, snapshotSource: "SINA",
                  refreshSeconds: Math.max(30, form.refreshSeconds) })} />
                <span><strong>新浪财经</strong><small>stock_zh_a_spot · 最小 30 秒</small></span></label>
            </div>
            <label className="refresh-field">定时频率（秒）
              <input type="number" min={form.snapshotSource === "SINA" ? 30 : 5} max={300}
                disabled={!isAdmin} value={form.refreshSeconds}
                onChange={(event) => setForm({ ...form, refreshSeconds: Number(event.target.value) })} />
              <small>仅交易时段 09:15–11:30、13:00–15:00 执行</small></label>
          </div> : <div className="source-form-section">
            <div><h3>单只股票来源</h3><p>桌面端不直连 AKShare；Java API 统一鉴权、限流和格式转换。</p></div>
            <div className="source-options">
              <label><input type="radio" name="single-source" value="EASTMONEY"
                disabled={!isAdmin} checked={form.singleSource === "EASTMONEY"}
                onChange={() => setForm({ ...form, singleSource: "EASTMONEY" })} />
                <span><strong>东方财富</strong><small>stock_bid_ask_em · 沪深证券</small></span></label>
              <label><input type="radio" name="single-source" value="XUEQIU"
                disabled={!isAdmin} checked={form.singleSource === "XUEQIU"}
                onChange={() => setForm({ ...form, singleSource: "XUEQIU" })} />
                <span><strong>雪球</strong><small>stock_individual_spot_xq</small></span></label>
            </div>
          </div>}
          <div className="source-save-row"><span className={message.includes("失败") ? "down" : "up"}>{message}</span>
            {isAdmin && <button className="primary-button small" disabled={saving} onClick={save}>
              <Save size={16} />{saving ? "保存中…" : "保存配置"}</button>}</div>
        </>}
      </section>
    </div>
  </main>;
}
