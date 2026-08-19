'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { useSession } from './session-store';

export interface CartLineItem {
  sku: string;
  productId?: string;
  name?: string;
  brand?: string;
  packSize?: string;
  unitPrice?: number;
  mrp?: number;
  quantity: number;
  rxRequired?: boolean;
  coldChain?: boolean;
  image?: string;
  expressDeliverySlaMins?: number;
}

export interface AuthoritativeCartResponse {
  customerId: string;
  items: CartLineItem[];
  itemCount: number;
  subtotal: number;
  mrpTotal: number;
  deliveryFee: number;
  coldChainFee: number;
  totalSavings: number;
  total: number;
  hasColdChain: boolean;
  hasPrescriptionItems: boolean;
  freeDeliveryThreshold: number;
  amountNeededForFreeDelivery: number;
}

interface CartState {
  lines: CartLineItem[];
  serverTotals: AuthoritativeCartResponse | null;
  isLoading: boolean;
  addItem: (item: { sku: string; name?: string; image?: string }, quantity?: number) => Promise<void>;
  removeItem: (sku: string) => Promise<void>;
  updateQuantity: (sku: string, quantity: number) => Promise<void>;
  refreshFromServer: () => Promise<AuthoritativeCartResponse | null>;
  clearCart: () => void;
}

const isProduction = process.env.NODE_ENV === 'production';
export const API_GATEWAY_URL = (
  process.env.NEXT_PUBLIC_API_GATEWAY_URL ||
  process.env.NEXT_PUBLIC_CART_API_URL ||
  ''
).replace(/\/$/, '') || (isProduction ? '' : 'http://localhost:8083');

function buildAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
  try {
    const session = useSession.getState();
    const token = session.user?.accessToken;
    if (token) headers['Authorization'] = `Bearer ${token}`;
  } catch (_) {}
  return headers;
}

function getCustomerId(): string | null {
  try {
    const session = useSession.getState();
    return session.user?.userId || null;
  } catch (_) {
    return null;
  }
}

export const useCart = create<CartState>()(
  persist(
    (set, get) => ({
      lines: [],
      serverTotals: null,
      isLoading: false,

      /**
       * Authoritative Cart Item Addition.
       * Transmits strictly { sku, quantity } to the backend server.
       * Local cart state is refreshed directly with the authoritative server response.
       */
      addItem: async (item, quantity = 1) => {
        const customerId = getCustomerId();
        const existing = get().lines.find((l) => l.sku === item.sku);
        const optimisticLines = existing
          ? get().lines.map((l) => (l.sku === item.sku ? { ...l, quantity: l.quantity + quantity } : l))
          : [...get().lines, { sku: item.sku, name: item.name, image: item.image, quantity }];
        set({ lines: optimisticLines });

        if (!API_GATEWAY_URL || !customerId) return;

        try {
          set({ isLoading: true });
          const res = await fetch(`${API_GATEWAY_URL}/api/v1/cart/${customerId}/items`, {
            method: 'POST',
            headers: buildAuthHeaders(),
            body: JSON.stringify({
              sku: item.sku,
              quantity: quantity,
            }),
          });
          if (res.ok) {
            const data: AuthoritativeCartResponse = await res.json();
            set({
              lines: data.items || [],
              serverTotals: data,
            });
          }
        } catch (err) {
          console.error('[CartStore] Failed to sync item addition with authoritative backend:', err);
        } finally {
          set({ isLoading: false });
        }
      },

      removeItem: async (sku) => {
        const customerId = getCustomerId();
        const optimisticLines = get().lines.filter((l) => l.sku !== sku);
        set({ lines: optimisticLines });

        if (!API_GATEWAY_URL || !customerId) return;

        try {
          const res = await fetch(`${API_GATEWAY_URL}/api/v1/cart/${customerId}/items/${encodeURIComponent(sku)}`, {
            method: 'DELETE',
            headers: buildAuthHeaders(),
          });
          if (res.ok) {
            const data: AuthoritativeCartResponse = await res.json();
            set({
              lines: data.items || [],
              serverTotals: data,
            });
          }
        } catch (err) {
          console.error('[CartStore] Failed to remove item on backend:', err);
        }
      },

      updateQuantity: async (sku, quantity) => {
        if (quantity <= 0) {
          await get().removeItem(sku);
          return;
        }

        const customerId = getCustomerId();
        const optimisticLines = get().lines.map((l) => (l.sku === sku ? { ...l, quantity } : l));
        set({ lines: optimisticLines });

        if (!API_GATEWAY_URL || !customerId) return;

        try {
          const res = await fetch(`${API_GATEWAY_URL}/api/v1/cart/${customerId}/items/${encodeURIComponent(sku)}`, {
            method: 'PATCH',
            headers: buildAuthHeaders(),
            body: JSON.stringify({ quantity }),
          });
          if (res.ok) {
            const data: AuthoritativeCartResponse = await res.json();
            set({
              lines: data.items || [],
              serverTotals: data,
            });
          }
        } catch (err) {
          console.error('[CartStore] Failed to update item quantity on backend:', err);
        }
      },

      refreshFromServer: async () => {
        const customerId = getCustomerId();
        if (!API_GATEWAY_URL || !customerId) return null;

        try {
          set({ isLoading: true });
          const res = await fetch(`${API_GATEWAY_URL}/api/v1/cart/${customerId}`, {
            method: 'GET',
            headers: buildAuthHeaders(),
          });
          if (res.ok) {
            const data: AuthoritativeCartResponse = await res.json();
            set({
              lines: data.items || [],
              serverTotals: data,
            });
            return data;
          }
        } catch (err) {
          console.error('[CartStore] Failed to fetch authoritative cart from backend:', err);
        } finally {
          set({ isLoading: false });
        }
        return null;
      },

      clearCart: () => set({ lines: [], serverTotals: null }),
    }),
    {
      name: 'commerceos-cart-v2',
      partialize: (state) => ({
        lines: state.lines.map((l) => ({ sku: l.sku, quantity: l.quantity })),
      }),
    }
  )
);
