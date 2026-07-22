import { useState } from "react";
import { X } from "lucide-react";
import { api } from "../lib/api";
import type { PortfolioItem } from "../types";

export function EditPositionDialog({ item, onClose, onSaved }: {
  item: PortfolioItem; onClose: () => void; onSaved: () => void;
}) {
  const [quantity, setQuantity] = useState(String(item.quantity));
  const [costPrice, setCostPrice] = useState(item.costPrice > 0 ? String(item.costPrice) : "");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (saving) return;
    const parsedQuantity = quantity === "" ? 0 : Number(quantity);
    const parsedCost = costPrice === "" ? null : Number(costPrice);
    if (!/^\d*(?:\.\d{0,4})?$/.test(quantity) || !/^\d*(?:\.\d{0,4})?$/.test(costPrice)
      || !Number.isFinite(parsedQuantity) || parsedQuantity < 0) {
      return setError("持仓数量和成本最多支持 4 位小数");
    }
    if (parsedQuantity > 0 && (parsedCost === null || !Number.isFinite(parsedCost) || parsedCost <= 0)) {
      return setError("持仓数量大于 0 时，请输入大于 0 的单位成本");
    }
    setSaving(true);
    try {
      await api.updateItem(item.id, {
        instrumentId: item.instrumentId,
        displayName: item.displayName,
        quantity: parsedQuantity,
        costPrice: parsedCost,
        sortOrder: item.sortOrder,
      });
      onSaved();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "保存失败");
    } finally {
      setSaving(false);
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
          <label>持仓数量<input type="text" inputMode="decimal" value={quantity}
            onChange={(event) => setQuantity(event.target.value)} /></label>
          <label>单位成本<input type="text" inputMode="decimal" value={costPrice}
            placeholder="无持仓可留空" onChange={(event) => setCostPrice(event.target.value)} /></label>
        </div>
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button" disabled={saving}>{saving ? "正在保存…" : "保存持仓"}</button>
      </form>
    </div>
  );
}
