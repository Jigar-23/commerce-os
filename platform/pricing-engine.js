/**
 * Commerce OS - Enterprise Rider Earnings & Quick-Commerce Pricing Domain Service
 * Version: 2.4.0 (Blinkit/Zepto Production Grade)
 */

const PRICING_CONFIG_V2_4 = {
  version: '2.4.0',
  currency: 'INR',
  baseFare: 40.0,
  perKmRate: 9.5,
  baseDistanceKm: 1.0,
  codCollectionIncentive: 15.0,
  coldChainHandlingFee: 15.0,
  itemWeightThreshold: 3,
  perItemWeightSurcharge: 5.0,
  peakSurgeMultiplier: 1.25,
  riderTierIncentives: {
    STANDARD: 0.0,
    BRONZE: 0.0,
    SILVER: 5.0,
    GOLD: 10.0,
    PLATINUM: 15.0
  },
  peakHourWindows: [
    { name: 'Lunch Rush', start: 12, end: 14 },
    { name: 'Dinner Rush', start: 19, end: 22 }
  ]
};

function isCurrentTimePeakSurge(customDate = null) {
  const date = customDate || new Date();
  const hour = date.getHours();
  return PRICING_CONFIG_V2_4.peakHourWindows.some(
    window => hour >= window.start && hour <= window.end
  );
}

function calculateAuthoritativeEarnings({
  distanceKm,
  isCod = false,
  isColdChain = false,
  itemCount = 1,
  riderTier = 'STANDARD',
  surgeOverride = null,
  zoneId = 'NCR_PANIPAT_01'
}) {
  const dist = Math.max(0.1, Number(distanceKm) || 0.1);
  const billableDistanceKm = Math.max(0, dist - PRICING_CONFIG_V2_4.baseDistanceKm);
  const distancePay = Math.round(billableDistanceKm * PRICING_CONFIG_V2_4.perKmRate * 100) / 100;
  
  const basePay = PRICING_CONFIG_V2_4.baseFare;
  const codFee = isCod ? PRICING_CONFIG_V2_4.codCollectionIncentive : 0.0;
  const coldChainFee = isColdChain ? PRICING_CONFIG_V2_4.coldChainHandlingFee : 0.0;
  const itemWeightFee = Math.max(0, (itemCount - PRICING_CONFIG_V2_4.itemWeightThreshold) * PRICING_CONFIG_V2_4.perItemWeightSurcharge);
  const tierIncentive = PRICING_CONFIG_V2_4.riderTierIncentives[riderTier] || 0.0;

  const isPeak = isCurrentTimePeakSurge();
  const surgeMultiplier = surgeOverride !== null ? surgeOverride : (isPeak ? PRICING_CONFIG_V2_4.peakSurgeMultiplier : 1.0);

  const subtotal = basePay + distancePay + codFee + coldChainFee + itemWeightFee + tierIncentive;
  const totalEarnings = Math.round(subtotal * surgeMultiplier * 100) / 100;

  const pricingSnapshot = {
    pricingVersion: PRICING_CONFIG_V2_4.version,
    calculatedAt: new Date().toISOString(),
    zoneId,
    distanceKm: dist,
    breakdown: {
      basePay,
      billableDistanceKm,
      distancePay,
      codFee,
      coldChainFee,
      itemWeightFee,
      tierIncentive,
      isPeakSurge: isPeak,
      surgeMultiplier
    },
    totalEarnings,
    currency: PRICING_CONFIG_V2_4.currency,
    isLocked: true
  };

  return {
    totalEarnings,
    pricingSnapshot
  };
}

