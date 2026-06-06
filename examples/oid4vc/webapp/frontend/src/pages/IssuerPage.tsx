import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type OfferResponse, type OfferStatus } from '../api/client'
import { CredentialCard } from '../components/CredentialCard'
import { ClaimForm } from '../components/ClaimForm'
import { QrCodeDisplay } from '../components/QrCodeDisplay'
import { SessionTracker } from '../components/SessionTracker'
import { WalletTargetField, type WalletTargetPreset } from '../components/WalletTargetField'
import { useCredentialMetadata } from '../hooks/useCredentialMetadata'
import { useLocalStorage } from '../hooks/useLocalStorage'
import { useSessionPolling } from '../hooks/useSessionPolling'
import { useLocale } from '../i18n/LocaleContext'

const ISSUER_PRESETS: WalletTargetPreset[] = [
  { value: 'openid-credential-offer://', label: 'openid-credential-offer://' },
  { value: 'haip://', label: 'haip://' },
]
const ISSUER_DEFAULT_TARGET = 'openid-credential-offer://'

type Step = 'select' | 'configure' | 'offer' | 'success' | 'error'

const OFFER_STAGES = [
  'credential_offer_created',
  'credential_offer_retrieved',
  'token_requested',
  'credential_requested',
  'credential_issued',
]

