const API_BASE = '/webapp/api'

export async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`${res.status}: ${text}`)
  }
  return res.json()
}

export interface IssuerMetadata {
  credential_issuer: string
  credential_endpoint: string
  credential_configurations_supported: Record<string, CredentialConfiguration>
}

export interface CredentialConfiguration {
  format: string
  scope?: string
  vct?: string
  doctype?: string
  cryptographic_binding_methods_supported?: string[]
  credential_signing_alg_values_supported?: string[]
  proof_types_supported?: Record<string, { proof_signing_alg_values_supported: string[] }>
  display?: Array<{ name: string; locale?: string; description?: string }>
  claims?: Record<string, { mandatory?: boolean; display?: Array<{ name: string; locale?: string }> }>
}

export interface VctMetadata {
  vct: string
  name?: string
  description?: string
  display?: VctDisplay[]
  claims?: VctClaim[]
}

export interface VctDisplay {
  locale: string
  name: string
  description?: string
  rendering?: {
    simple?: {
      logo?: { uri: string; alt_text?: string }
      background_image?: { uri: string }
      background_color?: string
      text_color?: string
    }
  }
}

export interface VctClaim {
  path: string[]
  display?: Array<{ locale: string; label: string; description?: string }>
  sd?: string
  mandatory?: boolean
}

export interface OfferResponse {
  correlation_id: string
  offer_uri: string
  status_uri?: string
  qr_uri?: string
  tx_code?: string
}

export interface OfferStatus {
  correlation_id: string
  status: string
  last_updated?: number
  error?: { code: string; message: string }
}

export interface AuthRequestResponse {
  correlation_id: string
  query_id?: string
  request_uri?: string
  status_uri?: string
  qr_uri?: string
}

export interface AuthRequestStatus {
  correlation_id: string
  status: string
  last_updated?: number
  error?: { code: string; message: string }
  verified_data?: {
    credential_claims?: Array<{
      id: string
      type?: string
      claims: Record<string, unknown>
    }>
  }
}

export const api = {
  getIssuerMetadata: () => fetchJson<IssuerMetadata>('/issuer/metadata'),

  // VCT is served directly by Caddy at the root path (not through the webapp proxy)
  getVctMetadata: async (type: string): Promise<VctMetadata> => {
    const res = await fetch(`/oid4vci/vct/${type}`)
    if (!res.ok) throw new Error(`VCT fetch failed: ${res.status}`)
    return res.json()
  },

  createOffer: (body: unknown) => fetchJson<OfferResponse>('/issuer/offers', {
    method: 'POST',
    body: JSON.stringify(body),
  }),

  getOfferStatus: (id: string) => fetchJson<OfferStatus>(`/issuer/offers/${id}/status`),

  createAuthRequest: (body: unknown) => fetchJson<AuthRequestResponse>('/verifier/requests', {
    method: 'POST',
    body: JSON.stringify(body),
  }),

  getAuthRequestStatus: (id: string) => fetchJson<AuthRequestStatus>(`/verifier/requests/${id}/status`),
}
