'use client';

import { useEffect, useState } from 'react';
import { sellerApi, SellerSession } from './apiClient';

export function useSellerSession() {
  const [session, setSession] = useState<SellerSession | null>(null);
  const [isMounted, setIsMounted] = useState(false);

  useEffect(() => {
    setIsMounted(true);
    setSession(sellerApi.getSession());

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
    storeName: isMounted && session?.storeName ? session.storeName : 'Rewari Central Hub'
  };
}
