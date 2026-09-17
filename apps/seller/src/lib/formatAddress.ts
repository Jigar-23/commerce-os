export function formatAddress(addr: any): string {
  if (!addr) return 'No Address Provided';
  if (typeof addr === 'string') {
    try {
      const parsed = JSON.parse(addr);
      if (parsed && typeof parsed === 'object') return formatAddress(parsed);
    } catch (_) {
      return addr;
    }
    return addr;
  }
  if (typeof addr === 'object') {
    const lineParts: string[] = [];

    // Tag (Home / Work / Other)
    const tag = addr.tag || addr.address_type || addr.addressType;
    if (tag && typeof tag === 'string') {
      lineParts.push(`[${tag.toUpperCase()}]`);
    }

    // Building / Flat / House number
    const buildingInfo = [
      addr.house_number || addr.houseNumber,
      addr.flat_number || addr.flatNumber || addr.floor,
      addr.building || addr.building_name || addr.buildingName
    ].filter(Boolean).join(', ');

    // Street / Address line
    const streetLine = addr.address_line || addr.addressLine || addr.street || addr.address;

    if (buildingInfo && streetLine && !streetLine.includes(buildingInfo)) {
      lineParts.push(buildingInfo);
      lineParts.push(streetLine);
    } else if (streetLine) {
      lineParts.push(streetLine);
    } else if (buildingInfo) {
      lineParts.push(buildingInfo);
    }

    // Landmark
    const landmark = addr.landmark;
    if (landmark && typeof landmark === 'string' && landmark.trim()) {
      lineParts.push(`Near ${landmark.trim()}`);
    }

    // City & State
    const city = addr.city || addr.district;
    const state = addr.state;
    if (city && state && city !== state) {
      lineParts.push(`${city}, ${state}`);
    } else if (city) {
      lineParts.push(city);
    } else if (state) {
      lineParts.push(state);
    }

    // Pincode / Postal code
    const pin = addr.postal_code || addr.postalCode || addr.pincode || addr.pin_code || addr.zip;
    if (pin) {
      lineParts.push(`PIN: ${pin}`);
    }

    const res = lineParts.filter(Boolean).join(', ');
    return res.length > 0 ? res : 'Address Available';
  }
  return String(addr);
}

/**
 * Strictly enforce DD/MM/YYYY formatting throughout application.
 */
export function formatDate(input: string | number | Date | null | undefined): string {
  if (!input) return '—';
  const d = new Date(input);
  if (isNaN(d.getTime())) return '—';
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  return `${day}/${month}/${year}`;
}

export function formatDateTime(input: string | number | Date | null | undefined): string {
  if (!input) return '—';
  const d = new Date(input);
  if (isNaN(d.getTime())) return '—';
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  let hours = d.getHours();
  const mins = String(d.getMinutes()).padStart(2, '0');
  const ampm = hours >= 12 ? 'PM' : 'AM';
  hours = hours % 12 || 12;
  const hourStr = String(hours).padStart(2, '0');
  return `${day}/${month}/${year}, ${hourStr}:${mins} ${ampm}`;
}
