// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PortfolioPage } from "./PortfolioPage";

describe("PortfolioPage", () => {
  it("四个市场菜单即使没有持仓也始终可见", () => {
    const onMarketChange = vi.fn();
    render(<PortfolioPage loading={false} data={{
      items: [], totalMarketValue: 0, totalProfit: 0, unavailableQuoteCount: 0,
      calculationNotice: "参考",
    }} onAdd={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()}
      market="A_SHARE" onMarketChange={onMarketChange} />);

    for (const label of ["A股持仓", "港股持仓", "美股持仓", "公募基金持仓"]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
    fireEvent.click(screen.getByRole("button", { name: "美股持仓" }));
    expect(onMarketChange).toHaveBeenCalledWith("US_STOCK");
  });
});
