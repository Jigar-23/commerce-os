'use client';

import React, { useState } from 'react';
import { Smartphone, Monitor, ShieldCheck, LogOut, Clock, Pill } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useSession } from '@/lib/session-store';

export default function SessionManagementPage() {
  const router = useRouter();
  const session = useSession((s) => s.user);
  const logout = useSession((s) => s.logout);
  const [feedback, setFeedback] = useState<string | null>(null);

  const handleLogoutCurrent = () => {
    logout();
    setFeedback('You have been signed out of this session.');
    setTimeout(() => router.push('/login'), 1500);
  };

  return (
    <div className="min-h-screen bg-surface-subtle text-content-primary">
      <header className="border-b border-border-default bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-action-speedBg text-white">
              <Pill className="h-5 w-5" />
            </div>
            <Link href="/" className="font-black text-lg">COMMERCE.OS</Link>
            <span className="text-xs font-bold text-content-muted">| Account Security &amp; Sessions</span>
          </div>
          <Link href="/profile" className="text-xs font-bold text-content-accent hover:underline">
            ← Back to Profile
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-4 py-8 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-black">Active Device Session</h1>
            <p className="text-xs text-content-secondary mt-1">Verified Identity Session &amp; Token Security</p>
          </div>
          {session && (
            <button
              onClick={handleLogoutCurrent}
              className="flex items-center gap-1.5 rounded-xl bg-action-dangerBg px-4 py-2 text-xs font-bold text-white hover:bg-action-dangerHover shadow-sm cursor-pointer"
            >
              <LogOut className="h-4 w-4" /> Sign Out
            </button>
          )}
        </div>

        {feedback && (
          <div className="p-4 rounded-2xl bg-surface-brandSubtle border border-border-brandSubtle text-xs font-bold text-content-brand">
            {feedback}
          </div>
        )}

        {session ? (
          <div className="rounded-2xl border border-border-default bg-white p-5 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="rounded-xl bg-surface-accentSubtle p-3 text-content-accent">
                  <Monitor className="h-6 w-6" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-sm">Active Web Session</h3>
                    <span className="rounded bg-surface-brandSubtle px-2 py-0.5 text-2xs font-bold text-content-brand">
                      CURRENT
                    </span>
                  </div>
                  <p className="text-xs text-content-muted font-mono mt-0.5">
                    User: {session.email || session.userId} • Roles: {session.roles?.join(', ') || 'ROLE_CUSTOMER'}
                  </p>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="rounded-2xl border border-border-default bg-white p-8 text-center space-y-3">
            <ShieldCheck className="h-8 w-8 text-content-muted mx-auto" />
            <p className="text-sm font-bold text-content-secondary">No active session found</p>
            <Link
              href="/login"
              className="inline-block px-4 py-2 bg-action-primaryBg text-action-primaryText text-xs font-bold rounded-xl"
            >
              Sign In
            </Link>
          </div>
        )}
      </main>
    </div>
  );
}
