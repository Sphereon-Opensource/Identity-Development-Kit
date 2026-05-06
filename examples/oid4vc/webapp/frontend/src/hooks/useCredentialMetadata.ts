import { useState, useEffect } from 'react'
import { api, type IssuerMetadata, type VctMetadata } from '../api/client'

/**
 * Fetches issuer metadata and builds a VCT metadata cache for all credential configurations.
 * For configurations without a vct URL (e.g. mdoc), synthesizes VCT-like metadata
 * from the issuer credential configuration.
 */
export function useCredentialMetadata() {
  const [metadata, setMetadata] = useState<IssuerMetadata | null>(null)
  const [vctCache, setVctCache] = useState<Record<string, VctMetadata>>({})
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.getIssuerMetadata()
      .then(setMetadata)
      .catch(e => setError(e.message))
  }, [])

  useEffect(() => {
    if (!metadata) return
    const configs = metadata.credential_configurations_supported
    for (const [id, config] of Object.entries(configs)) {
      if (vctCache[id]) continue

      if (config.vct) {
        const vctName = config.vct.split('/').pop() ?? id
        api.getVctMetadata(vctName)
          .then(vct => setVctCache(prev => ({ ...prev, [id]: vct })))
          .catch(() => {})
      } else {
        const syntheticDisplay = config.display?.map(d => ({
          locale: d.locale ?? 'en-US',
          name: d.name,
          description: d.description,
        }))
        // Per OID4VCI 1.0 final the claims metadata's location is format-dependent:
        //   - dc+sd-jwt / jwt_vc_json → top-level `claims` array.
        //   - mso_mdoc → `credential_metadata.claims` (the §A.3 schema branch doesn't
        //     allow top-level `claims`; the normative mso_mdoc example places them inside
        //     credential_metadata).
        // Read whichever side actually has them; both shapes use `path` claim pointers.
        const mdocClaims = config.credential_metadata?.claims
        const sourceClaims =
          mdocClaims && mdocClaims.length > 0
            ? mdocClaims.map(c => ({
                // credential_metadata path elements may be string or integer (§A.5);
                // for mdoc they're always strings ([namespace, elementId]).
                path: c.path.map(p => String(p)),
                display: c.display,
                mandatory: c.mandatory,
              }))
            : config.claims
        const syntheticClaims = sourceClaims?.map(c => ({
          path: c.path,
          display: c.display?.map(d => ({ locale: d.locale ?? 'en-US', label: d.name })),
          mandatory: c.mandatory,
        }))
        setVctCache(prev => ({ ...prev, [id]: { vct: id, display: syntheticDisplay, claims: syntheticClaims } }))
      }
    }
  }, [metadata])

  const configs = metadata?.credential_configurations_supported ?? {}

  return { metadata, configs, vctCache, error }
}
