// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../lib/api";
import { useAuth } from "../store/auth";
import { ChangePasswordDialog } from "./ChangePasswordDialog";

vi.mock("../lib/api", () => ({ api: { changePassword: vi.fn() } }));

describe("ChangePasswordDialog", () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.mocked(api.changePassword).mockReset().mockResolvedValue(undefined);
    useAuth.getState().setSession({
      accessToken: "access", refreshToken: "refresh", expiresAt: "2099-01-01T00:00:00Z", role: "USER",
    });
  });

  it("在客户端拒绝不一致和弱密码", () => {
    render(<ChangePasswordDialog onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("当前密码"), { target: { value: "OldPass123!" } });
    fireEvent.change(screen.getByLabelText("新密码"), { target: { value: "short" } });
    fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "different" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(screen.getByText(/两次输入的新密码不一致/)).toBeInTheDocument();
    expect(api.changePassword).not.toHaveBeenCalled();
  });

  it("服务端错误保留会话并展示反馈", async () => {
    vi.mocked(api.changePassword).mockRejectedValueOnce(new Error("当前密码错误"));
    render(<ChangePasswordDialog onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("当前密码"), { target: { value: "OldPass123!" } });
    fireEvent.change(screen.getByLabelText("新密码"), { target: { value: "NewPass123!" } });
    fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "NewPass123!" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));
    expect(await screen.findByText("当前密码错误")).toBeInTheDocument();
    expect(useAuth.getState().session).not.toBeNull();
  });

  it("成功后清除本地会话并返回登录态", async () => {
    render(<ChangePasswordDialog onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("当前密码"), { target: { value: "OldPass123!" } });
    fireEvent.change(screen.getByLabelText("新密码"), { target: { value: "NewPass123!" } });
    fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "NewPass123!" } });
    fireEvent.click(screen.getByRole("button", { name: "确认修改" }));

    await waitFor(() => expect(api.changePassword).toHaveBeenCalledWith({
      currentPassword: "OldPass123!", newPassword: "NewPass123!", confirmPassword: "NewPass123!",
    }));
    expect(useAuth.getState().session).toBeNull();
  });
});
