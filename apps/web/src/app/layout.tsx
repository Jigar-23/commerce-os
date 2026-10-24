import React from 'react';
import './globals.css';
import { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Commerce OS — Enterprise Digital Pharmacy & Healthcare Engine',
  description: 'Sub-10 minute quick-commerce medicine delivery, AI prescription OCR, and verified doctor consultations.',
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
