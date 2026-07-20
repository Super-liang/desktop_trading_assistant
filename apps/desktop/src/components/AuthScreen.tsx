import { useState } from "react";
import { Activity, ArrowRight, ShieldCheck } from "lucide-react";
import { api } from "../lib/api";
import { useAuth } from "../store/auth";

export function AuthScreen() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const setSession = useAuth((state) => state.setSession);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const session = mode === "login"
        ? await api.login({ email, password })
        : await api.register({ email, displayName, password });
      setSession(session);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "请求失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-story">
        <div className="brand"><Activity size={20} /> 隐线</div>
        <div>
          <p className="eyebrow">DESKTOP MARKET COMPANION</p>
          <h1>把行情留在<br /><em>视线边缘。</em></h1>
          <p className="lead">为 Windows 与 macOS 打造的轻量盯盘层。透明、克制，老板键一触即隐。</p>
        </div>
        <div className="trust-line"><ShieldCheck size={17} /> 持仓数据隔离 · 行情来源可追溯 · 不接交易</div>
      </section>
      <section className="auth-panel">
        <form onSubmit={submit}>
          <p className="eyebrow">{mode === "login" ? "WELCOME BACK" : "CREATE ACCOUNT"}</p>
          <h2>{mode === "login" ? "继续你的盯盘" : "创建个人工作台"}</h2>
          {mode === "register" && (
            <label>怎么称呼你
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)}
                     placeholder="例如：林先生" minLength={2} required />
            </label>
          )}
          <label>邮箱
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   placeholder="you@example.com" required />
          </label>
          <label>密码
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                   placeholder="至少 10 位，包含字母与数字" minLength={10} required />
          </label>
          {error && <div className="form-error">{error}</div>}
          <button className="primary-button" disabled={loading}>
            {loading ? "连接中…" : mode === "login" ? "登录" : "注册"} <ArrowRight size={17} />
          </button>
          <button type="button" className="text-button"
                  onClick={() => setMode(mode === "login" ? "register" : "login")}>
            {mode === "login" ? "第一次使用？创建账号" : "已有账号？返回登录"}
          </button>
        </form>
      </section>
    </main>
  );
}