function calculateCustomerOrderPricing({
  itemsSubtotal,
  items = [],
  distanceKm = 1.0,
  isCod = false,
  tip = 0,
  customDeliveryFee = null,
  discountAmount = 0,
  packagingFee = 0,
  surgeFee = 0,
  taxAmount = 0
}) {
  const subtotal = Math.round(Number(itemsSubtotal || 0) * 100) / 100;
  const discount = Math.max(0, Math.round(Number(discountAmount || 0) * 100) / 100);
  const deliveryFee = customDeliveryFee !== null ? Math.max(0, Number(customDeliveryFee)) : (subtotal >= 199.0 ? 0.0 : 2.0);
  const handlingFee = Math.max(0, Number(packagingFee || 0));
  const surgeAmount = Math.max(0, Number(surgeFee || 0));
  const tipAmount = Math.max(0, Number(tip) || 0);
  const codFee = 0.0;

  // Authoritative GST Tax Calculation & Per-Line Breakdown (Finding 8):
  // Catalog item prices in quick-commerce pharmacy are tax-inclusive:
  // - Per-item medicine rate (CBIC GST rates: 5% or 12% per SKU HSN)
  // - 18% GST for delivery, surge & handling service fees
  let lineTaxes = [];
  let medicineGst = 0;
  if (Array.isArray(items) && items.length > 0) {
    for (const it of items) {
      const rate = Number(it.gstRate != null ? it.gstRate : (it.gst_rate != null ? it.gst_rate : 5.0));
      const lineTotal = Number(it.lineTotal != null ? it.lineTotal : (it.line_total != null ? it.line_total : ((it.unitPrice || it.price || 0) * (it.quantity || 1))));
      const lineTax = lineTotal > 0 && rate > 0 ? Math.round((lineTotal - (lineTotal / (1 + (rate / 100)))) * 100) / 100 : 0.0;
      const lineCgst = Math.round((lineTax / 2) * 100) / 100;
      const lineSgst = Math.round((lineTax - lineCgst) * 100) / 100;
      medicineGst += lineTax;
      lineTaxes.push({
        productId: it.productId || it.product_id || null,
        sku: it.sku || null,
        hsnCode: it.hsnCode || it.hsn_code || '3004',
        gstRatePercent: rate,
        taxableAmount: Math.round((lineTotal - lineTax) * 100) / 100,
        taxAmount: lineTax,
        cgst: lineCgst,
        sgst: lineSgst
      });
    }
    medicineGst = Math.round(medicineGst * 100) / 100;
  } else if (subtotal > 0) {
    medicineGst = Math.round((subtotal - (subtotal / 1.05)) * 100) / 100;
  }

  const serviceFees = deliveryFee + handlingFee + surgeAmount;
  const feeGst = serviceFees > 0 ? Math.round((serviceFees - (serviceFees / 1.18)) * 100) / 100 : 0.0;
  const computedTax = Number(taxAmount || 0) > 0 ? Number(taxAmount) : Math.round((medicineGst + feeGst) * 100) / 100;
  const tax = Math.max(0, computedTax);
  const cgst = Math.round((tax / 2) * 100) / 100;
  const sgst = Math.round((tax - cgst) * 100) / 100;

  // Subtotal is tax-inclusive of medicine GST; delivery/handling/surge fees are tax-inclusive of service GST.
  const totalAmount = Math.max(0, Math.round((subtotal - discount + deliveryFee + handlingFee + surgeAmount + tipAmount + codFee) * 100) / 100);

  return {
    itemsSubtotal: subtotal,
    discountAmount: discount,
    deliveryFee,
    handlingFee,
    surgeFee: surgeAmount,
    taxAmount: tax,
    taxBreakdown: {
      cgst,
      sgst,
      totalTax: tax,
      lineTaxes,
      itemTaxes: lineTaxes,
      totalMedicineTax: medicineGst,
      totalFeeTax: feeGst,
      isTaxInclusive: true
    },
    tipAmount,
    codFee,
    totalAmount
  };
}

module.exports = {
  PRICING_CONFIG_V2_4,
  isCurrentTimePeakSurge,
  calculateAuthoritativeEarnings,
  calculateCustomerOrderPricing
};
