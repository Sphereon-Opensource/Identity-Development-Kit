import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api, type AuthRequestResponse, type AuthRequestStatus } from '../api/client'
import { CredentialCard } from '../components/CredentialCard'
import { ClaimSelector } from '../components/ClaimSelector'
import { QrCodeDisplay } from '../components/QrCodeDisplay'
import { SessionTracker } from '../components/SessionTracker'
import { useCredentialMetadata } from '../hooks/useCredentialMetadata'
import { useSessionPolling } from '../hooks/useSessionPolling'
import { useLocale } from '../i18n/LocaleContext'

type Step = 'select' | 'configure' | 'present' | 'verified'

const VP_STAGES = [
  'authorization_request_created',
  'authorization_request_retrieved',
  'authorization_response_received',
  'authorization_response_verified',
]

export function VerifierPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { locale, t } = useLocale()
  const { metadata, configs, vctCache, error: metadataError } = useCredentialMetadata()
  const [selectedConfig, setSelectedConfig] = useState<string | null>(searchParams.get('credential'))
  const [selectedClaims, setSelectedClaims] = useState<string[]>([])
  const [authRequest, setAuthRequest] = useState<AuthRequestResponse | null>(null)
  const [step, setStep] = useState<Step>('select')
  const [actionError, setActionError] = useState<string | null>(null)

  const correlationIdRef = useRef('')
  const error = metadataError ?? actionError

  // Auto-select if credential param is present
  useEffect(() => {
    const cred = searchParams.get('credential')
    if (cred && metadata?.credential_configurations_supported[cred]) {
      handleSelectCredential(cred)
    }
  }, [metadata, searchParams])

  const pollFn = useCallback(
    () => api.getAuthRequestStatus(correlationIdRef.current),
    []
  )
  const isTerminal = useCallback(
    (s: AuthRequestStatus) => ['authorization_response_verified', 'error'].includes(s.status),
    []
  )
  const { data: status, error: pollError, start: startPolling } = useSessionPolling(pollFn, isTerminal)

  useEffect(() => {
    if (status?.status === 'authorization_response_verified') setStep('verified')
  }, [status?.status])

  const handleSelectCredential = (configId: string) => {
    setSelectedConfig(configId)
    // Initialize selected claims: all SD claims selected by default, non-SD always included
    const claims = vctCache[configId]?.claims ?? []
    const allPaths = claims.map(c => c.path.join('.'))
    setSelectedClaims(allPaths)
    setStep('configure')
  }

  const handleRequestPresentation = async () => {
    if (!selectedConfig) return
    setActionError(null)

    const config = metadata?.credential_configurations_supported[selectedConfig]
    if (!config) return

    const claims = vctCache[selectedConfig]?.claims ?? []

    // Build claims array: include non-SD claims (always) + selected SD claims
    const requestedClaims = claims
      .filter(c => {
        const key = c.path.join('.')
        if (c.sd !== 'always') return true // non-SD: always include
        return selectedClaims.includes(key) // SD: only if selected
      })
      .map(c => ({ path: c.path }))

    try {
      const meta: Record<string, unknown> = config.doctype
        ? { doctype_value: config.doctype }
        : { vct_values: [config.vct ?? selectedConfig] }

      const result = await api.createAuthRequest({
        client_id: window.location.origin,
        dcql_query: {
          credentials: [{
            id: selectedConfig,
            format: config.format,
            meta,
            claims: requestedClaims,
          }],
        },
        qr_code: { size: 400 },
      })
      correlationIdRef.current = result.correlation_id
      setAuthRequest(result)
      setStep('present')
      startPolling()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e))
    }
  }

  const selectedVct = selectedConfig ? vctCache[selectedConfig] : undefined
  const selectedDisplay = selectedVct?.display?.find(d => d.locale === locale) ?? selectedVct?.display?.[0]
  const verifiedCredential = status?.verified_data?.credential_claims?.[0]

  return (
    <div className="page-content">
      <p className="page-subtitle">{t('verifier.subtitle')}</p>

      {error && <div className="error-banner">{error}</div>}

      {step === 'select' && (
        <section className="credential-grid">
          <h2>{t('verifier.select')}</h2>
          <div className="cards">
            {Object.entries(configs).map(([id, config]) => {
              const display = vctCache[id]?.display?.find(d => d.locale === locale) ?? vctCache[id]?.display?.[0]
              return (
                <CredentialCard
                  key={id}
                  name={id}
                  format={config.format}
                  display={display}
                  onClick={() => handleSelectCredential(id)}
                />
              )
            })}
          </div>
        </section>
      )}

      {step === 'configure' && selectedConfig && (
        <section className="configure-section">
          <CredentialCard
            name={selectedConfig}
            format={configs[selectedConfig]?.format ?? ''}
            display={selectedDisplay}
            selected
          />

          <ClaimSelector
            claims={selectedVct?.claims ?? []}
            selectedPaths={selectedClaims}
            onSelectionChange={setSelectedClaims}
          />

          <div className="configure-actions">
            <button className="btn btn-primary" onClick={handleRequestPresentation}>
              {t('verifier.requestPresentation')}
            </button>
            <button className="btn btn-secondary" onClick={() => setStep('select')}>
              {t('issuer.back')}
            </button>
          </div>
        </section>
      )}

      {step === 'present' && authRequest && (
        <section className="present-section">
          <CredentialCard
            name={selectedConfig ?? ''}
            format={configs[selectedConfig ?? '']?.format ?? ''}
            display={selectedDisplay}
          />

          <QrCodeDisplay
            qrUri={authRequest.qr_uri ?? undefined}
            deepLink={authRequest.request_uri ?? undefined}
          />

          <SessionTracker
            status={status?.status ?? 'authorization_request_created'}
            stages={VP_STAGES}
            error={pollError}
          />
        </section>
      )}

      {step === 'verified' && verifiedCredential && (
        <section className="verified-section">
          <div className="success-icon">&#10003;</div>
          <h2>{t('verifier.verified')}</h2>

          <CredentialCard
            name={selectedConfig ?? ''}
            format={configs[selectedConfig ?? '']?.format ?? ''}
            display={selectedDisplay}
          />

          <div className="verified-claims-list">
            <h3>{t('verifier.verifiedClaims')}</h3>
            {Object.entries(verifiedCredential.claims ?? {}).map(([key, value]) => {
              const meta = selectedVct?.claims?.find(c => c.path.join('.') === key)
              const label = meta?.display?.find(d => d.locale === locale)?.label ?? meta?.display?.[0]?.label ?? key
              return (
                <div key={key} className="verified-claim-row">
                  <span className="verified-claim-label">{label}</span>
                  <span className="verified-claim-value">{String(value)}</span>
                </div>
              )
            })}
          </div>

          <div className="success-actions">
            <button className="btn btn-primary" onClick={() => navigate('/')}>
              {t('verifier.issueCred')}
            </button>
            <button className="btn btn-secondary" onClick={() => {
              setStep('select')
              setAuthRequest(null)
              setSelectedConfig(null)
            }}>
              {t('verifier.verifyAnother')}
            </button>
          </div>
        </section>
      )}
    </div>
  )
}
