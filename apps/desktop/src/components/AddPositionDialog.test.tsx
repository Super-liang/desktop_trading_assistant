// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AddPositionDialog } from "./AddPositionDialog";
import { api } from "../lib/api";

vi.mock("../lib/api", () => ({
  api: { search: vi.fn(), addItem: vi.fn() },
}));

const result = {
  instrumentId: "SSE:600519", code: "600519", name: "贵州茅台",
  exchange: "SSE", assetType: "STOCK",
};

describe("AddPositionDialog", () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.mocked(api.search).mockReset().mockResolvedValue([result]);
    vi.mocked(api.addItem).mockReset().mockResolvedValue({});
  });

  it("空查询不请求，数量直接输入 1 且成本可输入小数", async () => {
    const onAdded = vi.fn();
    render(<AddPositionDialog onClose={vi.fn()} onAdded={onAdded} />);
    expect(api.search).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("输入代码或名称，例如 600519"), {
      target: { value: "600519" },
    });
    await waitFor(() => expect(screen.getByText("贵州茅台")).toBeInTheDocument());
    fireEvent.click(screen.getByText("贵州茅台"));
    fireEvent.change(screen.getByLabelText("持仓数量"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("单位成本"), { target: { value: "0.25" } });

    expect(screen.getByLabelText("持仓数量")).toHaveValue("1");
    expect(screen.getByLabelText("单位成本")).toHaveValue("0.25");
    fireEvent.click(screen.getByRole("button", { name: "加入盯盘" }));

    await waitFor(() => expect(api.addItem).toHaveBeenCalledWith({
      instrumentId: "SSE:600519",
      displayName: "贵州茅台",
      quantity: 1,
      costPrice: 0.25,
      sortOrder: 0,
    }));
    expect(onAdded).toHaveBeenCalledOnce();
  });

  it("允许数量 0、成本留空的纯自选", async () => {
    render(<AddPositionDialog onClose={vi.fn()} onAdded={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText("输入代码或名称，例如 600519"), {
      target: { value: "茅台" },
    });
    await waitFor(() => expect(screen.getByText("贵州茅台")).toBeInTheDocument());
    fireEvent.click(screen.getByText("贵州茅台"));
    fireEvent.click(screen.getByRole("button", { name: "加入盯盘" }));

    await waitFor(() => expect(api.addItem).toHaveBeenCalledWith(expect.objectContaining({
      quantity: 0, costPrice: null,
    })));
  });

  it("搜索失败结束加载并展示错误", async () => {
    vi.mocked(api.search).mockRejectedValueOnce(new Error("证券目录暂不可用"));
    render(<AddPositionDialog onClose={vi.fn()} onAdded={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText("输入代码或名称，例如 600519"), {
      target: { value: "600519" },
    });

    expect(await screen.findByText("证券目录暂不可用")).toBeInTheDocument();
    expect(screen.queryByText("正在查询证券目录…")).not.toBeInTheDocument();
  });

  it("快速搜索只展示最后一次结果", async () => {
    let resolveOld: ((value: (typeof result)[]) => void) | undefined;
    vi.mocked(api.search)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }))
      .mockResolvedValueOnce([{ ...result, instrumentId: "SZSE:000001", code: "000001", name: "平安银行" }]);
    render(<AddPositionDialog onClose={vi.fn()} onAdded={vi.fn()} />);
    const input = screen.getByPlaceholderText("输入代码或名称，例如 600519");
    fireEvent.change(input, { target: { value: "600" } });
    await waitFor(() => expect(api.search).toHaveBeenCalledTimes(1));
    fireEvent.change(input, { target: { value: "000001" } });
    expect(await screen.findByText("平安银行")).toBeInTheDocument();
    resolveOld?.([result]);

    await waitFor(() => expect(screen.queryByText("贵州茅台")).not.toBeInTheDocument());
  });

  it("保存期间禁止重复提交", async () => {
    vi.mocked(api.addItem).mockImplementation(() => new Promise(() => undefined));
    render(<AddPositionDialog onClose={vi.fn()} onAdded={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText("输入代码或名称，例如 600519"), {
      target: { value: "600519" },
    });
    await waitFor(() => expect(screen.getByText("贵州茅台")).toBeInTheDocument());
    fireEvent.click(screen.getByText("贵州茅台"));
    fireEvent.click(screen.getByRole("button", { name: "加入盯盘" }));

    const saving = await screen.findByRole("button", { name: "正在保存…" });
    fireEvent.click(saving);
    expect(api.addItem).toHaveBeenCalledTimes(1);
    expect(saving).toBeDisabled();
  });

  it("保存超时后提示先核对列表并恢复按钮", async () => {
    vi.mocked(api.addItem).mockRejectedValueOnce(new Error("请求超时，请检查网络后重试"));
    render(<AddPositionDialog onClose={vi.fn()} onAdded={vi.fn()} />);
    fireEvent.change(screen.getByPlaceholderText("输入代码或名称，例如 600519"), {
      target: { value: "600519" },
    });
    await waitFor(() => expect(screen.getByText("贵州茅台")).toBeInTheDocument());
    fireEvent.click(screen.getByText("贵州茅台"));
    fireEvent.click(screen.getByRole("button", { name: "加入盯盘" }));

    expect(await screen.findByText(/请先核对自选列表/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "加入盯盘" })).toBeEnabled();
  });
});