export function IssuerPage() {
  const navigate = useNavigate()
  const { locale, t } = useLocale()
  const { configs, vctCache, error: metadataError } = useCredentialMetadata()
  const [selectedConfig, setSelectedConfig] = useState<string | null>(null)
  const [grantType, setGrantType] = useState<'pre-auth' | 'auth-code'>('pre-auth')
  const [usePin, setUsePin] = useState(false)
  const [offer, setOffer] = useState<OfferResponse | null>(null)
  const [step, setStep] = useState<Step>('select')
  const [actionError, setActionError] = useState<string | null>(null)
  const [walletTarget, setWalletTarget] = useLocalStorage<string>('oid4vc.issuer.walletTarget', ISSUER_DEFAULT_TARGET)

  const correlationIdRef = useRef('')
  const error = metadataError ?? actionError

  const pollFn = useCallback(
    () => api.getOfferStatus(correlationIdRef.current),
    []
  )
  const isTerminal = useCallback(
    (s: OfferStatus) => ['credential_issued', 'error'].includes(s.status),
    []
  )
  const { data: status, error: pollError, start: startPolling } = useSessionPolling(pollFn, isTerminal)

  useEffect(() => {
    if (status?.status === 'credential_issued') setStep('success')
    else if (status?.status === 'error') setStep('error')
  }, [status?.status])

  const handleSelectCredential = (configId: string) => {
    setSelectedConfig(configId)
    setStep('configure')
  }

  const handleIssueClaims = async (claimValues: Record<string, string>) => {
    if (!selectedConfig) return
    setActionError(null)

    const grants: Record<string, unknown> = {}
    if (grantType === 'pre-auth') {
      const preAuth: Record<string, unknown> = {}
      if (usePin) {
        preAuth.tx_code = { input_mode: 'numeric', length: 6 }
      }
      grants['pre_authorized_code'] = preAuth
    } else {
      grants.authorization_code = { issuer_state: `demo-${Date.now()}` }
    }

    try {
      const result = await api.createOffer({
        credential_configuration_ids: [selectedConfig],
        credential_subject_data: claimValues,
        grants,
        scheme: walletTarget,
        qr_code: { size: 400 },
      })
      correlationIdRef.current = result.correlation_id
      setOffer(result)
      setStep('offer')
      startPolling()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e))
    }
  }

  const selectedVct = selectedConfig ? vctCache[selectedConfig] : undefined
  const selectedDisplay = selectedVct?.display?.find(d => d.locale === locale) ?? selectedVct?.display?.[0]

  return (
    <div className="page-content">
      <p className="page-subtitle">{t('issuer.subtitle')}</p>

      {error && <div className="error-banner">{error}</div>}

      {step === 'select' && (
        <section className="credential-grid">
          <h2>{t('issuer.select')}</h2>
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
          <WalletTargetField
            presets={ISSUER_PRESETS}
            value={walletTarget}
            onChange={setWalletTarget}
            customPlaceholder="https://wallet.example.com/credential_offer"
          />
          <div className="grant-selection">
            <div className="grant-selection-header">
              <div className="grant-options">
                <h3>{t('issuer.grantType')}</h3>
                <label>
                  <input type="radio" value="pre-auth" checked={grantType === 'pre-auth'}
                    onChange={() => setGrantType('pre-auth')} />
                  {t('issuer.preAuth')}
                </label>
                {grantType === 'pre-auth' && (
                  <label className="pin-option">
                    <input type="checkbox" checked={usePin} onChange={e => setUsePin(e.target.checked)} />
                    {t('issuer.requirePin')}
                  </label>
                )}
                <label>
                  <input type="radio" value="auth-code" checked={grantType === 'auth-code'}
                    onChange={() => setGrantType('auth-code')} />
                  {t('issuer.authCode')}
                </label>
                {grantType === 'auth-code' && (
                  <p className="field-hint">{t('issuer.demoLogin')}</p>
                )}
              </div>
              <CredentialCard
                name={selectedConfig}
                format={configs[selectedConfig]?.format ?? ''}
                display={selectedDisplay}
                selected
              />
            </div>
          </div>

          <ClaimForm
            claims={selectedVct?.claims ?? []}
            locale={locale}
            onSubmit={handleIssueClaims}
            submitLabel={t('issuer.issueCred')}
            heading={t('issuer.claims')}
          />

          <button className="btn btn-secondary" onClick={() => setStep('select')}>{t('issuer.back')}</button>
        </section>
      )}

      {step === 'offer' && offer && (
        <section className="offer-section">
          <CredentialCard
            name={selectedConfig ?? ''}
            format={configs[selectedConfig ?? '']?.format ?? ''}
            display={selectedDisplay}
          />

          <QrCodeDisplay
            qrUri={offer.qr_uri ?? undefined}
            deepLink={offer.offer_uri}
            pinCode={offer.tx_code ?? undefined}
          />

          <SessionTracker
            status={status?.status ?? 'credential_offer_created'}
            stages={OFFER_STAGES}
            error={pollError}
          />

          <details className="session-details">
            <summary>{t('details.toggle')}</summary>
            <pre className="session-details-payload">
              {JSON.stringify({ offer, status }, null, 2)}
            </pre>
          </details>
        </section>
      )}

      {step === 'success' && (
        <section className="success-section">
          <div className="success-icon">&#10003;</div>
          <h2>{t('issuer.success')}</h2>
          <p>{t('issuer.successDesc')}</p>
          <div className="success-actions">
            <button className="btn btn-primary" onClick={() => {
              navigate(`/verifier?credential=${selectedConfig}`)
            }}>
              {t('issuer.verifyThis')}
            </button>
            <button className="btn btn-secondary" onClick={() => {
              setStep('select')
              setOffer(null)
              setSelectedConfig(null)
            }}>
              {t('issuer.issueAnother')}
            </button>
          </div>
        </section>
      )}

      {step === 'error' && (
        <section className="error-section">
          <div className="error-icon">&#10007;</div>
          <h2>{t('issuer.failed')}</h2>
          <p className="error-message">{status?.error?.message ?? t('issuer.failedDesc')}</p>
          {status?.error?.code && <p className="field-hint">{status.error.code}</p>}

          <details className="session-details">
            <summary>{t('details.toggle')}</summary>
            <pre className="session-details-payload">
              {JSON.stringify({ offer, status }, null, 2)}
            </pre>
          </details>

          <div className="success-actions">
            <button className="btn btn-secondary" onClick={() => {
              setStep('select')
              setOffer(null)
              setSelectedConfig(null)
            }}>
              {t('issuer.issueAnother')}
            </button>
          </div>
        </section>
      )}
    </div>
  )
}
