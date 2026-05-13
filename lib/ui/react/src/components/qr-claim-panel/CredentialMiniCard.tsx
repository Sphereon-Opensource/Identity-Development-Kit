import {forwardRef} from 'react'
import {type CredentialPreviewItem} from './types'
import styles from './CredentialMiniCard.module.css'

export interface CredentialMiniCardProps {
  credential: CredentialPreviewItem
  mandatoryLabel?: string
  className?: string
}

export const CredentialMiniCard = forwardRef<HTMLDivElement, CredentialMiniCardProps>(function CredentialMiniCard(
  {credential, mandatoryLabel, className},
  ref,
) {
  const {displayName, type, logoUrl, mandatory} = credential
  return (
    <div ref={ref} className={`${styles.root} ${className ?? ''}`.trim()} data-mandatory={mandatory ? 'true' : undefined}>
      <div className={styles.logoWrap} aria-hidden={logoUrl ? undefined : 'true'}>
        {logoUrl ? (
          <img className={styles.logo} src={logoUrl} alt="" />
        ) : (
          <span className={styles.logoFallback}>{displayName.charAt(0).toUpperCase()}</span>
        )}
      </div>
      <div className={styles.info}>
        <span className={styles.name}>{displayName}</span>
        {type ? <span className={styles.type}>{type}</span> : null}
      </div>
      {mandatory && mandatoryLabel ? (
        <span className={styles.mandatoryBadge}>{mandatoryLabel}</span>
      ) : null}
    </div>
  )
})
