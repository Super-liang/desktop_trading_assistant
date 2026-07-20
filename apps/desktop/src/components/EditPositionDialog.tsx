import { useState } from "react";
import { X } from "lucide-react";
import { api } from "../lib/api";
import type { PortfolioItem } from "../types";

export function EditPositionDialog({ item, onClose, onSaved }: {
  item: PortfolioItem; onClose: () => void; onSaved: () => void;
}) {
  const [quantity, setQuantity] = useState(item.quantity);
  const [costPrice, setCostPrice] = useState(item.costPrice);
  const [error, setError] = useState("");

  async function save(event: React.FormEvent) {
    event.preventDefault();
    try {
      await api.updateItem(item.id, {
        instrumentId: item.instrumentId,
        displayName: item.displayName,
        quantity,
        costPrice,
        sortOrder: item.sortOrder,
      });
      onSaved();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "保存失败");
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <form className="dialog" onSubmit={save}>
        <div className="dialog-title">
          <div><small>EDIT POSITION</small><h3>{item.displayName} · {item.instrumentId}</h3></div>
          <button type="button" className="icon-button" onClick={onClose}><X size={19} /></button>
        </div>
        <div className="two-fields">
          <label>持仓数量<input type="number" min="0" step="0.0001" value={quantity}
            onChange={(event) => setQuantity(Number(event.target.value))} /></label>
          <label>单位成本<input type="number" min="0.0001" step="0.0001" value={costPrice}
            onChange={(event) => setCostPrice(Number(event.target.value))} required /></label>
        </div>
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button">保存持仓</button>
      </form>
    </div>
  );
}
