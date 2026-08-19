import { useSession } from './session-store';

const isProduction = process.env.NODE_ENV === 'production';

function resolveWebEndpoint(envVal: string | undefined, defaultLocal: string): string {
  if (envVal && envVal.trim().length > 0) {
    return envVal.replace(/\/$/, '');
  }
  if (isProduction) {
    return ''; // Fail closed in production rather than silently connecting to localhost
  }
  return defaultLocal;
}

const GATEWAY_ORIGIN = resolveWebEndpoint(
  process.env.NEXT_PUBLIC_API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL,
  'http://localhost:8083'
);

export const API_CONFIG = {
  GATEWAY: GATEWAY_ORIGIN,
  CATALOG: GATEWAY_ORIGIN,
  IDENTITY: GATEWAY_ORIGIN,
  ORDER: GATEWAY_ORIGIN,
  CUSTOMER: GATEWAY_ORIGIN,
  CART: GATEWAY_ORIGIN,
  AI: GATEWAY_ORIGIN,
  PAYMENT: GATEWAY_ORIGIN,
  INVENTORY: GATEWAY_ORIGIN,
};

export interface MedicineProduct {
  id: string;
  sku: string;
  name: string;
  brandName: string;
  manufacturer?: string;
  packSize?: string;
  rxRequirement?: string;
  price: number;
  discountedPrice?: number;
  mrp?: number;
  expressDeliverySlaMins?: number;
  coldChainRequired?: boolean;
  therapeuticCategory?: string;
  rating?: number;
  reviewCount?: number;
  image?: string;
  discountPercentage?: number;
  inStock?: boolean;
  stockCount?: number;
  templateType?: string;
}

export interface OrderLine {
  sku: string;
  name: string;
  packSize?: string;
  unitPrice: number;
  quantity: number;
  rxRequired?: boolean;
  coldChain?: boolean;
}

export interface OrderResponse {
  id: string;
  orderId?: string;
  customerId: string;
  orderType: string;
  status: string;
  totalAmount: number;
  taxAmount?: number;
  deliveryFee?: number;
  deliveryAddress?: string | { formattedAddress?: string; addressLine?: string };
  items?: OrderLine[];
  deliveryOtp?: string;
  deliverySlaMins?: number;
  slaMins?: number;
  paymentMethod?: string;
  paymentId?: string;
  paymentStatus?: string;
  createdAt?: string;
  delivery?: string;
  orderStatus?: string;
  riderName?: string;
  riderPhone?: string;
  cod?: {
    amountToCollect: number;
    collectionStatus: string;
    collectedAt?: string | null;
  };
  cancellation?: {
    reason: string;
    cancelledBy: string;
    cancelledAt: string;
    restorationNote?: string;
  } | null;
  cancellationRequest?: {
    reason: string;
    requestedBy: string;
    requestedAt: string;
    status: string;
  } | null;
  trackingCheckpoints?: {
    status: string;
    label: string;
    actor: string;
    createdAt: string;
  }[];
}

/**
 * Retrieve authenticated JWT bearer token dynamically from session store.
 */
export function getActiveAuthToken(): string | null {
  try {
    if (typeof window !== 'undefined') {
      const state = useSession.getState();
      return state?.user?.accessToken || null;
    }
  } catch (_) {}
  return null;
}

/**
 * Retrieve authenticated user ID dynamically from session store.
 */
export function getActiveCustomerId(): string {
  try {
    if (typeof window !== 'undefined') {
      const state = useSession.getState();
      if (state?.user?.userId) return state.user.userId;
    }
  } catch (_) {}
  return '';
}

