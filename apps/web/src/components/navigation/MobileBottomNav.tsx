'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, LayoutGrid, ShoppingBag, User } from 'lucide-react';
import { useCart } from '@/lib/cart-store';

/**
 * Mobile Bottom Navigation Bar benchmarked directly against the CommerceOS Android App.
 * Matches canonical Android destinations verbatim: Home, Categories, Orders, Account.
 */
export default function MobileBottomNav() {
  const pathname = usePathname();
  const lines = useCart((s) => s.lines);
  const cartCount = lines.reduce((sum, l) => sum + l.quantity, 0);

  const tabs = [
    { label: 'Home', href: '/', icon: Home },
    { label: 'Categories', href: '/categories', icon: LayoutGrid },
    { label: 'Orders', href: '/orders', icon: ShoppingBag },
    { label: 'Account', href: '/account', icon: User },
  ];

  return (
    <nav className="sm:hidden fixed bottom-0 inset-x-0 z-40 bg-white/95 backdrop-blur-md border-t border-slate-200/80 shadow-[0_-4px_16px_rgba(0,0,0,0.04)] px-3 py-1.5">
      <div className="grid grid-cols-4 items-center">
        {tabs.map((tab) => {
          const isActive = pathname === tab.href || (tab.href === '/account' && pathname === '/profile');
          const Icon = tab.icon;

          return (
            <Link
              key={tab.label}
              href={tab.href}
              className={`flex flex-col items-center justify-center py-1 transition-colors relative ${
                isActive
                  ? 'text-[#005844] font-bold'
                  : 'text-slate-500 hover:text-slate-800 font-medium'
              }`}
            >
              <div className="relative">
                <Icon
                  className={`w-5 h-5 transition-transform ${
                    isActive ? 'scale-110 stroke-[2.5]' : 'stroke-[1.8]'
                  }`}
                />
              </div>
              <span className="text-[10.5px] mt-1 tracking-tight">
                {tab.label}
              </span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
