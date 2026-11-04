'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function ProfileRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace('/account');
  }, [router]);
  return (
    <div className="min-h-screen flex items-center justify-center bg-[#F4F6F8]">
      <div className="text-sm font-bold text-slate-500 animate-pulse">
        Loading Account Dashboard...
      </div>
    </div>
  );
}
