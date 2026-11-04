'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  FileText,
  UploadCloud,
  CheckCircle2,
  Clock,
  AlertCircle,
  Camera,
  Plus,
  ArrowLeft,
  X,
  Eye,
  ShieldCheck,
  ShoppingBag,
  Share2,
  Trash2,
} from 'lucide-react';
import UniversalHeader from '@/components/navigation/UniversalHeader';
import MobileBottomNav from '@/components/navigation/MobileBottomNav';

interface PrescriptionSlip {
  id: string;
  doctorName: string;
  patientName: string;
  uploadedAt: string;
  status: 'VERIFIED' | 'UNDER_REVIEW' | 'EXPIRED';
  pharmacistNotes?: string;
  imageUrl?: string;
  previewName: string;
}

const INITIAL_PRESCRIPTIONS: PrescriptionSlip[] = [
  {
    id: 'rx_001',
    doctorName: 'Dr. Suresh Verma, MD (Internal Medicine)',
    patientName: 'Self (Patient)',
    uploadedAt: 'Today, 11:20 AM',
    status: 'VERIFIED',
    pharmacistNotes: 'Verified for Dolo 650, Pan 40 & Multivitamins. Valid for 6 months.',
    previewName: 'Dr_Verma_Rx_Nov2026.pdf',
  },
  {
    id: 'rx_002',
    doctorName: 'Dr. Neha Kapoor, Cardiologist',
    patientName: 'Self (Patient)',
    uploadedAt: '12 Oct 2026',
    status: 'VERIFIED',
    pharmacistNotes: 'Chronic prescription for Telmisartan 40mg & Glycomet GP1.',
    previewName: 'Cardiac_Consultation_Oct2026.jpg',
  },
  {
    id: 'rx_003',
    doctorName: 'Dr. R. K. Gupta, ENT Specialist',
    patientName: 'Family Member',
    uploadedAt: '28 Sep 2026',
    status: 'UNDER_REVIEW',
    pharmacistNotes: 'Awaiting pharmacist dosage verification for antibiotic course.',
    previewName: 'ENT_Clinic_Slip.jpg',
  },
];

