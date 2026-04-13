import type { VctClaim } from '../api/client'
import { useLocale } from '../i18n/LocaleContext'

interface ClaimSelectorProps {
  claims: VctClaim[]
  selectedPaths: string[]
  onSelectionChange: (paths: string[]) => void
}

export function ClaimSelector({ claims, selectedPaths, onSelectionChange }: ClaimSelectorProps) {
  const { locale, t } = useLocale()

  const getLabel = (claim: VctClaim): string => {
    const match = claim.display?.find(d => d.locale === locale) ?? claim.display?.[0]
    return match?.label ?? claim.path.join('.')
  }

  const toggle = (key: string) => {
    if (selectedPaths.includes(key)) {
      onSelectionChange(selectedPaths.filter(p => p !== key))
    } else {
      onSelectionChange([...selectedPaths, key])
    }
  }

  return (
    <div className="claim-selector">
      <h3>{t('verifier.selectClaims')}</h3>
      <p className="claim-selector-hint">{t('verifier.sdHint')}</p>
      <div className="claim-selector-list">
        {claims.map(claim => {
          const key = claim.path.join('.')
          const isSD = claim.sd === 'always'
          const isSelected = selectedPaths.includes(key)

          return (
            <label key={key} className={`claim-selector-row ${!isSD ? 'locked' : ''}`}>
              <input
                type="checkbox"
                checked={isSD ? isSelected : true}
                disabled={!isSD}
                onChange={() => isSD && toggle(key)}
              />
              <span className="claim-selector-label">{getLabel(claim)}</span>
              {!isSD && <span className="claim-selector-badge">{t('verifier.alwaysIncluded')}</span>}
            </label>
          )
        })}
      </div>
    </div>
  )
}
