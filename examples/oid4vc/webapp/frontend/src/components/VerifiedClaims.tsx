import React from 'react'
import type { VctDisplay, VctClaim } from '../api/client'

interface VerifiedClaimsProps {
  claims: Record<string, unknown>
  display?: VctDisplay
  claimMetadata?: VctClaim[]
  locale?: string
}

export function VerifiedClaims({ claims, display, claimMetadata, locale = 'en-US' }: VerifiedClaimsProps) {
  const rendering = display?.rendering?.simple

  const getClaimLabel = (key: string): string => {
    const meta = claimMetadata?.find(c => c.path.join('.') === key)
    const match = meta?.display?.find(d => d.locale === locale) ?? meta?.display?.[0]
    return match?.label ?? key
  }

  return (
    <div
      className="verified-claims-card"
      style={{
        backgroundColor: rendering?.background_color ?? '#1a56db',
        color: rendering?.text_color ?? '#ffffff',
        backgroundImage: rendering?.background_image?.uri ? `url(${rendering.background_image.uri})` : undefined,
        backgroundSize: 'cover',
      }}
    >
      {rendering?.logo && (
        <img
          className="credential-logo"
          src={rendering.logo.uri}
          alt={rendering.logo.alt_text ?? 'Logo'}
        />
      )}
      <h3>{display?.name ?? 'Verified Credential'}</h3>
      <div className="claims-list">
        {Object.entries(claims).map(([key, value]) => (
          <div key={key} className="claim-item">
            <span className="claim-label">{getClaimLabel(key)}</span>
            <span className="claim-value">{String(value)}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
