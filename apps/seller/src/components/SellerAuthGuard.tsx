'use client';

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useSellerSession } from '@/lib/useSellerSession';
import { ShieldCheck } from 'lucide-react';

interface SellerAuthGuardProps {
  children: React.ReactNode;
}

export default function SellerAuthGuard({ children }: SellerAuthGuardProps) {
  const router = useRouter();
  const { session, isMounted, isAuthenticated } = useSellerSession();

  useEffect(() => {
    if (isMounted && !session?.token) {
      router.replace('/login');
    }
  }, [isMounted, session, router]);

  if (!isMounted) {
    return (
      <div className="h-screen w-screen bg-surface-canvas flex flex-col items-center justify-center space-y-4 font-sans">
        <div className="w-10 h-10 rounded-xl bg-action-speedBg text-white flex items-center justify-center animate-pulse">
          <ShieldCheck className="w-5 h-5" />
        </div>
        <div className="flex items-center space-x-2 text-content-secondary text-xs font-bold">
          <div className="w-2 h-2 rounded-full bg-action-speedBg animate-ping" />
          <span>Verifying Merchant Session…</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="h-screen w-screen bg-surface-canvas flex flex-col items-center justify-center space-y-4 font-sans">
        <div className="w-10 h-10 rounded-xl bg-action-speedBg text-white flex items-center justify-center">
          <ShieldCheck className="w-5 h-5" />
        </div>
        <p className="text-content-secondary text-xs font-bold">Redirecting to merchant authentication…</p>
      </div>
    );
  }

  return <>{children}</>;
}
