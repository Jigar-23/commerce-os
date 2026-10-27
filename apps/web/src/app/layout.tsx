import React from 'react';
import './globals.css';
import { Metadata } from 'next';

const appName = process.env.NEXT_PUBLIC_APP_NAME || 'Commerce OS';

export const metadata: Metadata = {
  title: `${appName} — Universal Quick-Commerce Engine`,
  description: `Instant 10-minute hyperlocal delivery, dynamic inventory, and real-time order tracking powered by ${appName}.`,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-surface-subtle text-content-primary antialiased dark:bg-surface-inverse dark:text-content-inverse">
        {children}
      </body>
    </html>
  );
}
