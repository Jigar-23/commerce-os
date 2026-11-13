/**
 * Commerce OS — Core Invariant Verification Engine
 * ------------------------------------------------
 * Invariants are mathematical and business laws that must NEVER be violated
 * under ANY combination of user inputs, network failures, or node errors.
 */

const ORDER_TRANSITIONS = {
  CREATED: ['PAYMENT_PENDING', 'CANCELLED'],
  PAYMENT_PENDING: ['PAID', 'PAYMENT_FAILED', 'CANCELLED'],
  PAID: ['ALLOCATING_FLEET', 'CANCELLED'],
  ALLOCATING_FLEET: ['ASSIGNED', 'OUTBOX_RETRY', 'CANCELLED'],
  ASSIGNED: ['AT_STORE', 'CANCELLED'],
  AT_STORE: ['PICKED_UP', 'CANCELLED'],
  PICKED_UP: ['OUT_FOR_DELIVERY'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'RETURNED_TO_STORE'],
  DELIVERED: [], // Terminal
  CANCELLED: [], // Terminal
  RETURNED_TO_STORE: [] // Terminal
};

class InvariantEngine {
  /**
   * INVARIANT 1: Universal Financial Integrity
   * Total charged MUST equal exact cents sum: Items - Discounts + Fees + Taxes.
   * Floating-point issues (e.g. 0.1 + 0.2 = 0.30000000000000004) are strictly disallowed.
   */
  static verifyFinancialIntegrity(cartData) {
    const { items, discount = 0, deliveryFee = 0, tax = 0, finalTotal } = cartData;
    
    // Calculate in integer cents / paise to eliminate float rounding errors
    const calculatedItemsTotal = items.reduce((sum, item) => {
      const priceCents = Math.round(item.unitPrice * 100);
      return sum + (priceCents * item.quantity);
    }, 0);

    const discountCents = Math.round(discount * 100);
    const feeCents = Math.round(deliveryFee * 100);
    const taxCents = Math.round(tax * 100);
    const expectedTotalCents = Math.max(0, calculatedItemsTotal - discountCents + feeCents + taxCents);

    const actualTotalCents = Math.round(finalTotal * 100);

    if (expectedTotalCents !== actualTotalCents) {
      return {
        passed: false,
        invariant: 'INVARIANT_FINANCIAL_INTEGRITY',
        expected: (expectedTotalCents / 100).toFixed(2),
        actual: (actualTotalCents / 100).toFixed(2),
        delta: Math.abs(expectedTotalCents - actualTotalCents) / 100,
        message: `Financial mismatch detected! Expected ${(expectedTotalCents / 100).toFixed(2)}, got ${(actualTotalCents / 100).toFixed(2)}`
      };
    }

    if (finalTotal < 0) {
      return {
        passed: false,
        invariant: 'INVARIANT_FINANCIAL_NON_NEGATIVE',
        message: `Final total cannot be negative: ${finalTotal}`
      };
    }

    return { passed: true, invariant: 'INVARIANT_FINANCIAL_INTEGRITY' };
  }

  /**
   * INVARIANT 2: Physical Inventory Non-Negativity
   * Stock can NEVER drop below zero, even under heavy concurrency.
   */
  static verifyInventoryNonNegative(stockBefore, requestedDebit, stockAfter) {
    if (stockAfter < 0) {
      return {
        passed: false,
        invariant: 'INVARIANT_INVENTORY_NON_NEGATIVE',
        message: `Inventory dropped below zero: ${stockAfter} (was ${stockBefore}, debited ${requestedDebit})`
      };
    }

    if (stockBefore < requestedDebit && stockAfter !== stockBefore) {
      return {
        passed: false,
        invariant: 'INVARIANT_OVERDEBIT_PREVENTED',
        message: `Insufficient inventory debited: requested ${requestedDebit} from ${stockBefore}`
      };
    }

    return { passed: true, invariant: 'INVARIANT_INVENTORY_NON_NEGATIVE' };
  }

  /**
   * INVARIANT 3: State Machine Unidirectional Flow
   * An order cannot leapfrog valid intermediate states (e.g. from CREATED directly to DELIVERED).
   */
  static verifyStateTransition(currentState, nextState) {
    if (currentState === nextState) {
      return { passed: true, idempotent: true };
    }

    const allowedNext = ORDER_TRANSITIONS[currentState] || [];
    if (!allowedNext.includes(nextState)) {
      return {
        passed: false,
        invariant: 'INVARIANT_STATE_MACHINE_CORRECTNESS',
        message: `Illegal state leap: Cannot transition from '${currentState}' to '${nextState}'. Allowed: [${allowedNext.join(', ')}]`
      };
    }

    return { passed: true, invariant: 'INVARIANT_STATE_MACHINE_CORRECTNESS' };
  }

  /**
   * INVARIANT 4: Idempotent Payment & Order Execution
   * Replaying the same request with the same idempotency key must never generate duplicate side-effects.
   */
  static verifyIdempotency(firstPayload, secondPayload) {
    const isSameOrder = firstPayload.order_id === secondPayload.order_id;
    const isSameAmount = firstPayload.amount === secondPayload.amount;

    if (!isSameOrder || !isSameAmount) {
      return {
        passed: false,
        invariant: 'INVARIANT_IDEMPOTENCY_INTEGRITY',
        message: 'Idempotency violation: Replayed request returned mismatched payload attributes'
      };
    }

    return { passed: true, invariant: 'INVARIANT_IDEMPOTENCY_INTEGRITY' };
  }

  /**
   * INVARIANT 5: Zero-Deadlock / Infinite Spinner Prevention
   * Any client request, even upon total gateway failure, must resolve cleanly within SLA.
   */
  static async verifyNoDeadlock(actionPromise, timeoutMs = 3000) {
    let timer;
    const timeoutPromise = new Promise((_, reject) => {
      timer = setTimeout(() => {
        reject(new Error(`DEADLOCK: Operation timed out after ${timeoutMs}ms without terminating`));
      }, timeoutMs);
    });

    try {
      const result = await Promise.race([actionPromise, timeoutPromise]);
      clearTimeout(timer);
      return { passed: true, result };
    } catch (err) {
      clearTimeout(timer);
      const isDeadlock = err.message && err.message.includes('DEADLOCK');
      return {
        passed: !isDeadlock,
        invariant: 'INVARIANT_ZERO_DEADLOCK',
        message: err.message
      };
    }
  }
}

module.exports = {
  InvariantEngine,
  ORDER_TRANSITIONS
};
