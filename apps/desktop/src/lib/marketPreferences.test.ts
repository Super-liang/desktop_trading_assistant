// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import { cleanup, renderHook, waitFor } from "@testing-library/react";
import {
  portfolioRefetchInterval,
  MODE_STORAGE_KEY,
  readMarketPreferences,
  saveMarketPreferences,
  SINGLE_SOURCE_STORAGE_KEY,
  SINGLE_REFRESH_STORAGE_KEY,
  SNAPSHOT_SOURCE_STORAGE_KEY,
  useMarketPreferences,
} from "./marketPreferences";

describe("marketPreferences", () => {
  beforeEach(() => window.localStorage.clear());

  it("首次使用默认服务端来源和 10 秒单股频率", () => {
    expect(readMarketPreferences("MARKET_SNAPSHOT", "EASTMONEY", "XUEQIU")).toEqual({
      mode: "MARKET_SNAPSHOT",
      snapshotSource: "EASTMONEY",
      singleSource: "XUEQIU",
      singleRefreshSeconds: 10,
    });
  });

  it("持久化用户选择并拒绝非法本地值", () => {
    saveMarketPreferences({ mode: "SINGLE_STOCK", snapshotSource: "SINA", singleSource: "XUEQIU", singleRefreshSeconds: 2 });
    expect(readMarketPreferences("MARKET_SNAPSHOT", "EASTMONEY", "EASTMONEY")).toEqual({
      mode: "SINGLE_STOCK", snapshotSource: "SINA", singleSource: "XUEQIU", singleRefreshSeconds: 2,
    });

    window.localStorage.setItem(SNAPSHOT_SOURCE_STORAGE_KEY, "UNKNOWN");
    window.localStorage.setItem(MODE_STORAGE_KEY, "UNKNOWN");
    window.localStorage.setItem(SINGLE_SOURCE_STORAGE_KEY, "UNKNOWN");
    window.localStorage.setItem(SINGLE_REFRESH_STORAGE_KEY, "3");
    expect(readMarketPreferences("MARKET_SNAPSHOT", "EASTMONEY", "EASTMONEY")).toEqual({
      mode: "MARKET_SNAPSHOT", snapshotSource: "EASTMONEY", singleSource: "EASTMONEY", singleRefreshSeconds: 10,
    });
  });

  it("仅单股模式采用用户轮询频率", () => {
    expect(portfolioRefetchInterval("SINGLE_STOCK", 20)).toBe(20_000);
    expect(portfolioRefetchInterval("MARKET_SNAPSHOT", 20)).toBe(2_000);
  });

  it("接收其他窗口的 storage 事件后即时同步偏好", async () => {
    const hook = renderHook(() => useMarketPreferences(
      "MARKET_SNAPSHOT", "EASTMONEY", "EASTMONEY"));
    window.localStorage.setItem(MODE_STORAGE_KEY, "SINGLE_STOCK");
    window.localStorage.setItem(SNAPSHOT_SOURCE_STORAGE_KEY, "SINA");
    window.localStorage.setItem(SINGLE_SOURCE_STORAGE_KEY, "XUEQIU");
    window.localStorage.setItem(SINGLE_REFRESH_STORAGE_KEY, "5");
    window.dispatchEvent(new StorageEvent("storage", { key: SNAPSHOT_SOURCE_STORAGE_KEY }));

    await waitFor(() => expect(hook.result.current).toEqual({
      mode: "SINGLE_STOCK", snapshotSource: "SINA", singleSource: "XUEQIU", singleRefreshSeconds: 5,
    }));
    cleanup();
  });
});
