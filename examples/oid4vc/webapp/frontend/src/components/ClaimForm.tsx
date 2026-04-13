import { useState } from 'react'
import type { VctClaim } from '../api/client'

interface ClaimFormProps {
  claims: VctClaim[]
  locale?: string
  onSubmit: (values: Record<string, string>) => void
  submitLabel?: string
  heading?: string
}

const DEFAULTS: Record<string, string> = {
  given_name: 'Jan',
  family_name: 'De Vries',
  email: 'jan.devries@example.com',
  birth_date: '1985-03-15',
  age_over_18: 'true',
  nationality: 'NL',
  resident_country: 'NL',
  resident_city: 'Amsterdam',
  resident_postal_code: '1012 AB',
  resident_street: 'Keizersgracht 123',
  issuing_authority: 'RvIG',
  issuing_country: 'NL',
  document_number: 'SPECI2025',
  gender: 'M',
  birth_place: 'Utrecht',
}

export function ClaimForm({ claims, locale = 'en-US', onSubmit, submitLabel = 'Issue Credential', heading = 'Credential Claims' }: ClaimFormProps) {
  const [values, setValues] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {}
    for (const claim of claims) {
      const key = claim.path.join('.')
      initial[key] = DEFAULTS[key] ?? ''
    }
    return initial
  })

  const getLabel = (claim: VctClaim): string => {
    const match = claim.display?.find(d => d.locale === locale) ?? claim.display?.[0]
    return match?.label ?? claim.path.join('.')
  }

  const getDescription = (claim: VctClaim): string | undefined => {
    const match = claim.display?.find(d => d.locale === locale) ?? claim.display?.[0]
    return match?.description
  }

  return (
    <form className="claim-form" onSubmit={e => { e.preventDefault(); onSubmit(values) }}>
      <h3>{heading}</h3>
      {claims.map(claim => {
        const key = claim.path.join('.')
        const description = getDescription(claim)
        return (
          <div key={key} className="form-field">
            <label htmlFor={key}>
              {getLabel(claim)}
              {claim.mandatory && <span className="required">*</span>}
            </label>
            {description && <p className="field-hint">{description}</p>}
            {(values[key] === 'true' || values[key] === 'false') ? (
              <label className="checkbox-field">
                <input
                  id={key}
                  type="checkbox"
                  checked={values[key] === 'true'}
                  onChange={e => setValues(prev => ({ ...prev, [key]: e.target.checked ? 'true' : 'false' }))}
                />
                {values[key] === 'true' ? 'Yes' : 'No'}
              </label>
            ) : (
            <input
              id={key}
              type={key === 'email' ? 'email' : key.includes('date') ? 'date' : 'text'}
              value={values[key] ?? ''}
              onChange={e => setValues(prev => ({ ...prev, [key]: e.target.value }))}
              required={claim.mandatory}
            />
            )}
          </div>
        )
      })}
      <button type="submit" className="btn btn-primary">{submitLabel}</button>
    </form>
  )
}
