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
  distanceKm = 1.0,
  isCod = false
}) {
  const subtotal = Math.round(Number(itemsSubtotal || 0) * 100) / 100;
  const dist = Math.max(0.1, Number(distanceKm) || 1.0);
  const deliveryFee = dist > 4.0 ? (29.0 + Math.round((dist - 4.0) * 5)) : 29.0;
  const taxAmount = Math.round(subtotal * 0.05 * 100) / 100;
  const codFee = isCod ? 15.0 : 0.0;
  const totalAmount = Math.round((subtotal + deliveryFee + taxAmount + codFee) * 100) / 100;

  return {
    itemsSubtotal: subtotal,
    deliveryFee,
    taxAmount,
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
