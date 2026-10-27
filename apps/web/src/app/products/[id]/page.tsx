'use client';

import React from 'react';
import MedicineDetailPage from '@/app/medicines/[id]/page';

export default function ProductDetailPage({ params }: { params: { id: string } }) {
  return <MedicineDetailPage params={params} />;
}
