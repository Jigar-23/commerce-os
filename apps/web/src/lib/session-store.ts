'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface SessionUser {
  userId: string;
  email: string;
  fullName: string;
  roles: string[];
  accessToken?: string;
}

interface SessionState {
  user: SessionUser | null;
  setUser: (user: SessionUser | null) => void;
  logout: () => void;
}

export const useSession = create<SessionState>()(
  persist(
    (set) => ({
      user: null,
      setUser: (user) => set({ user }),
      logout: () => set({ user: null }),
    }),
    {
      name: 'commerceos-session',
      // Security: Partialize persistence to exclude accessToken from localStorage
      partialize: (state) => ({
        user: state.user
          ? {
              userId: state.user.userId,
              email: state.user.email,
              fullName: state.user.fullName,
              roles: state.user.roles,
            }
          : null,
      }),
    }
  )
);