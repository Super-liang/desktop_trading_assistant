import { useEffect, useState } from "react";

export const WINDOW_VISIBILITY_EVENT = "window-visibility-changed";

type WindowVisibilityPayload = {
  label: string;
  visible: boolean;
};

export function isTauriRuntime() {
  return "__TAURI_INTERNALS__" in window;
}

export function effectiveWindowVisibility(visible: boolean, minimized: boolean) {
  return visible && !minimized;
}

export function useWindowVisibility(label: "main" | "ticker") {
  const native = isTauriRuntime();
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    if (!native) {
      setVisible(true);
      return;
    }

    let disposed = false;
    let receivedEvent = false;
    let unlisten: (() => void) | undefined;
    Promise.all([
      import("@tauri-apps/api/event"),
      import("@tauri-apps/api/webviewWindow"),
    ]).then(async ([events, windows]) => {
      unlisten = await events.listen<WindowVisibilityPayload>(
        WINDOW_VISIBILITY_EVENT,
        (event) => {
          if (!disposed && event.payload.label === label) {
            receivedEvent = true;
            setVisible(event.payload.visible);
          }
        },
      );
      const target = await windows.WebviewWindow.getByLabel(label);
      if (!disposed && target) {
        const [isVisible, isMinimized] = await Promise.all([
          target.isVisible(),
          target.isMinimized(),
        ]);
        if (!disposed && !receivedEvent) {
          setVisible(effectiveWindowVisibility(isVisible, isMinimized));
        }
      }
    }).catch(() => {
      if (!disposed) setVisible(true);
    });

    return () => {
      disposed = true;
      unlisten?.();
    };
  }, [label, native]);

  return visible;
}
