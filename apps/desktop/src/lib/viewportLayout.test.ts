import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const styles = readFileSync(new URL("../styles.css", import.meta.url), "utf8");

describe("主窗口可视区布局", () => {
  it("禁止文档级滚动并固定三类主页面高度", () => {
    expect(styles).toMatch(/html,body,#root\s*\{[^}]*height:100%[^}]*overflow:hidden/);
    expect(styles).toMatch(/\.app-shell\s*\{[^}]*height:100%[^}]*overflow:hidden/);
    expect(styles).toMatch(/\.source-settings-page\s*\{[^}]*height:100%[^}]*overflow:hidden/);
    expect(styles).toMatch(/\.admin-page\s*\{[^}]*height:100%[^}]*overflow:hidden/);
  });

  it("长列表只在所属卡片内部滚动且表头保持可见", () => {
    expect(styles).toMatch(/\.list-card \.quote-table\s*\{[^}]*overflow:auto/);
    expect(styles).toMatch(/\.admin-table\s*\{[^}]*overflow:auto/);
    expect(styles).toContain(".list-card .quote-head { position:sticky");
    expect(styles).toContain(".admin-table .admin-head { position:sticky");
  });

  it("较矮窗口启用紧凑布局", () => {
    expect(styles).toContain("@media(max-height:700px)");
  });
});
