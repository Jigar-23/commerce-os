'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { sellerApi } from '@/lib/apiClient';
import { ShieldCheck, Lock, Mail, Store, AlertCircle } from 'lucide-react';

export default function SellerLoginPage() {
  const router = useRouter();
  const [identifier, setIdentifier] = useState('seller_rewari_01');
  const [password, setPassword] = useState('rewari_hub_sec_881');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!identifier.trim() || !password.trim()) {
      setErrorMessage('Merchant identifier and password are required.');
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const res = await sellerApi.login(identifier.trim(), password.trim());
      if (res.ok && res.session) {
        router.push('/');
      } else {
        setErrorMessage(res.error || 'Authentication failed. Please verify your merchant credentials.');
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'An unexpected error occurred during merchant login.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-inverse flex items-center justify-center p-4 font-sans">
      <div className="max-w-md w-full bg-white rounded-3xl p-8 shadow-2xl space-y-6">
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-2xl bg-action-speedBg text-white font-black text-xl flex items-center justify-center mx-auto shadow-lg shadow-subtle">
            S
          </div>
          <h1 className="text-2xl font-black text-content-primary tracking-tight">Merchant Portal</h1>
          <p className="text-xs text-content-secondary font-medium">Authenticate to manage your store inventory, orders, and settlements</p>
        </div>

        {errorMessage && (
          <div className="p-4 bg-surface-dangerSubtle border border-border-danger rounded-2xl flex items-start space-x-3 text-content-danger text-xs font-semibold">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-bold text-content-secondary flex items-center space-x-1.5">
              <Store className="w-3.5 h-3.5 text-content-muted" />
              <span>Merchant Identifier / Email</span>
            </label>
            <input
              type="text"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="e.g. seller_rewari_01 or merchant@commerceos.io"
              className="w-full px-4 py-3 bg-surface-subtle border border-border-default rounded-xl text-sm font-semibold text-content-primary outline-none focus:border-border-accent transition"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-bold text-content-secondary flex items-center space-x-1.5">
              <Lock className="w-3.5 h-3.5 text-content-muted" />
              <span>Password / Security PIN</span>
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your security credential"
              className="w-full px-4 py-3 bg-surface-subtle border border-border-default rounded-xl text-sm font-semibold text-content-primary outline-none focus:border-border-accent transition"
              required
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-3.5 bg-action-speedBg hover:bg-action-speedHover text-white text-sm font-bold rounded-xl shadow-lg shadow-subtle transition-all disabled:opacity-50 flex items-center justify-center space-x-2 mt-2"
          >
            {isLoading ? (
              <span>Authenticating…</span>
            ) : (
              <>
                <ShieldCheck className="w-4 h-4" />
                <span>Sign In to Merchant Hub</span>
              </>
            )}
          </button>
        </form>

        <div className="pt-4 border-t border-border-subtle text-center">
          <p className="text-2xs text-content-muted">
            Protected by Commerce OS Enterprise Merchant Access Control
          </p>
        </div>
      </div>
    </div>
  );
}
