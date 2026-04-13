import { type ReactNode, createContext, useContext, useState, useCallback } from 'react'
import { translations, SUPPORTED_LOCALES, type SupportedLocale } from './translations'

interface LocaleContextType {
  locale: SupportedLocale
  setLocale: (locale: SupportedLocale) => void
  t: (key: string) => string
}

const LocaleContext = createContext<LocaleContextType>({
  locale: 'en-US',
  setLocale: () => {},
  t: (key) => key,
})

function detectLocale(): SupportedLocale {
  const browserLang = navigator.language
  const match = SUPPORTED_LOCALES.find(l => browserLang.startsWith(l.split('-')[0]))
  return match ?? 'en-US'
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<SupportedLocale>(detectLocale)

  const t = useCallback((key: string): string => {
    return translations[locale]?.[key] ?? translations['en-US']?.[key] ?? key
  }, [locale])

  return (
    <LocaleContext.Provider value={{ locale, setLocale, t }}>
      {children}
    </LocaleContext.Provider>
  )
}

export function useLocale() {
  return useContext(LocaleContext)
}
