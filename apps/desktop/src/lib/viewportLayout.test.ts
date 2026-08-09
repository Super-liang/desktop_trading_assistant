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

  it("手机视口恢复纵向滚动并提供固定单行四入口导航", () => {
    expect(styles).toContain("html:not(.ticker-surface)");
    expect(styles).toMatch(/html:not\(\.ticker-surface\)[^{]*\{[^}]*overflow-x:hidden[^}]*overflow-y:auto/);
    expect(styles).toMatch(/\.mobile-nav\{position:fixed[^}]*display:grid[^}]*grid-template-columns:repeat\(4,minmax\(0,1fr\)\)/);
    expect(styles).toContain("--mobile-nav-height:64px");
    expect(styles).not.toContain("116px");
  });

  it("手机持仓与管理数据降级为卡片且对话框保持在可视高度内", () => {
    expect(styles).toContain('grid-template-areas:"security security" "price profit"');
    expect(styles).toMatch(/\.dialog\{width:calc\(100vw - 24px\)[^}]*max-height:calc\(100dvh - 24px\)/);
    expect(styles).toContain(".two-fields{grid-template-columns:1fr}");
    expect(styles).toContain(".admin-users-table .admin-row{");
    expect(styles).toContain(".portfolio-page .list-card{flex:none");
    expect(styles).toContain(".portfolio-row-actions .icon-button{width:44px;height:44px}");
    expect(styles).toContain(".portfolio-market-tabs button.active{color:#17211e;background:var(--lime)");
    expect(styles).toContain(".dialog-backdrop{z-index:50");
  });
});
