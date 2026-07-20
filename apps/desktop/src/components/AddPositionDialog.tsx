import { useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { api } from "../lib/api";
import type { SearchResult } from "../types";

export function AddPositionDialog({ onClose, onAdded }: { onClose: () => void; onAdded: () => void }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [selected, setSelected] = useState<SearchResult | null>(null);
  const [quantity, setQuantity] = useState(0);
  const [costPrice, setCostPrice] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => api.search(query).then(setResults).catch(() => setResults([])), 200);
    return () => window.clearTimeout(timer);
  }, [query]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (!selected) return setError("请先选择一只证券");
    try {
      await api.addItem({
        instrumentId: selected.instrumentId,
        displayName: selected.name,
        quantity,
        costPrice,
        sortOrder: 0,
      });
      onAdded();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "保存失败");
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <form className="dialog" onSubmit={save}>
        <div className="dialog-title"><div><small>NEW POSITION</small><h3>添加自选 / 持仓</h3></div>
          <button type="button" className="icon-button" onClick={onClose}><X size={19} /></button>
        </div>
        <div className="search-box"><Search size={17} />
          <input autoFocus value={query} onChange={(e) => setQuery(e.target.value)}
                 placeholder="输入代码或名称，例如 600519" />
        </div>
        <div className="search-results">
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
          <label>持仓数量<input type="number" min="0" step="0.0001" value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))} /></label>
          <label>单位成本<input type="number" min="0.0001" step="0.0001" value={costPrice}
            onChange={(e) => setCostPrice(Number(e.target.value))} required /></label>
        </div>
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button">加入盯盘</button>
      </form>
    </div>
  );
}

