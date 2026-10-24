import type { Metadata } from 'next';
import React from 'react';
import './globals.css';

export const metadata: Metadata = {
  title: 'Commerce OS — Pharmacist & Compliance Console',
  description: 'Licensed Pharmacist Inspection & Prescription Verification Console',
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
