import type { ReactNode } from 'react'
import type { VctDisplay } from '../api/client'

interface CredentialCardProps {
  name: string
  format: string
  display?: VctDisplay
  onClick?: () => void
  selected?: boolean
  children?: ReactNode
}

export function CredentialCard({ name, format, display, onClick, selected, children }: CredentialCardProps) {
  const rendering = display?.rendering?.simple
  const bgColor = rendering?.background_color ?? '#1a56db'
  const textColor = rendering?.text_color ?? '#ffffff'
  const bgImage = rendering?.background_image?.uri
  const logo = rendering?.logo

  return (
    <div
      className={`credential-card ${selected ? 'selected' : ''} ${onClick ? 'clickable' : ''}`}
      style={{
        backgroundColor: bgColor,
        color: textColor,
        backgroundImage: bgImage ? `url(${bgImage})` : undefined,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }}
      onClick={onClick}
    >
      <div className="credential-card-top">
        {logo && (
          <img
            className="credential-logo"
            src={logo.uri}
            alt={logo.alt_text ?? 'Logo'}
          />
        )}
        <div className="credential-info">
          <h3 className="credential-name">{display?.name ?? name}</h3>
          {display?.description && (
            <p className="credential-description">{display.description}</p>
          )}
        </div>
      </div>
      <div className="credential-card-bottom">
        <span className="credential-format-badge">{format}</span>
      </div>
      {children}
    </div>
  )
}
