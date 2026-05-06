import { useEffect, useState } from 'react'
import { useLocale } from '../i18n/LocaleContext'

export interface WalletTargetPreset {
  value: string
  label: string
}

interface WalletTargetFieldProps {
  presets: WalletTargetPreset[]
  value: string
  onChange: (next: string) => void
  customPlaceholder?: string
}

const URI_REGEX = /^[a-z][a-z0-9+.-]*:/i
const CUSTOM_SENTINEL = '__custom__'

export function WalletTargetField({ presets, value, onChange, customPlaceholder }: WalletTargetFieldProps) {
  const { t } = useLocale()
  const matchesPreset = (v: string) => presets.some(p => p.value === v)

  const [selectValue, setSelectValue] = useState<string>(matchesPreset(value) ? value : CUSTOM_SENTINEL)
  const [customDraft, setCustomDraft] = useState<string>(matchesPreset(value) ? '' : value)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (matchesPreset(value)) {
      setSelectValue(value)
    } else {
      setSelectValue(CUSTOM_SENTINEL)
      setCustomDraft(value)
    }
    setError(null)
  }, [value])

  const handleSelect = (v: string) => {
    setSelectValue(v)
    setError(null)
    if (v !== CUSTOM_SENTINEL) {
      onChange(v)
    } else if (customDraft && URI_REGEX.test(customDraft)) {
      onChange(customDraft)
    }
  }

  const handleCustomChange = (next: string) => {
    setCustomDraft(next)
    if (!next) {
      setError(t('walletTarget.errorEmpty'))
      return
    }
    if (!URI_REGEX.test(next)) {
      setError(t('walletTarget.errorFormat'))
      return
    }
    setError(null)
    onChange(next)
  }

  return (
    <div className="form-field wallet-target-field">
      <label>{t('walletTarget.label')}</label>
      <div className="wallet-target-row">
        <select value={selectValue} onChange={e => handleSelect(e.target.value)}>
          {presets.map(p => (
            <option key={p.value} value={p.value}>{p.label}</option>
          ))}
          <option value={CUSTOM_SENTINEL}>{t('walletTarget.customLabel')}</option>
        </select>
        {selectValue === CUSTOM_SENTINEL && (
          <input
            className="wallet-target-custom"
            type="text"
            placeholder={customPlaceholder ?? t('walletTarget.placeholder')}
            value={customDraft}
            onChange={e => handleCustomChange(e.target.value)}
          />
        )}
      </div>
      {error && <p className="field-error">{error}</p>}
      <p className="field-hint">{t('walletTarget.helpText')}</p>
    </div>
  )
}
