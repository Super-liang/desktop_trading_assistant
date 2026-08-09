import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Database, Gauge, RadioTower, Save } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import {
  saveMarketPreferences,
  SINGLE_REFRESH_OPTIONS,
  useMarketPreferences,
  type MarketMode,
  type MarketPreferences,
  type SingleSource,
} from "../lib/marketPreferences";
import type { MarketDataConfig } from "../types";
import { MarketStatusLights } from "./MarketStatusLights";

type EditableConfig = Pick<MarketDataConfig,
  "provider" | "mode" | "snapshotSource" | "singleSource" | "refreshSeconds">;

export function MarketDataSettings({ isAdmin, onBack }: { isAdmin: boolean; onBack: () => void }) {
  const config = useQuery({ queryKey: ["market-data-config"], queryFn: api.marketDataConfig });
  const [form, setForm] = useState<EditableConfig | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const savedPreferences = useMarketPreferences(
    config.data?.mode ?? "MARKET_SNAPSHOT",
    config.data?.snapshotSource ?? "SINA",
    config.data?.singleSource ?? "EASTMONEY",
  );
  const [clientForm, setClientForm] = useState<MarketPreferences>(savedPreferences);
  const clientFormDirty = useRef(false);
  useEffect(() => {
    if (config.data) setForm({
      provider: config.data.provider,
      mode: config.data.mode,
      snapshotSource: "SINA",
      singleSource: config.data.singleSource,
      refreshSeconds: config.data.refreshSeconds,
    });
  }, [config.data]);
  useEffect(() => {
    if (!clientFormDirty.current) setClientForm(savedPreferences);
  }, [savedPreferences]);

  function selectMode(mode: MarketMode) {
    clientFormDirty.current = true;
    setClientForm({ ...clientForm, mode });
    if (isAdmin && form) setForm({ ...form, mode });
  }

  function selectSingleSource(source: SingleSource) {
    clientFormDirty.current = true;
    setClientForm({ ...clientForm, singleSource: source });
    if (isAdmin && form) setForm({ ...form, singleSource: source });
  }

  async function save() {
    if (!form) return;
    setSaving(true);
    setMessage("");
    try {
      if (isAdmin) {
        await api.updateMarketDataConfig(form);
        await config.refetch();
      }
      saveMarketPreferences(clientForm);
      clientFormDirty.current = false;
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
          <p>{isAdmin ? "管理员可修改系统策略与本机偏好" : "可修改本机行情偏好；系统策略仅管理员可改"}</p></div>
          <MarketStatusLights compact mode={clientForm.mode}
            singleSource={clientForm.singleSource} /></div>
        {config.isError ? <div className="empty-state">
          行情源配置读取失败，请检查后端服务后重试。
          <button className="secondary-button small" onClick={() => config.refetch()}>重新读取</button>
        </div> : config.isLoading || !form ? <div className="empty-state">正在读取配置…</div> : <div className="source-config-content">
          <div className="mode-grid">
            <button className={clientForm.mode === "MARKET_SNAPSHOT" ? "selected" : ""}
              onClick={() => selectMode("MARKET_SNAPSHOT")}>
              <RadioTower /><strong>全市场快照模式</strong><span>延迟较高 · Redis 共享缓存</span>
            </button>
            <button className={clientForm.mode === "SINGLE_STOCK" ? "selected" : ""}
              onClick={() => selectMode("SINGLE_STOCK")}>
              <Gauge /><strong>单只股票模式</strong><span>低延迟 · 服务端按代码查询</span>
            </button>
          </div>
          {clientForm.mode === "MARKET_SNAPSHOT" ? <div className="source-form-section">
            <div><h3>全市场行情来源</h3><p>服务端统一维护新浪快照，交易时段按管理员设置的频率刷新。</p></div>
            <div className="source-options">
              <div className="source-option-static" aria-label="新浪财经全市场行情">
                <RadioTower size={18} />
                <span><strong>新浪财经</strong><small>A股、港股全市场快照；美股按持仓查询</small></span>
              </div>
            </div>
            <label className="refresh-field">服务端快照刷新频率（秒）
              <input type="number" min={30} max={300}
                disabled={!isAdmin} value={form.refreshSeconds}
                onChange={(event) => setForm({ ...form, refreshSeconds: Number(event.target.value) })} />
              <small>仅管理员可改 · 各市场交易时段执行</small></label>
          </div> : <div className="source-form-section">
            <div><h3>本机单只股票来源</h3><p>当前客户端按所选来源请求；桌面端不直连 AKShare，由 Java API 统一鉴权、限流和格式转换。</p></div>
            <div className="source-options">
              <label><input type="radio" name="single-source" value="EASTMONEY"
                checked={clientForm.singleSource === "EASTMONEY"}
                onChange={() => selectSingleSource("EASTMONEY")} />
                <span><strong>东方财富</strong><small>stock_bid_ask_em · 沪深证券</small></span></label>
              <label><input type="radio" name="single-source" value="XUEQIU"
                checked={clientForm.singleSource === "XUEQIU"}
                onChange={() => selectSingleSource("XUEQIU")} />
                <span><strong>雪球</strong><small>stock_individual_spot_xq</small></span></label>
            </div>
            <small>{isAdmin ? "管理员保存时同时更新服务端默认源与本机选择" : "仅影响当前设备，不会修改服务端默认源"}</small>
            <div className="client-refresh-section"><h3>客户端查询频率</h3>
              <p>主页面与透明小窗按此频率轮询，默认 10 秒。</p>
              <div className="frequency-options">{SINGLE_REFRESH_OPTIONS.map((seconds) =>
                <button type="button" key={seconds}
                  className={clientForm.singleRefreshSeconds === seconds ? "selected" : ""}
                  onClick={() => {
                    clientFormDirty.current = true;
                    setClientForm({ ...clientForm, singleRefreshSeconds: seconds });
                  }}>
                  {seconds} 秒
                </button>)}</div>
            </div>
          </div>}
          <div className="source-save-row"><span className={message.includes("失败") ? "down" : "up"}>{message}</span>
            <button className="primary-button small" disabled={saving} onClick={save}>
              <Save size={16} />{saving ? "保存中…" : isAdmin ? "保存配置" : "保存本机设置"}</button></div>
        </div>}
      </section>
    </div>
  </main>;
}
