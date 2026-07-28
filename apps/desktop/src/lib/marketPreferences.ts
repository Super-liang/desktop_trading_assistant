import { useEffect, useState } from "react";
import type { MarketDataConfig } from "../types";

export type SnapshotSource = MarketDataConfig["snapshotSource"];
export type SingleSource = MarketDataConfig["singleSource"];
export type MarketMode = MarketDataConfig["mode"];
export type SingleRefreshSeconds = 2 | 5 | 10 | 20;

export const MODE_STORAGE_KEY = "market.mode";
export const SNAPSHOT_SOURCE_STORAGE_KEY = "market.snapshotSource";
export const SINGLE_SOURCE_STORAGE_KEY = "market.singleSource";
export const SINGLE_REFRESH_STORAGE_KEY = "market.singleRefreshSeconds";
export const DEFAULT_SINGLE_REFRESH_SECONDS: SingleRefreshSeconds = 10;
export const SINGLE_REFRESH_OPTIONS: SingleRefreshSeconds[] = [2, 5, 10, 20];
const CHANGE_EVENT = "market-preferences-changed";

export type MarketPreferences = {
  mode: MarketMode;
  snapshotSource: SnapshotSource;
  singleSource: SingleSource;
  singleRefreshSeconds: SingleRefreshSeconds;
};

function validMode(value: string | null): value is MarketMode {
  return value === "MARKET_SNAPSHOT" || value === "SINGLE_STOCK";
}

function validSource(value: string | null): value is SnapshotSource {
  return value === "SINA" || value === "EASTMONEY";
}

function validSingleSource(value: string | null): value is SingleSource {
  return value === "EASTMONEY" || value === "XUEQIU";
}

function validRefresh(value: number): value is SingleRefreshSeconds {
  return SINGLE_REFRESH_OPTIONS.includes(value as SingleRefreshSeconds);
}

export function readMarketPreferences(
  defaultMode: MarketMode,
  defaultSource: SnapshotSource,
  defaultSingleSource: SingleSource,
  storage: Pick<Storage, "getItem"> = window.localStorage,
): MarketPreferences {
  const savedMode = storage.getItem(MODE_STORAGE_KEY);
  const savedSource = storage.getItem(SNAPSHOT_SOURCE_STORAGE_KEY);
  const savedSingleSource = storage.getItem(SINGLE_SOURCE_STORAGE_KEY);
  const savedRefresh = Number(storage.getItem(SINGLE_REFRESH_STORAGE_KEY));
  return {
    mode: validMode(savedMode) ? savedMode : defaultMode,
    snapshotSource: validSource(savedSource) ? savedSource : defaultSource,
    singleSource: validSingleSource(savedSingleSource) ? savedSingleSource : defaultSingleSource,
    singleRefreshSeconds: validRefresh(savedRefresh)
      ? savedRefresh : DEFAULT_SINGLE_REFRESH_SECONDS,
  };
}

export function saveMarketPreferences(
  preferences: MarketPreferences,
  storage: Pick<Storage, "setItem"> = window.localStorage,
) {
  storage.setItem(MODE_STORAGE_KEY, preferences.mode);
  storage.setItem(SNAPSHOT_SOURCE_STORAGE_KEY, preferences.snapshotSource);
  storage.setItem(SINGLE_SOURCE_STORAGE_KEY, preferences.singleSource);
  storage.setItem(SINGLE_REFRESH_STORAGE_KEY, String(preferences.singleRefreshSeconds));
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: preferences }));
  if ("__TAURI_INTERNALS__" in window) {
    void import("@tauri-apps/api/event")
      .then(({ emit }) => emit(CHANGE_EVENT, preferences))
      .catch(() => undefined);
  }
}

export function portfolioRefetchInterval(
  mode: MarketDataConfig["mode"],
  singleRefreshSeconds: SingleRefreshSeconds,
  snapshotRefreshSeconds: number,
) {
  if (mode === "SINGLE_STOCK") return singleRefreshSeconds * 1000;
  const safeRefreshSeconds = Number.isFinite(snapshotRefreshSeconds) && snapshotRefreshSeconds > 0
    ? snapshotRefreshSeconds : 30;
  return Math.max(5, safeRefreshSeconds) * 1000;
}

export function useMarketPreferences(defaultMode: MarketMode,
  defaultSource: SnapshotSource, defaultSingleSource: SingleSource) {
  const [preferences, setPreferences] = useState(() =>
    readMarketPreferences(defaultMode, defaultSource, defaultSingleSource));

  useEffect(() => {
    setPreferences(readMarketPreferences(defaultMode, defaultSource, defaultSingleSource));
  }, [defaultMode, defaultSource, defaultSingleSource]);

  useEffect(() => {
    const reload = () => setPreferences(
      readMarketPreferences(defaultMode, defaultSource, defaultSingleSource));
    const receive = (event: Event) => {
      const detail = (event as CustomEvent<MarketPreferences>).detail;
      setPreferences(detail ?? readMarketPreferences(defaultMode, defaultSource, defaultSingleSource));
    };
    window.addEventListener("storage", reload);
    window.addEventListener(CHANGE_EVENT, receive);
    let unlisten: (() => void) | undefined;
    if ("__TAURI_INTERNALS__" in window) {
      void import("@tauri-apps/api/event")
        .then(async ({ listen }) => {
          unlisten = await listen<MarketPreferences>(CHANGE_EVENT, (event) => {
            if (event.payload) setPreferences(event.payload);
          });
        })
        .catch(() => undefined);
    }
    return () => {
      window.removeEventListener("storage", reload);
      window.removeEventListener(CHANGE_EVENT, receive);
      unlisten?.();
    };
  }, [defaultMode, defaultSource, defaultSingleSource]);

  return preferences;
}
