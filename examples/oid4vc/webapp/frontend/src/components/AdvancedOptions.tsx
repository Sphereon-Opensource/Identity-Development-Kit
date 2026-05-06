import { useState, type ReactNode } from 'react'
import { useLocale } from '../i18n/LocaleContext'

interface AdvancedOptionsProps {
  children: ReactNode
}

export function AdvancedOptions({ children }: AdvancedOptionsProps) {
  const { t } = useLocale()
  const [open, setOpen] = useState(false)
  return (
    <details
      className="advanced-options"
      open={open}
      onToggle={e => setOpen((e.target as HTMLDetailsElement).open)}
    >
      <summary>{t('advanced.toggle')}</summary>
      <div className="advanced-options-content">{children}</div>
    </details>
  )
}
