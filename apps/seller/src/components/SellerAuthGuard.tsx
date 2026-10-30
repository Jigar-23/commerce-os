'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useSellerSession } from '@/lib/useSellerSession';
import { ShieldCheck, RefreshCw, ArrowRight } from 'lucide-react';
import Link from 'next/link';

interface SellerAuthGuardProps {
  children: React.ReactNode;
}

export default function SellerAuthGuard({ children }: SellerAuthGuardProps) {
  const router = useRouter();
  const { isMounted, isResolving, isAuthenticated } = useSellerSession();
  const [showTimeoutFallback, setShowTimeoutFallback] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setShowTimeoutFallback(true);
    }, 3500);

    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (isMounted && !isResolving && !isAuthenticated) {
      router.replace('/login');
    }
  }, [isMounted, isResolving, isAuthenticated, router]);

  if (!isMounted || isResolving) {
    return (
      <div className="h-screen w-screen bg-surface-canvas flex flex-col items-center justify-center space-y-4 font-sans px-4">
        <div className="w-12 h-12 rounded-2xl bg-action-speedBg text-white flex items-center justify-center animate-pulse shadow-lg shadow-action-speedBg/20">
          <ShieldCheck className="w-6 h-6" />
        </div>
        <div className="flex items-center space-x-2 text-content-secondary text-xs font-bold">
          <div className="w-2 h-2 rounded-full bg-action-speedBg animate-ping" />
          <span>Verifying Merchant Session…</span>
        </div>

        {showTimeoutFallback && (
          <div className="mt-4 flex flex-col items-center space-y-2 animate-fadeIn text-center">
            <p className="text-xs text-content-tertiary">Taking longer than expected?</p>
            <div className="flex items-center space-x-3">
              <button
                onClick={() => window.location.reload()}
                className="px-3 py-1.5 rounded-lg border border-border-subtle bg-surface-card hover:bg-surface-elevated text-xs font-semibold text-content-primary flex items-center gap-1.5 transition-all shadow-sm"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Reload</span>
              </button>
              <Link
                href="/login"
                className="px-3 py-1.5 rounded-lg bg-action-speedBg text-white text-xs font-semibold hover:bg-action-speedBg/90 flex items-center gap-1.5 transition-all shadow-sm"
              >
                <span>Go to Login</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        )}
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return <>{children}</>;
}