function buildHeaders(extraHeaders: Record<string, string> = {}): Record<string, string> {
  const headers: Record<string, string> = { ...extraHeaders };
  const token = getActiveAuthToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

async function getJson(url: string, headers: Record<string, string> = {}) {
  if (!url || !url.startsWith('http')) {
    throw new Error('CRITICAL_CONFIGURATION_ERROR: Mandatory production API endpoint is not configured.');
  }
  const res = await fetch(url, {
    headers: buildHeaders(headers),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: Failed to fetch from authoritative backend service.`);
  return res.json();
}

/**
 * Fetch authoritative catalog medicines from Catalog Service.
 */
export async function fetchCatalogMedicines(query: string = '', category?: string): Promise<MedicineProduct[]> {
  const url = query.trim()
    ? `${API_CONFIG.CATALOG}/api/v1/catalog/medicines/search?query=${encodeURIComponent(query)}`
    : `${API_CONFIG.CATALOG}/api/v1/catalog/medicines`;
  
  const data = await getJson(url);
  let result = data.content || data || [];
  if (Array.isArray(result)) {
    if (category) {
      result = result.filter((m: MedicineProduct) => m.therapeuticCategory === category);
    }
    return result;
  }
  return [];
}

export async function fetchMedicineById(id: string): Promise<MedicineProduct | null> {
  return getJson(`${API_CONFIG.CATALOG}/api/v1/catalog/medicines/${id}`);
}

export async function fetchCart(customerId?: string) {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to access cart.');
  return getJson(`${API_CONFIG.CART}/api/v1/cart/${targetCustomerId}`);
}

export async function addCartItem(item: Record<string, unknown>, customerId?: string) {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to modify cart.');
  const res = await fetch(`${API_CONFIG.CART}/api/v1/cart/${targetCustomerId}/items`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(item),
  });
  if (!res.ok) throw new Error('Failed to update cart');
  return res.json();
}

export async function removeCartItem(sku: string, customerId?: string) {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to modify cart.');
  const res = await fetch(`${API_CONFIG.CART}/api/v1/cart/${targetCustomerId}/items/${encodeURIComponent(sku)}`, {
    method: 'DELETE',
    headers: buildHeaders(),
  });
  if (!res.ok) throw new Error('Failed to remove cart item');
  return res.json();
}

export async function login(credentials: { email?: string; phone?: string; password?: string }) {
  const res = await fetch(`${API_CONFIG.IDENTITY}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials),
  });
  if (!res.ok) throw new Error('Login authentication failed');
  return res.json();
}

