'use client';

import React, { useState } from 'react';
import { Lock, Mail, ShieldCheck, Key, ArrowRight, Pill, Loader2, User, AlertCircle } from 'lucide-react';
import Link from 'next/link';
import { login, register } from '@/lib/api-client';
import { useSession } from '@/lib/session-store';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const setUser = useSession((s) => s.setUser);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!email.trim() || !password.trim()) {
      setError('Please enter your email and password.');
      return;
    }
    setIsSubmitting(true);
    try {
      const resp = await login({ email, password });
      if (resp && (resp.userId || resp.accessToken || resp.id)) {
        setUser({
          userId: resp.userId || resp.id,
          email: resp.email || email,
          fullName: resp.fullName || resp.email?.split('@')[0] || 'Customer',
          roles: resp.roles || ['ROLE_CUSTOMER'],
          accessToken: resp.accessToken,
        });
        window.location.href = '/';
      } else {
        throw new Error(resp?.message || resp?.error || 'Authentication failed.');
      }
    } catch (err: any) {
      setError(err?.message || 'Invalid credentials or authentication service unavailable. Please retry.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!email.trim() || !password.trim() || !fullName.trim()) {
      setError('Please fill in your full name, email, and password.');
      return;
    }
    setIsSubmitting(true);
    try {
      const resp = await register({ email, password, fullName });
      if (resp && (resp.userId || resp.id || resp.accessToken)) {
        setUser({
          userId: resp.userId || resp.id,
          email: resp.email || email,
          fullName: resp.fullName || fullName,
          roles: resp.roles || ['ROLE_CUSTOMER'],
          accessToken: resp.accessToken,
        });
        window.location.href = '/';
      } else {
        throw new Error(resp?.message || resp?.error || 'Registration failed.');
      }
    } catch (err: any) {
      setError(err?.message || 'Registration failed. Email may already be in use or identity service is unreachable.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface-canvas p-4 font-sans antialiased selection:bg-surface-brandSubtle selection:text-content-brand">
      <div className="w-full max-w-md rounded-3xl border border-border-default bg-surface-card p-8 shadow-card space-y-6">
        <div className="text-center space-y-2">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-action-primaryBg text-action-primaryText shadow-md">
            <Pill className="h-6 w-6" />
          </div>
          <h1 className="text-2xl font-black text-content-primary tracking-tight">
            {mode === 'login' ? 'Sign In to Commerce OS' : 'Create Customer Account'}
          </h1>
          <p className="text-xs text-content-secondary">
            Zero-Trust Healthcare &amp; Quick Commerce Authentication
          </p>
        </div>

        {/* MODE SWITCHER */}
        <div className="flex rounded-2xl border border-border-default bg-surface-subtle p-1">
          <button
            type="button"
            onClick={() => {
              setMode('login');
              setError('');
            }}
            className={`flex-1 rounded-xl py-2 text-xs font-bold transition-all cursor-pointer ${
              mode === 'login'
                ? 'bg-action-primaryBg text-action-primaryText shadow-subtle'
                : 'text-content-secondary hover:text-content-primary'
            }`}
          >
            Login
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('register');
              setError('');
            }}
            className={`flex-1 rounded-xl py-2 text-xs font-bold transition-all cursor-pointer ${
              mode === 'register'
                ? 'bg-action-primaryBg text-action-primaryText shadow-subtle'
                : 'text-content-secondary hover:text-content-primary'
            }`}
          >
            Register
          </button>
        </div>

        {error && (
          <div className="rounded-2xl bg-surface-dangerSubtle border border-border-danger p-3.5 text-xs font-bold text-content-danger flex items-start gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={mode === 'login' ? handleLogin : handleRegister} className="space-y-4">
          {mode === 'register' && (
            <div>
              <label className="text-xs font-extrabold text-content-muted uppercase tracking-wider block mb-1">
                Full Name
              </label>
              <div className="relative">
                <User className="absolute left-3.5 top-3 h-4 w-4 text-content-muted" />
                <input
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="e.g. Rahul Sharma"
                  required
                  className="w-full rounded-xl border border-border-default bg-surface-subtle py-2.5 pl-10 pr-4 text-xs font-bold text-content-primary placeholder:text-content-muted outline-none focus:border-border-brand transition-colors"
                />
              </div>
            </div>
          )}

          <div>
            <label className="text-xs font-extrabold text-content-muted uppercase tracking-wider block mb-1">
              Email Address
            </label>
            <div className="relative">
              <Mail className="absolute left-3.5 top-3 h-4 w-4 text-content-muted" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="customer@example.com"
                required
                className="w-full rounded-xl border border-border-default bg-surface-subtle py-2.5 pl-10 pr-4 text-xs font-bold text-content-primary placeholder:text-content-muted outline-none focus:border-border-brand transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-extrabold text-content-muted uppercase tracking-wider block mb-1">
              Password
            </label>
            <div className="relative">
              <Lock className="absolute left-3.5 top-3 h-4 w-4 text-content-muted" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="w-full rounded-xl border border-border-default bg-surface-subtle py-2.5 pl-10 pr-4 text-xs font-bold text-content-primary placeholder:text-content-muted outline-none focus:border-border-brand transition-colors"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-3 px-4 rounded-xl bg-action-primaryBg hover:bg-action-primaryHover active:bg-action-primaryBg text-action-primaryText font-extrabold text-xs shadow-subtle transition-all active:scale-98 disabled:opacity-50 flex items-center justify-center gap-2 cursor-pointer"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>Verifying Identity...</span>
              </>
            ) : (
              <>
                <span>{mode === 'login' ? 'Authenticate & Enter' : 'Create Account'}</span>
                <ArrowRight className="h-4 w-4" />
              </>
            )}
          </button>
        </form>

        <div className="text-center pt-2 border-t border-border-subtle">
          <Link
            href="/"
            className="text-xs font-bold text-content-secondary hover:text-content-primary transition-colors"
          >
            ← Back to Storefront
          </Link>
        </div>
      </div>
    </div>
  );
}