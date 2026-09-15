export interface AuthState {
  userId?: string;
  email?: string;
  roles: string[];
  accessToken?: string;
  isAuthenticated: boolean;
}

export function parseJwtClaims(token: string): Record<string, any> | null {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
}

export async function registerWebAuthnPasskey(email: string): Promise<boolean> {
  if (!window.PublicKeyCredential) {
    console.warn('WebAuthn Passkeys are not supported on this browser hardware.');
    return false;
  }
  // Simulated WebAuthn Hardware Registration
  return true;
}
