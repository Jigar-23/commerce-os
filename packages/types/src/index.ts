// --- MEDICINE & PHARMACY CATALOG TYPES ---
export type DosageForm = 
  | 'TABLET' 
  | 'CAPSULE' 
  | 'SYRUP' 
  | 'INJECTION' 
  | 'CREAM' 
  | 'DROPS' 
  | 'POWDER' 
  | 'INHALER';

export type RxRequirement = 'OTC' | 'PRESCRIPTION_REQUIRED' | 'CONTROLLED_DRUG_SCHEDULE_H';

export interface SaltComposition {
  saltName: string;
  strength: string; // e.g. "500mg"
}

export interface DrugSafetyWarning {
  category: 'PREGNANCY' | 'BREASTFEEDING' | 'ALCOHOL' | 'DRIVING' | 'KIDNEY' | 'LIVER';
  rating: 'SAFE' | 'UNSAFE' | 'CAUTION' | 'CONSULT_DOCTOR';
  description: string;
}

export interface MedicineProduct {
  id: string;
  sku: string;
  name: string;
  brandName: string;
  manufacturer: string;
  saltCompositions: SaltComposition[];
  dosageForm: DosageForm;
  packSize: string; // e.g. "Strip of 15 Tablets"
  rxRequirement: RxRequirement;
  price: number; // MRP in cents/paisa
  discountedPrice: number;
  discountPercentage: number;
  inStock: boolean;
  stockCount: number;
  coldChainRequired: boolean;
  therapeuticCategory: string;
  uses: string[];
  sideEffects: string[];
  warnings: DrugSafetyWarning[];
  images: string[];
  substitutesCount: number;
  rating: number;
  reviewCount: number;
  expressDeliverySlaMins: number; // e.g. 10 or 15 mins
}

// --- PRESCRIPTION MANAGEMENT TYPES ---
export type PrescriptionStatus = 
  | 'UPLOADED' 
  | 'OCR_PROCESSING' 
  | 'PENDING_PHARMACIST_VERIFICATION' 
  | 'VERIFIED' 
  | 'REJECTED_EXPIRED' 
  | 'REJECTED_ILLEGIBLE';

export interface Prescription {
  id: string;
  customerId: string;
  patientName: string;
  doctorName?: string;
  doctorRegistrationNo?: string;
  fileUrl: string;
  status: PrescriptionStatus;
  ocrExtractedText?: string;
  extractedMedicines?: {
    medicineName: string;
    dosage: string;
    durationDays: number;
  }[];
  verifiedByPharmacistId?: string;
  pharmacistLicenseNo?: string;
  rejectionReason?: string;
  uploadedAt: string;
  expiresAt: string;
}

// --- DRUG INTERACTION ALERTS ---
export type InteractionSeverity = 'INFO' | 'MILD' | 'MODERATE' | 'SEVERE_CONTRAINDICATION';

export interface DrugInteractionCheckResult {
  hasInteraction: boolean;
  highestSeverity: InteractionSeverity;
  interactions: {
    saltA: string;
    saltB: string;
    severity: InteractionSeverity;
    clinicalEffect: string;
    recommendation: string;
  }[];
}

// --- INVENTORY BATCH (FEFO) ---
export interface BatchInventory {
  batchId: string;
  sku: string;
  batchNumber: string;
  manufacturingDate: string;
  expiryDate: string; // Used for FEFO sorting
  availableStock: number;
  coldChainStorageTempCelsius: number;
  warehouseId: string;
  darkStoreId?: string;
}

// --- ORDER & CHECKOUT TYPES ---
export type OrderType = 'QUICK_COMMERCE_10MIN' | 'STANDARD_DELIVERY' | 'REFILL_SUBSCRIPTION';

export type OrderStatus = 
  | 'CREATED' 
  | 'PRESCRIPTION_VERIFICATION_PENDING' 
  | 'PHARMACIST_APPROVED' 
  | 'ALLOCATED_DARK_STORE' 
  | 'PACKED_FEFO' 
  | 'OUT_FOR_DELIVERY' 
  | 'DELIVERED' 
  | 'CANCELLED';

export interface CartItem {
  productId: string;
  product: MedicineProduct;
  quantity: number;
  selectedSubstituteId?: string;
  rxValidated: boolean;
}

export interface HealthProfile {
  id: string;
  customerId: string;
  patientName: string;
  age: number;
  gender: 'MALE' | 'FEMALE' | 'OTHER';
  bloodGroup: string;
  knownAllergies: string[];
  chronicConditions: string[];
  activePillRemindersCount: number;
}
