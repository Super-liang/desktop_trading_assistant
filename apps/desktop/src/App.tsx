import { useEffect, useState } from "react";
import { useAuth } from "./store/auth";
import { AuthScreen } from "./components/AuthScreen";
import { Dashboard } from "./components/Dashboard";
import { TickerWindow } from "./components/TickerWindow";

export default function App() {
  const session = useAuth((state) => state.session);
  const setSession = useAuth((state) => state.setSession);
  const clear = useAuth((state) => state.clear);
  const [nativeWarning, setNativeWarning] = useState("");
  const ticker = new URLSearchParams(window.location.search).get("view") === "ticker";
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    if ("__TAURI_INTERNALS__" in window) {
      Promise.all([import("@tauri-apps/api/event"), import("@tauri-apps/api/core")])
        .then(async ([events, core]) => {
          unlisten = await events.listen("session-sync", (event) => {
            if (event.payload) setSession(event.payload as Parameters<typeof setSession>[0]);
            else clear();
          });
          const warning = await core.invoke<string | null>("startup_warning");
          if (warning) setNativeWarning(warning);
        })
        .catch(() => setNativeWarning("原生能力初始化失败，请从托盘重新打开应用"));
    }
    return () => unlisten?.();
  }, [clear, setSession]);

  useEffect(() => {
    if (ticker || !session || !("__TAURI_INTERNALS__" in window)) return;
    import("@tauri-apps/api/event")
      .then(({ emitTo }) => emitTo("ticker", "session-sync", session))
      .catch(() => undefined);
  }, [session, ticker]);

  // 透明小窗不承载认证页面；退出后保持空白，避免隐藏透明 WebView 渲染整页导致 WebKit 抖动。
  const content = ticker
    ? session ? <TickerWindow /> : null
    : session ? <Dashboard /> : <AuthScreen />;
  return <>{nativeWarning && <div className="native-warning">{nativeWarning}</div>}{content}</>;
}
