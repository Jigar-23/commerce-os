export function formatAddress(addr: any): string {
  if (!addr) return 'No Address Provided';
  if (typeof addr === 'string') return addr;
  if (typeof addr === 'object') {
    const parts = [
      addr.tag ? `[${addr.tag}]` : null,
      addr.addressLine,
      addr.landmark ? `Near ${addr.landmark}` : null,
      addr.city,
      addr.state,
      addr.postalCode,
    ].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : 'Address Available';
  }
  return String(addr);
}
