'use client';

import { useEffect, useState } from 'react';
import { sellerApi, SellerSession } from './apiClient';

export function useSellerSession() {
  const [session, setSession] = useState<SellerSession | null>(null);
  const [isMounted, setIsMounted] = useState(false);

  useEffect(() => {
    setIsMounted(true);
    const existing = sellerApi.getSession();
    if (existing) {
      setSession(existing);
    } else {
      sellerApi.ensureSession().then((s) => {
        if (s) setSession(s);
      });
    }

    const interval = setInterval(() => {
      const current = sellerApi.getSession();
      setSession((prev) => {
        if (!prev && current) return current;
        if (prev && !current) return null;
        if (prev && current && prev.token !== current.token) return current;
        return prev;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  return {
    session,
    isMounted,
    isAuthenticated: !!(session && session.token),
    storeName: isMounted && session?.storeName ? session.storeName : 'Rewari Central Hub'
  };
}
