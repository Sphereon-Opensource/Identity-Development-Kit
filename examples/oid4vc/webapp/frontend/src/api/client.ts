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
  // Per OID4VCI 1.0 final §12.2.3 the array element type is format-specific: JWA strings
  // (`"ES256"`) for JWS-based formats, COSE numeric ids (`-7`) for `mso_mdoc`. Typed as
  // `Array<string | number>` so both shapes round-trip without lossy normalisation.
  credential_signing_alg_values_supported?: Array<string | number>
  proof_types_supported?: Record<string, { proof_signing_alg_values_supported: string[] }>
  display?: Array<{
    name: string
    locale?: string
    description?: string
    // OID4VCI 1.0 §11.2.2 branding — present for configs (e.g. mso_mdoc) that advertise it
    // in metadata rather than via an SD-JWT VCT.
    logo?: { uri: string; alt_text?: string }
    background_image?: { uri: string }
    background_color?: string
    text_color?: string
  }>
  // OID4VCI 1.0 final §12.2.3 / §A.5: array of claim-description objects each with a
  // claims-path-pointer. Per the schema, top-level `claims` is only valid for JWS-based
  // formats (dc+sd-jwt, jwt_vc_json). For mso_mdoc the spec puts claims inside
  // `credential_metadata.claims` (see normative spec example).
  claims?: Array<{
    path: string[]
    mandatory?: boolean
    value_type?: string
    display?: Array<{ name: string; locale?: string }>
  }>
  // OID4VCI 1.0 final §12.2.4 (#credential-issuer-parameters): credential-level `display`
  // and `claims` live here — NOT at the top level of the configuration — for every format.
  // The top-level `display`/`claims` fields above are retained only to parse pre-final /
  // non-compliant issuers.
  credential_metadata?: {
    display?: Array<{
      name?: string
      locale?: string
      description?: string
      // §12.2.4 branding — the display object carries the same logo/colour/background members
      // as the issuer- and (legacy) top-level display objects.
      logo?: { uri: string; alt_text?: string }
      background_image?: { uri: string }
      background_color?: string
      text_color?: string
    }>
    claims?: Array<{
      path: Array<string | number>
      mandatory?: boolean
      display?: Array<{ name: string; locale?: string }>
    }>
  }
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

export interface CreateOfferInput {
  credential_configuration_ids: string[]
  credential_subject_data?: Record<string, string>
  grants?: Record<string, unknown>
  scheme?: string
  qr_code?: { size?: number }
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

export interface DcqlCredentialQuery {
  id: string
  format: string
  meta: Record<string, unknown>
  claims: Array<{ path: string[] }>
}

/**
 * Verifier-side credential status policy for one DCQL credential query (NOT part of the wire DCQL).
 * Defaults are strict: status optional, reject revoked + suspended, fail closed on unresolvable.
 */
export interface CredentialStatusPolicy {
  requireStatus?: boolean
  acceptRevoked?: boolean
  acceptSuspended?: boolean
  acceptStatusValues?: number[]
  rejectOnUnresolvable?: boolean
}

export interface CreateAuthRequestInput {
  dcql_query: { credentials: DcqlCredentialQuery[] }
  // HTTPS base URL where the wallet fetches the signed JAR (OID4VP §5.10 `request_uri`).
  // Defaults to the verifier's external base URL when omitted.
  request_uri_base?: string
  // Outer wallet-deeplink URI scheme (without `://`). e.g. 'openid4vp', 'haip', 'oid4vp'.
  wallet_uri_scheme?: string
  // OID4VP §5.9.3 Client Identifier Prefix the verifier should sign under. The verifier
  // selects the matching VerifierSignerBinding (DID kid vs x5c) per request. Send the
  // ClientIdScheme enum constant name: `X509_HASH`, `X509_SAN_DNS`, `DECENTRALIZED_IDENTIFIER`.
  client_id_scheme?: 'X509_HASH' | 'X509_SAN_DNS' | 'DECENTRALIZED_IDENTIFIER'
  request_uri_method?: 'get' | 'post'
  response_mode?: 'direct_post' | 'direct_post.jwt'
  transaction_data?: string[]
  // Per-DCQL-credential-query credential status policy, keyed by the credential query id.
  credential_status_policies?: Record<string, CredentialStatusPolicy>
  qr_code?: { size?: number }
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

/** Correlation id of the status list the example issuer hosts and binds its credentials to. */
export const DEFAULT_STATUS_LIST_ID = 'revocation'

/**
 * Operator view of a single status-list index, returned by the issuer's status-list admin routes.
 * A status list only encodes a bit per index, so this is just the status value: a non-revoked index
 * reads VALID whether or not a credential was ever issued there (the list deliberately can't tell you).
 */
export interface StatusListEntryStatus {
  correlationId: string
  index: number
  length: number
  value: number
  /** 'VALID' | 'REVOKED' | 'SUSPENDED' | 'UNKNOWN'. */
  status: string
  revoked: boolean
}

export interface DemoConfig {
  externalBaseUrl: string
  /** Compose env-file profile name: `default` | `did-jwk` | `x509-san-dns` | `x509-hash` | `haip`. */
  profile: string
  /** True when the HAIP profile is active — verifier UI locks response_mode + request_uri_method. */
  haip: boolean
}

export const api = {
  getDemoConfig: () => fetchJson<DemoConfig>('/config'),

  getIssuerMetadata: () => fetchJson<IssuerMetadata>('/issuer/metadata'),

  // VCT is served directly by Caddy at the root path (not through the webapp proxy)
  getVctMetadata: async (type: string): Promise<VctMetadata> => {
    const res = await fetch(`/oid4vci/vct/${type}`)
    if (!res.ok) throw new Error(`VCT fetch failed: ${res.status}`)
    return res.json()
  },

  createOffer: (body: CreateOfferInput) => fetchJson<OfferResponse>('/issuer/offers', {
    method: 'POST',
    body: JSON.stringify(body),
  }),

  getOfferStatus: (id: string) => fetchJson<OfferStatus>(`/issuer/offers/${id}/status`),

  createAuthRequest: (body: CreateAuthRequestInput) => fetchJson<AuthRequestResponse>('/verifier/requests', {
    method: 'POST',
    body: JSON.stringify(body),
  }),

  getAuthRequestStatus: (id: string) => fetchJson<AuthRequestStatus>(`/verifier/requests/${id}/status`),

  // Status-list admin routes are served directly by the issuer at the root path (like VCT),
  // not through the webapp BFF proxy.
  getStatusListEntry: async (listId: string, index: number): Promise<StatusListEntryStatus> => {
    const res = await fetch(`/statuslists/${encodeURIComponent(listId)}/entries/${index}`)
    if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`)
    return res.json()
  },

  revokeStatusListEntry: async (listId: string, index: number): Promise<StatusListEntryStatus> => {
    const res = await fetch(`/statuslists/${encodeURIComponent(listId)}/entries/${index}/revoke`, { method: 'POST' })
    if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`)
    return res.json()
  },

  // Reset the list to all-valid (drops every revocation). Demo convenience for the in-memory list.
  clearStatusList: async (listId: string): Promise<{ correlationId: string; length: number; cleared: boolean }> => {
    const res = await fetch(`/statuslists/${encodeURIComponent(listId)}/clear`, { method: 'POST' })
    if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`)
    return res.json()
  },
}
