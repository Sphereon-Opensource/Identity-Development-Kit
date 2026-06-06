import type { ReactNode } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { IssuerPage } from './pages/IssuerPage'
import { VerifierPage } from './pages/VerifierPage'
import { TrustAnchorPage } from './pages/TrustAnchorPage'
import { LocaleProvider, useLocale } from './i18n/LocaleContext'
import { SUPPORTED_LOCALES, LOCALE_LABELS } from './i18n/translations'

function Layout({ children }: { children: ReactNode }) {
  const location = useLocation()
  const navigate = useNavigate()
  const isIssuer = location.pathname.startsWith('/issuer') || location.pathname === '/'
  const isVerifier = location.pathname.startsWith('/verifier')
  const isTrustAnchor = location.pathname.startsWith('/trust-anchor')
  const { locale, setLocale, t } = useLocale()

  return (
    <div className="app-layout">
      <div className="app-header">
        <img src="/assets/sphereon-logo.png" alt="Sphereon" className="app-logo" />
        <select
          className="locale-selector"
          value={locale}
          onChange={e => setLocale(e.target.value as typeof locale)}
        >
          {SUPPORTED_LOCALES.map(l => (
            <option key={l} value={l}>{LOCALE_LABELS[l]}</option>
          ))}
        </select>
      </div>
      <nav className="tab-bar">
        <button
          className={`tab ${isIssuer ? 'active' : ''}`}
          onClick={() => navigate('/issuer')}
        >
          {t('tab.issue')}
        </button>
        <button
          className={`tab ${isVerifier ? 'active' : ''}`}
          onClick={() => navigate('/verifier')}
        >
          {t('tab.verify')}
        </button>
        <button
          className={`tab ${isTrustAnchor ? 'active' : ''}`}
          onClick={() => navigate('/trust-anchor')}
        >
          {t('tab.trustAnchor')}
        </button>
      </nav>
      <div className="page">
        {children}
      </div>
    </div>
  )
}

export function App() {
  return (
    <LocaleProvider>
      <BrowserRouter basename="/webapp">
        <Layout>
          <Routes>
            <Route path="/issuer" element={<IssuerPage />} />
            <Route path="/verifier" element={<VerifierPage />} />
            <Route path="/trust-anchor" element={<TrustAnchorPage />} />
            <Route path="*" element={<Navigate to="/issuer" replace />} />
          </Routes>
        </Layout>
      </BrowserRouter>
    </LocaleProvider>
  )
}
