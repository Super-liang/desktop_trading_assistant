import { useState } from "react";
import { KeyRound, X } from "lucide-react";
import { api } from "../lib/api";
import { useAuth } from "../store/auth";

export function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  const clear = useAuth((state) => state.clear);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (saving) return;
    if (newPassword !== confirmPassword) return setError("两次输入的新密码不一致");
    if (newPassword.length < 10 || newPassword.length > 72
      || !/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
      return setError("新密码需为 10 到 72 位，并同时包含字母和数字");
    }
    if (newPassword === currentPassword) return setError("新密码不能与当前密码相同");
    setSaving(true);
    setError("");
    try {
      await api.changePassword({ currentPassword, newPassword, confirmPassword });
      clear();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "密码修改失败，请稍后重试");
    } finally {
      setSaving(false);
    }
  }

  return <div className="dialog-backdrop" role="presentation">
    <form className="dialog password-dialog" onSubmit={submit}>
      <div className="dialog-title">
        <div><small>ACCOUNT SECURITY</small><h3><KeyRound size={20} /> 修改密码</h3></div>
        <button type="button" className="icon-button" aria-label="关闭修改密码" onClick={onClose}>
          <X size={19} />
        </button>
      </div>
      <p className="dialog-hint">修改成功后，所有设备的登录会话都会失效，需要使用新密码重新登录。</p>
      <label>当前密码<input type="password" autoComplete="current-password" value={currentPassword}
        onChange={(event) => setCurrentPassword(event.target.value)} required /></label>
      <label>新密码<input type="password" autoComplete="new-password" value={newPassword}
        onChange={(event) => setNewPassword(event.target.value)} minLength={10} maxLength={72} required /></label>
      <label>确认新密码<input type="password" autoComplete="new-password" value={confirmPassword}
        onChange={(event) => setConfirmPassword(event.target.value)} minLength={10} maxLength={72} required /></label>
      {error && <div className="form-error">{error}</div>}
      <button className="primary-button" disabled={saving}>{saving ? "正在修改…" : "确认修改"}</button>
    </form>
  </div>;
}
