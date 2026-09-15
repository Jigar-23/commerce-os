'use client';

import { useEffect, useState } from 'react';
import { sellerApi, SellerSession } from './apiClient';

export function useSellerSession() {
  const [session, setSession] = useState<SellerSession | null>(null);
  const [isMounted, setIsMounted] = useState(false);
  const [isResolving, setIsResolving] = useState(true);

  useEffect(() => {
    setIsMounted(true);
    let active = true;

    async function init() {
      try {
        const s = await sellerApi.ensureSession();
        if (active && s) {
          setSession(s);
        }
      } catch (e) {
        console.error('[useSellerSession] Failed to ensure session:', e);
      } finally {
        if (active) setIsResolving(false);
      }
    }

    init();

    const interval = setInterval(() => {
      const current = sellerApi.getSession();
      setSession((prev) => {
        if (!prev && current) return current;
        if (prev && !current) return null;
        if (prev && current && prev.token !== current.token) return current;
        return prev;
      });
    }, 1000);

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, []);

  return {
    session,
    isMounted,
    isResolving,
    isAuthenticated: !!(session && session.token),
    storeName: isMounted && session?.storeName ? session.storeName : 'Rewari Central Hub'
  };
}
