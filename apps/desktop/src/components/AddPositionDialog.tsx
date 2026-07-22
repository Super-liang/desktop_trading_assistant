import { useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { api } from "../lib/api";
import type { SearchResult } from "../types";

export function AddPositionDialog({ onClose, onAdded }: { onClose: () => void; onAdded: () => void }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [selected, setSelected] = useState<SearchResult | null>(null);
  const [quantity, setQuantity] = useState("");
  const [costPrice, setCostPrice] = useState("");
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
        const nextResults = await api.search(keyword, controller.signal);
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
  }, [query]);

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
        quantity: parsedQuantity,
        costPrice: parsedCost,
        sortOrder: 0,
      });
      onAdded();
    } catch (reason) {
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
        <div className="search-box"><Search size={17} />
          <input autoFocus value={query} onChange={(e) => {
            setQuery(e.target.value);
            setSelected(null);
          }}
                 placeholder="输入代码或名称，例如 600519" />
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
          <label>持仓数量<input type="text" inputMode="decimal" value={quantity}
            placeholder="0（可不持仓）" onChange={(e) => setQuantity(e.target.value)} /></label>
          <label>单位成本<input type="text" inputMode="decimal" value={costPrice}
            placeholder="无持仓可留空" onChange={(e) => setCostPrice(e.target.value)} /></label>
        </div>
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button" disabled={saving}>{saving ? "正在保存…" : "加入盯盘"}</button>
      </form>
    </div>
  );
}
