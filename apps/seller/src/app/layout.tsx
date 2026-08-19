import type { Metadata } from 'next';
import React from 'react';
import './globals.css';

export const metadata: Metadata = {
  title: 'Commerce OS — Pharmacy Partner Merchant Portal',
  description: 'Pharmacy Merchant Onboarding, Drug Licensing KYC & Bulk Inventory Import Portal',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="bg-surface-inverse text-content-inverse min-h-screen antialiased">
        {children}
      </body>
    </html>
  );
}