export async function register(credentials: { email: string; password: string; fullName?: string }) {
  const res = await fetch(`${API_CONFIG.IDENTITY}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials),
  });
  if (!res.ok) throw new Error('Registration failed');
  return res.json();
}

export async function fetchCustomerProfile(customerId?: string) {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to fetch profile.');
  return getJson(`${API_CONFIG.CUSTOMER}/api/v1/customers/${targetCustomerId}`);
}

/**
 * Authoritative Order Placement.
 */
export async function createOrder(payload: {
  customerId?: string;
  orderType: string;
  addressId?: string;
  deliveryAddress?: any;
  paymentMethod: string;
  prescriptionId?: string;
  items: OrderLine[];
}): Promise<OrderResponse> {
  const targetCustomerId = payload.customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to place orders.');

  const res = await fetch(`${API_CONFIG.ORDER}/api/v1/orders`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ ...payload, customerId: targetCustomerId }),
  });
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({}));
    throw new Error(errBody?.message || errBody?.error || `Order creation failed with status ${res.status}`);
  }
  return res.json();
}

export async function fetchOrders(customerId?: string): Promise<OrderResponse[]> {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to fetch orders.');
  return getJson(`${API_CONFIG.ORDER}/api/v1/orders/customer/${targetCustomerId}`);
}

export async function fetchOrderById(orderId: string): Promise<OrderResponse> {
  return getJson(`${API_CONFIG.ORDER}/api/v1/orders/${orderId}`);
}

export async function cancelOrder(orderId: string, reason: string = 'Customer requested cancellation') {
  const res = await fetch(`${API_CONFIG.ORDER}/api/v1/orders/${orderId}/cancel`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason, cancelledBy: 'customer' }),
  });
  if (!res.ok) throw new Error('Order cancellation failed');
  return res.json();
}

export async function transitionOrder(orderId: string, status: string, actor: string = 'seller') {
  const res = await fetch(`${API_CONFIG.ORDER}/api/v1/orders/${orderId}/transition`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ status, actor }),
  });
  if (!res.ok) throw new Error('Order transition failed');
  return res.json();
}

export async function fetchSellerOrders(): Promise<OrderResponse[]> {
  return getJson(`${API_CONFIG.ORDER}/api/v1/orders/seller`);
}

export async function fetchSellerInventory(): Promise<MedicineProduct[]> {
  return getJson(`${API_CONFIG.CATALOG}/api/v1/catalog/seller/inventory`);
}

export async function updateProductStock(productIdOrSku: string, stockCount: number): Promise<MedicineProduct> {
  const res = await fetch(`${API_CONFIG.CATALOG}/api/v1/catalog/medicines/${encodeURIComponent(productIdOrSku)}/stock`, {
    method: 'PATCH',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ stockCount }),
  });
  if (!res.ok) throw new Error('Stock update failed');
  return res.json();
}

export async function fetchActiveDelivery(): Promise<OrderResponse> {
  return getJson(`${API_CONFIG.ORDER}/api/v1/orders/active-delivery`);
}

export async function deliverWithOtp(orderId: string, submittedOtp: string): Promise<OrderResponse> {
  const res = await fetch(`${API_CONFIG.ORDER}/api/v1/orders/${orderId}/deliver-with-otp`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ submittedOtp }),
  });
  if (!res.ok) throw new Error('Delivery OTP verification failed');
  return res.json();
}

export async function initiatePayment(paymentPayload: {
  orderId: string;
  amount: number;
  paymentMethod: string;
  currency?: string;
}) {
  const res = await fetch(`${API_CONFIG.PAYMENT}/api/v1/payments/initiate`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      ...paymentPayload,
      currency: paymentPayload.currency || 'INR',
    }),
  });
  if (!res.ok) throw new Error('Payment initiation failed');
  return res.json();
}

export async function checkDrugInteraction(medicines: string[]) {
  const res = await fetch(`${API_CONFIG.AI}/api/v1/ai/drug-interaction`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ medicines }),
  });
  if (!res.ok) throw new Error('Drug interaction analysis failed');
  return res.json();
}

export interface SavedAddress {
  id: string;
  tag: string;
  addressLine: string;
  city: string;
  state?: string;
  postalCode: string;
  country?: string;
  landmark?: string;
  contactName?: string;
  contactPhone?: string;
  isDefault?: boolean;
  latitude?: number;
  longitude?: number;
  deliveryInstructions?: string;
  placeId?: string;
  accuracyMeters?: number;
}

export async function fetchCustomerAddresses(customerId?: string): Promise<SavedAddress[]> {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) return [];
  const list = await getJson(`${API_CONFIG.CUSTOMER}/api/v1/customers/${targetCustomerId}/addresses`);
  if (Array.isArray(list)) return list;
  return [];
}

export async function createCustomerAddress(
  address: Partial<SavedAddress>,
  customerId?: string
): Promise<SavedAddress> {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to save address.');
  const res = await fetch(`${API_CONFIG.CUSTOMER}/api/v1/customers/${targetCustomerId}/addresses`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(address),
  });
  if (!res.ok) throw new Error('Failed to create address');
  return res.json();
}

export async function setDefaultCustomerAddress(
  addressId: string,
  customerId?: string
): Promise<SavedAddress> {
  const targetCustomerId = customerId || getActiveCustomerId();
  if (!targetCustomerId) throw new Error('UNAUTHENTICATED: Active session required to update default address.');
  const res = await fetch(
    `${API_CONFIG.CUSTOMER}/api/v1/customers/${targetCustomerId}/addresses/${addressId}/default-shipping`,
    {
      method: 'POST',
      headers: buildHeaders(),
    }
  );
  if (!res.ok) throw new Error('Failed to set default address');
  return res.json();
}

/**
 * Customer Realtime Delivery SSE Event Stream Subscription with Last-Event-ID reconnection.
 */
export function subscribeDeliveryRealtimeStream(
  orderId: string,
  onEvent: (data: any) => void,
  onError?: (err: any) => void
): () => void {
  let eventSource: EventSource | null = null;
  let isClosed = false;

  const connect = async () => {
    try {
      // Secure ticket acquisition: Request short-lived single-use SSE Ticket via Bearer Header
      let ticket: string | null = null;
      const token = getActiveAuthToken();
      if (token) {
        const ticketRes = await fetch(`${API_CONFIG.ORDER}/api/v1/delivery/sse-ticket`, {
          method: 'POST',
          headers: buildHeaders({ 'Content-Type': 'application/json' }),
        }).catch(() => null);
        if (ticketRes && ticketRes.ok) {
          const ticketData = await ticketRes.json();
          ticket = ticketData.ticket || ticketData.sseTicket || null;
        }
      }

      if (isClosed) return;

      const queryParams = new URLSearchParams();
      if (ticket) queryParams.set('ticket', ticket);

      const queryString = queryParams.toString();
      const url = `${API_CONFIG.ORDER}/api/v1/delivery/order/${orderId}/stream${queryString ? `?${queryString}` : ''}`;
      eventSource = new EventSource(url);

      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          onEvent(data);
        } catch (e) {
          console.error('Error parsing SSE event data:', e);
        }
      };

      eventSource.onerror = (err) => {
        if (onError) onError(err);
      };
    } catch (err) {
      if (onError) onError(err);
    }
  };

  connect();

  return () => {
    isClosed = true;
    eventSource?.close();
  };
}
