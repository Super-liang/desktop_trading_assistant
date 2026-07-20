import { create } from "zustand";
import type { AuthResponse } from "../types";

type AuthState = {
  session: AuthResponse | null;
  setSession: (session: AuthResponse) => void;
  clear: () => void;
};

export const useAuth = create<AuthState>((set) => ({
  session: null,
  setSession: (session) => {
    set({ session });
  },
  clear: () => {
    set({ session: null });
  },
}));