export default function PrescriptionVaultPage() {
  const router = useRouter();
  const [prescriptions, setPrescriptions] = useState<PrescriptionSlip[]>(INITIAL_PRESCRIPTIONS);
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [previewItem, setPreviewItem] = useState<PrescriptionSlip | null>(null);

  // New prescription form state
  const [doctorName, setDoctorName] = useState('');
  const [patientName, setPatientName] = useState('Self');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [filePreview, setFilePreview] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      if (file.type.startsWith('image/')) {
        const reader = new FileReader();
        reader.onload = () => setFilePreview(reader.result as string);
        reader.readAsDataURL(file);
      } else {
        setFilePreview(null);
      }
    }
  };

  const handleUploadSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile && !doctorName) return;
    setIsUploading(true);

    setTimeout(() => {
      const newRx: PrescriptionSlip = {
        id: `rx_${Date.now()}`,
        doctorName: doctorName || 'Registered Medical Practitioner',
        patientName: patientName || 'Self',
        uploadedAt: 'Just now',
        status: 'UNDER_REVIEW',
        pharmacistNotes: 'Received by Rewari central dark store. Pharmacist review in progress.',
        imageUrl: filePreview || undefined,
        previewName: selectedFile?.name || 'Prescription_Upload.jpg',
      };
      setPrescriptions([newRx, ...prescriptions]);
      setIsUploading(false);
      setIsUploadModalOpen(false);
      setDoctorName('');
      setSelectedFile(null);
      setFilePreview(null);
    }, 600);
  };

  return (
    <div className="min-h-screen bg-[#F4F6F8] text-slate-900 font-sans antialiased pb-28">
      <UniversalHeader locationLabel="Rewari Hub" etaMinutes={10} />

      <main className="max-w-4xl mx-auto px-4 sm:px-6 pt-5 flex flex-col gap-4">
        {/* Header Row */}
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <Link href="/account" className="text-slate-400 hover:text-slate-600 transition-colors">
                <ArrowLeft className="w-5 h-5" />
              </Link>
              <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                Prescriptions Vault
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-500 font-medium ml-7">
              Manage doctor slips verified by registered pharmacists
            </p>
          </div>

          <button
            onClick={() => setIsUploadModalOpen(true)}
            className="px-4 py-2 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
          >
            <Plus className="w-4 h-4" />
            <span>Upload New Rx</span>
          </button>
        </div>

        {/* Regulatory Banner */}
        <div className="bg-emerald-50 border border-emerald-200/80 rounded-2xl p-4 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-100 text-[#0E675E] flex items-center justify-center shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <h4 className="text-xs font-extrabold text-[#0E675E]">
              100% Legal & Government Compliant Dispensing
            </h4>
            <p className="text-[11px] text-slate-600 mt-0.5">
              Prescriptions are reviewed exclusively by registered B.Pharm pharmacists before order packing. Your medical records remain encrypted and confidential.
            </p>
          </div>
        </div>

        {/* Prescription List */}
        <div className="space-y-3">
          {prescriptions.map((rx) => {
            const isVerified = rx.status === 'VERIFIED';
            return (
              <div
                key={rx.id}
                className="bg-white rounded-2xl border border-slate-200/80 p-4 shadow-2xs hover:border-slate-300 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-4"
              >
                <div className="flex items-start gap-3.5">
                  <div className="w-12 h-12 rounded-xl bg-slate-100 border border-slate-200 text-[#0E675E] flex items-center justify-center shrink-0">
                    <FileText className="w-6 h-6" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-black text-slate-900 leading-tight">
                        {rx.doctorName}
                      </h3>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-black uppercase ${
                          isVerified
                            ? 'bg-emerald-100 text-[#0E675E]'
                            : 'bg-amber-100 text-amber-800'
                        }`}
                      >
                        {isVerified ? 'Verified' : 'Under Review'}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5">
                      Patient: <span className="font-semibold text-slate-700">{rx.patientName}</span> • Uploaded: {rx.uploadedAt}
                    </p>
                    {rx.pharmacistNotes && (
                      <p className="text-[11px] text-slate-600 bg-slate-50 border border-slate-100 rounded-lg px-2.5 py-1 mt-2">
                        💬 <span className="font-medium">{rx.pharmacistNotes}</span>
                      </p>
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 self-end sm:self-center">
                  <button
                    onClick={() => setPreviewItem(rx)}
                    className="px-3 py-1.5 rounded-xl border border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-700 text-xs font-bold flex items-center gap-1 transition-colors"
                  >
                    <Eye className="w-3.5 h-3.5" />
                    <span>View Slip</span>
                  </button>
                  <Link
                    href="/"
                    className="px-3 py-1.5 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-bold flex items-center gap-1 transition-colors shadow-2xs"
                  >
                    <ShoppingBag className="w-3.5 h-3.5" />
                    <span>Order Medicines</span>
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      </main>

      {/* Upload Prescription Dialog */}
      {isUploadModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl relative animate-in fade-in zoom-in-95">
            <button
              onClick={() => setIsUploadModalOpen(false)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-lg font-black text-slate-900 mb-1">Upload Doctor Prescription</h3>
            <p className="text-xs text-slate-500 mb-4">
              Our certified pharmacist will review and dispense your prescribed medications.
            </p>

            <form onSubmit={handleUploadSubmit} className="space-y-3.5">
              {/* File Dropzone */}
              <label className="border-2 border-dashed border-slate-300 hover:border-[#0E675E] bg-slate-50 hover:bg-emerald-50/30 rounded-2xl p-6 flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors block text-center">
                <input type="file" accept="image/*,.pdf" onChange={handleFileChange} className="hidden" />
                <div className="w-12 h-12 rounded-full bg-emerald-100 text-[#0E675E] flex items-center justify-center">
                  <UploadCloud className="w-6 h-6" />
                </div>
                <span className="text-xs font-bold text-slate-800">
                  {selectedFile ? selectedFile.name : 'Tap to Upload Prescription Image or PDF'}
                </span>
                <span className="text-[10px] text-slate-400">Supports JPG, PNG, PDF up to 10MB</span>
              </label>

              {filePreview && (
                <div className="w-full h-32 rounded-xl overflow-hidden border border-slate-200">
                  <img src={filePreview} alt="Preview" className="w-full h-full object-cover" />
                </div>
              )}

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Doctor / Clinic Name</label>
                <input
                  type="text"
                  value={doctorName}
                  onChange={(e) => setDoctorName(e.target.value)}
                  placeholder="e.g. Dr. Rajesh Sharma, Civil Hospital Rewari"
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#0E675E]"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Patient Name</label>
                <input
                  type="text"
                  value={patientName}
                  onChange={(e) => setPatientName(e.target.value)}
                  placeholder="e.g. Self or Family Member"
                  className="w-full px-3.5 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-[#0E675E]"
                />
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsUploadModalOpen(false)}
                  className="flex-1 py-2 rounded-xl border border-slate-300 text-slate-700 text-xs font-bold hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isUploading || (!selectedFile && !doctorName)}
                  className="flex-1 py-2 rounded-xl bg-[#0E675E] hover:bg-[#0A4F48] text-white text-xs font-bold disabled:opacity-50 transition-colors"
                >
                  {isUploading ? 'Uploading...' : 'Submit to Pharmacist'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Slip Preview Dialog */}
      {previewItem && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl relative animate-in fade-in zoom-in-95">
            <button
              onClick={() => setPreviewItem(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-slate-600"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-black text-slate-900 mb-1">{previewItem.doctorName}</h3>
            <p className="text-xs text-slate-500 mb-4">{previewItem.previewName} • Uploaded {previewItem.uploadedAt}</p>

            <div className="w-full h-64 bg-slate-100 rounded-xl border border-slate-200 flex flex-col items-center justify-center p-4 text-center">
              {previewItem.imageUrl ? (
                <img src={previewItem.imageUrl} alt="Prescription" className="w-full h-full object-contain" />
              ) : (
                <>
                  <FileText className="w-16 h-16 text-[#0E675E] mb-2" />
                  <p className="text-xs font-bold text-slate-800">{previewItem.previewName}</p>
                  <p className="text-[11px] text-slate-500 mt-1">Verified Medical Record on Encrypted Vault</p>
                </>
              )}
            </div>

            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between">
              <span className="text-xs font-bold text-emerald-700">Status: {previewItem.status}</span>
              <button
                onClick={() => setPreviewItem(null)}
                className="px-4 py-2 rounded-xl bg-slate-900 text-white text-xs font-bold"
              >
                Close Preview
              </button>
            </div>
          </div>
        </div>
      )}

      <MobileBottomNav />
    </div>
  );
}
