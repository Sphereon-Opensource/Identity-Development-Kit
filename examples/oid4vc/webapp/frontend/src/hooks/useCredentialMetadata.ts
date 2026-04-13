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
        const syntheticClaims = config.claims ? Object.entries(config.claims).map(([name, meta]) => ({
          path: [name],
          display: meta.display?.map(d => ({ locale: d.locale ?? 'en-US', label: d.name })),
          mandatory: meta.mandatory,
        })) : undefined
        setVctCache(prev => ({ ...prev, [id]: { vct: id, display: syntheticDisplay, claims: syntheticClaims } }))
      }
    }
  }, [metadata])

  const configs = metadata?.credential_configurations_supported ?? {}

  return { metadata, configs, vctCache, error }
}
