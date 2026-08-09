import { useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { api, ApiError } from "../lib/api";
import { marketLocalDate } from "../lib/marketDate";
import type { Market, SearchResult } from "../types";

const markets: Array<{ value: Market; label: string }> = [
  { value: "A_SHARE", label: "A股" }, { value: "HK_STOCK", label: "港股" },
  { value: "US_STOCK", label: "美股" }, { value: "PUBLIC_FUND", label: "公募基金" },
];

export function AddPositionDialog({ onClose, onAdded }: {
  onClose: () => void; onAdded: (market: Market) => void;
}) {
  const [market, setMarket] = useState<Market>("A_SHARE");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [selected, setSelected] = useState<SearchResult | null>(null);
  const [quantity, setQuantity] = useState("");
  const [costPrice, setCostPrice] = useState("");
  const [openedOn, setOpenedOn] = useState(() => marketLocalDate("A_SHARE"));
  const [error, setError] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const keyword = query.trim();
    if (!keyword) {
      setResults([]);
      setSearchError("");
      setSearching(false);
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSearching(true);
      setSearchError("");
      try {
        const nextResults = await api.search(keyword, controller.signal, market);
        if (!controller.signal.aborted) setResults(nextResults);
      } catch (reason) {
        if (controller.signal.aborted) return;
        setResults([]);
        setSearchError(reason instanceof Error ? reason.message : "证券搜索失败，请重试");
      } finally {
        if (!controller.signal.aborted) setSearching(false);
      }
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [market, query]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (saving) return;
    if (!selected) return setError("请先选择一只证券");
    if (!/^\d*(?:\.\d{0,4})?$/.test(quantity) || !/^\d*(?:\.\d{0,4})?$/.test(costPrice)) {
      return setError("持仓数量和成本最多支持 4 位小数");
    }
    const parsedQuantity = quantity === "" ? 0 : Number(quantity);
    const parsedCost = costPrice === "" ? null : Number(costPrice);
    if (!Number.isFinite(parsedQuantity) || parsedQuantity < 0) return setError("持仓数量不能小于 0");
    if (parsedQuantity > 0 && (parsedCost === null || !Number.isFinite(parsedCost) || parsedCost <= 0)) {
      return setError("持仓数量大于 0 时，请输入大于 0 的单位成本");
    }
    setSaving(true);
    setError("");
    try {
      await api.addItem({
        instrumentId: selected.instrumentId,
        displayName: selected.name,
        market,
        openedOn,
        quantity: parsedQuantity,
        costPrice: parsedCost,
        sortOrder: 0,
      });
      onAdded(market);
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === "POSITION_ALREADY_EXISTS") {
        const existingPositionId = reason.details.existingPositionId;
        if (typeof existingPositionId === "string" && parsedQuantity > 0
          && parsedCost !== null && parsedCost > 0) {
          if (!window.confirm("该持仓已存在，是否累加持仓？")) return;
          try {
            await api.accumulateItem(existingPositionId, {
              quantity: parsedQuantity, costPrice: parsedCost,
            });
            onAdded(market);
            return;
          } catch (accumulateReason) {
            setError(accumulateReason instanceof Error ? accumulateReason.message : "累加持仓失败");
            return;
          }
        }
        setError("该持仓已存在；如需增加持仓，请填写大于 0 的数量和单位成本");
        return;
      }
      const message = reason instanceof Error ? reason.message : "保存失败";
      setError(message.includes("请求超时")
        ? "保存结果暂未确认，请先核对自选列表；确认未保存后再重试"
        : message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <form className="dialog" onSubmit={save}>
        <div className="dialog-title"><div><small>NEW POSITION</small><h3>添加自选 / 持仓</h3></div>
          <button type="button" className="icon-button" onClick={onClose}><X size={19} /></button>
        </div>
        <div className="market-picker" role="group" aria-label="选择市场">
          {markets.map((item) => <button type="button" key={item.value}
            className={market === item.value ? "selected" : ""} onClick={() => {
              setMarket(item.value); setOpenedOn(marketLocalDate(item.value));
              setQuery(""); setResults([]); setSelected(null);
            }}>{item.label}</button>)}
        </div>
        <div className="search-box"><Search size={17} />
          <input autoFocus value={query} onChange={(e) => {
            setQuery(e.target.value);
            setSelected(null);
          }}
                 placeholder={market === "PUBLIC_FUND" ? "输入基金代码或名称" : "输入代码或名称，例如 600519"} />
        </div>
        <div className="search-results">
          {searching && <div className="search-feedback">正在查询证券目录…</div>}
          {searchError && <div className="form-error">{searchError}</div>}
          {results.map((result) => (
            <button type="button" key={result.instrumentId}
                    className={selected?.instrumentId === result.instrumentId ? "selected" : ""}
                    onClick={() => setSelected(result)}>
              <span><strong>{result.name}</strong><small>{result.instrumentId}</small></span>
              <span className="asset-tag">{result.assetType}</span>
            </button>
          ))}
        </div>
        <div className="two-fields">
          <label>{market === "PUBLIC_FUND" ? "持有份额" : "持仓数量"}<input type="text" inputMode="decimal" value={quantity}
            placeholder="0（可不持仓）" onChange={(e) => setQuantity(e.target.value)} /></label>
          <label>单位成本<input type="text" inputMode="decimal" value={costPrice}
            placeholder="无持仓可留空" onChange={(e) => setCostPrice(e.target.value)} /></label>
        </div>
        <label>建仓日期<input type="date" value={openedOn} max={marketLocalDate(market)}
          onChange={(event) => setOpenedOn(event.target.value)} /></label>
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button" disabled={saving}>{saving ? "正在保存…" : "加入盯盘"}</button>
      </form>
    </div>
  );
}
