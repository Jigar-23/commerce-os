# Commerce OS — Native Android Application Blueprint Architecture

This document defines the production Navigation & Screen Architecture for the **Commerce OS Native Android App**.

---

## 🏛️ Bottom Navigation Architecture & App Blueprint

```
                     ┌──────────────────────────────────────────┐
                     │          MainActivity (Scaffold)         │
                     └────────────────────┬─────────────────────┘
                                          │
    ┌──────────────────┬──────────────────┼──────────────────┬──────────────────┐
    │                  │                  │                  │                  │
┌───▼──────────┐   ┌───▼──────────┐   ┌───▼──────────┐   ┌───▼──────────┐   ┌───▼──────────┐
│   HomeTab    │   │  CatalogTab  │   │PrescriptionTab│  │   CartTab    │   │  ProfileTab  │
│  (Storefront)│   │  (Search/Rx) │   │ (AI Vault)   │   │ (Checkout)   │   │  (Health)    │
└──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘
```

---

## 📱 Navigation Tabs & Screen Responsibilities

### 1. 🏠 Home Tab (`HomeTab.kt`)
- **Header**: `COMMERCE.OS` logo, 10-Min SLA badge, Delivery Address selector (`Home - 192.168.1.5`).
- **Search Bar**: Quick search launcher.
- **Category Chips**: All, Pain Relief, Antibiotics, Diabetes, Cardiac, Cold-Chain.
- **Banner**: "10-Min Emergency Medicine Guarantee — Pharmacist Verified".
- **Trending Products**: Product Cards with Rx Badge, Salt composition, Price, and `Add to Cart` trigger.

### 2. 🔍 Catalog Tab (`CatalogTab.kt`)
- **Salt Composition Finder**: Search by active salt (*e.g., Paracetamol, Amoxicillin, Insulin Glargine*).
- **Generic Substitutes Engine**: Compare brand price vs generic bio-equivalent savings (Up to 80% discount).
- **Filters**: OTC vs Prescription Required, Cold-Chain 2-8°C filter.

### 3. 📋 Prescription Vault Tab (`PrescriptionTab.kt`)
- **Prescription Upload Vault**: Drag-and-drop or camera capture.
- **AI OCR Transformer Extraction View**: Live extraction of Doctor Name, Reg No, Patient Name, Medicines & Dosage.
- **Pharmacist Verification Status**: Pending, Approved, or Revision Needed badge.

### 4. 🛒 Cart & Checkout Tab (`CartTab.kt`)
- **Cart Item List**: Quantity controls (`+` / `-`), item removals, subtotal calculation.
- **Rx Verification Hold**: Warning banner if any item requires prescription verification before dispatch.
- **Delivery Address**: Selected shipping address with 10-Min SLA confirmation.
- **Payment Method**: UPI / Cards / COD selector.
- **Action Button**: "Proceed to Express Checkout ($XX.XX)".

### 5. 👤 Profile / Health Tab (`ProfileTab.kt`)
- **User Info**: Account Name, Phone, Zero-Trust Passkey status.
- **Known Drug Allergies**: Medical safety profile (*e.g., Penicillin Allergy*).
- **Chronic Conditions**: Diabetes, Hypertension refill subscriptions.
- **Order History**: Live tracking and invoice downloads.
