import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  api,
  type AuthRequestResponse,
  type AuthRequestStatus,
  type CreateAuthRequestInput,
} from '../api/client'
import { AdvancedOptions } from '../components/AdvancedOptions'
import { CredentialCard } from '../components/CredentialCard'
import { ClaimSelector } from '../components/ClaimSelector'
import { QrCodeDisplay } from '../components/QrCodeDisplay'
import { SessionTracker } from '../components/SessionTracker'
import { WalletTargetField, type WalletTargetPreset } from '../components/WalletTargetField'
import { useCredentialMetadata } from '../hooks/useCredentialMetadata'
import { useLocalStorage } from '../hooks/useLocalStorage'
import { useSessionPolling } from '../hooks/useSessionPolling'
import { useLocale } from '../i18n/LocaleContext'

// Only the spec-compliant OID4VP 1.0 scheme is offered. Other targets (HAIP, web-wallet
// universal links, app links) are entered via the field's "Custom" option.
const VERIFIER_PRESETS: WalletTargetPreset[] = [
  { value: 'openid4vp://', label: 'openid4vp://' },
]
// Spec-compliant default per OpenID4VP 1.0 (Final) — the IANA-registered scheme is `openid4vp`.
const VERIFIER_DEFAULT_TARGET = 'openid4vp://'
const WALLET_TARGET_STORAGE_KEY = 'oid4vc.verifier.walletTarget'

// One-time migration: the original default persisted the non-spec `oid4vp://`. Remap any stored
// legacy value to the spec-compliant `openid4vp://` so existing users are corrected in place
// rather than silently keeping the wrong scheme. Runs at module load, before the hook reads it.
try {
  if (typeof localStorage !== 'undefined' && localStorage.getItem(WALLET_TARGET_STORAGE_KEY) === 'oid4vp://') {
    localStorage.setItem(WALLET_TARGET_STORAGE_KEY, VERIFIER_DEFAULT_TARGET)
  }
} catch {
  /* storage unavailable (SSR / disabled) — nothing to migrate */
}

type RequestUriMethod = 'get' | 'post'
type ResponseMode = 'direct_post' | 'direct_post.jwt'
type ClientIdScheme = 'did:web' | 'x509_san_dns' | 'x509_hash'
type ProfileId = 'haip' | 'x509-hash' | 'x509-san-dns' | 'did-web' | 'custom'

interface ProfilePreset {
  id: ProfileId
  label: string
  walletTarget: string
  requestUriMethod: RequestUriMethod
  responseMode: ResponseMode
  clientIdScheme: ClientIdScheme
}

// Each preset locks the §5.9.3 binding + the request-URI mechanics OIDF conformance plans
// validate. Edit any field and the dropdown auto-flips to `custom`. `haip` covers
// VP1FinalVerifierTestPlanHaip; the three middle presets each map to a non-HAIP plan.
const VERIFIER_PROFILES: ProfilePreset[] = [
  {
    id: 'haip',
    label: 'HAIP (x509_hash + direct_post.jwt + POST)',
    walletTarget: 'haip://',
    requestUriMethod: 'post',
    responseMode: 'direct_post.jwt',
    clientIdScheme: 'x509_hash',
  },
  {
    id: 'x509-hash',
    label: 'x509_hash (cert-pinned)',
    walletTarget: 'openid4vp://',
    requestUriMethod: 'get',
    responseMode: 'direct_post',
    clientIdScheme: 'x509_hash',
  },
  {
    id: 'x509-san-dns',
    label: 'x509_san_dns (DNS-pinned)',
    walletTarget: 'openid4vp://',
    requestUriMethod: 'get',
    responseMode: 'direct_post',
    clientIdScheme: 'x509_san_dns',
  },
  {
    id: 'did-web',
    label: 'did:web (DID-bound)',
    walletTarget: 'openid4vp://',
    requestUriMethod: 'get',
    responseMode: 'direct_post',
    clientIdScheme: 'did:web',
  },
]

type Step = 'select' | 'configure' | 'present' | 'verified' | 'error'

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
  // Credential status policy (default strict: require nothing, reject revoked + suspended).
  const [requireStatus, setRequireStatus] = useState(false)
  const [acceptRevoked, setAcceptRevoked] = useState(false)
  const [acceptSuspended, setAcceptSuspended] = useState(false)
  const [authRequest, setAuthRequest] = useState<AuthRequestResponse | null>(null)
  const [step, setStep] = useState<Step>('select')
  const [actionError, setActionError] = useState<string | null>(null)
  const [walletTarget, setWalletTarget] = useLocalStorage<string>(WALLET_TARGET_STORAGE_KEY, VERIFIER_DEFAULT_TARGET)
  const [requestUriMethod, setRequestUriMethod] = useLocalStorage<RequestUriMethod>('oid4vc.verifier.requestUriMethod', 'get')
  const [responseMode, setResponseMode] = useLocalStorage<ResponseMode>('oid4vc.verifier.responseMode', 'direct_post')
  const [clientIdScheme, setClientIdScheme] = useLocalStorage<ClientIdScheme>('oid4vc.verifier.clientIdScheme', 'x509_hash')
  const [profile, setProfile] = useLocalStorage<ProfileId>('oid4vc.verifier.profile', 'custom')

  // Pre-select profile from server-side compose env on first load (when the user hasn't
  // already locked something else into localStorage).
  useEffect(() => {
    let cancelled = false
    api.getDemoConfig()
      .then(cfg => {
        if (cancelled) return
        const known = VERIFIER_PROFILES.find(p => p.id === cfg.profile)
        if (known && profile === 'custom') {
          applyProfile(known.id)
        }
      })
      .catch(() => { /* config endpoint optional — fall back to localStorage */ })
    return () => { cancelled = true }
  }, [])

  // Apply a preset's values (and pin the dropdown to the preset id).
  function applyProfile(id: ProfileId) {
    if (id === 'custom') {
      setProfile('custom')
      return
    }
    const p = VERIFIER_PROFILES.find(x => x.id === id)
    if (!p) return
    setWalletTarget(p.walletTarget)
    setRequestUriMethod(p.requestUriMethod)
    setResponseMode(p.responseMode)
    setClientIdScheme(p.clientIdScheme)
    setProfile(id)
  }

  // Whenever an individual field diverges from the active preset, demote to `custom` so
  // the dropdown reflects reality.
  function detectCurrentProfile(): ProfileId {
    const match = VERIFIER_PROFILES.find(p =>
      p.walletTarget === walletTarget &&
      p.requestUriMethod === requestUriMethod &&
      p.responseMode === responseMode &&
      p.clientIdScheme === clientIdScheme,
    )
    return match?.id ?? 'custom'
  }
  useEffect(() => {
    const detected = detectCurrentProfile()
    if (detected !== profile) setProfile(detected)
  }, [walletTarget, requestUriMethod, responseMode, clientIdScheme])

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
    else if (status?.status === 'error') setStep('error')
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

      // walletTarget drives the OUTER deeplink prefix the wallet listens on. Two cases:
      //   - bare scheme like `openid4vp://` — strip the trailing `://` and send the scheme name
      //   - full URL like `https://demo.certification.openid.net/.../authorize` — used by web
      //     wallets, universal links, and app links — send verbatim
      // The inner `request_uri` (HTTPS endpoint where the wallet fetches the JAR) falls back
      // to the verifier's configured external base URL.
      const isFullUrl = /^[a-z][a-z0-9+.-]*:\/\/[^?#\s]+/i.test(walletTarget)
      const walletUriScheme = isFullUrl ? walletTarget : walletTarget.replace(/:\/\/?$/, '')

      // Map UI `clientIdScheme` to the backend enum constant name.
      const clientIdSchemeWire =
        clientIdScheme === 'did:web' ? 'DECENTRALIZED_IDENTIFIER'
        : clientIdScheme === 'x509_san_dns' ? 'X509_SAN_DNS'
        : 'X509_HASH'

      const payload: CreateAuthRequestInput = {
        dcql_query: {
          credentials: [{
            id: selectedConfig,
            format: config.format,
            meta,
            claims: requestedClaims,
          }],
        },
        wallet_uri_scheme: walletUriScheme,
        client_id_scheme: clientIdSchemeWire,
        request_uri_method: requestUriMethod,
        response_mode: responseMode,
        // Per-query credential status policy (verifier-side; not part of the wire DCQL). Only sent
        // when it deviates from the strict default, keyed by the DCQL credential query id.
        credential_status_policies:
          requireStatus || acceptRevoked || acceptSuspended
            ? { [selectedConfig]: { requireStatus, acceptRevoked, acceptSuspended } }
            : undefined,
        qr_code: { size: 400 },
      }
      const result = await api.createAuthRequest(payload)
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

          {(vctCache[selectedConfig]?.claims?.length ?? 0) > 0 && (
            <details className="advanced-options claims-section" open>
              <summary>
                {t('verifier.selectClaims')} ({selectedClaims.length}/{vctCache[selectedConfig]?.claims?.length})
              </summary>
              <div className="advanced-options-content">
                <ClaimSelector
                  claims={vctCache[selectedConfig]?.claims ?? []}
                  selectedPaths={selectedClaims}
                  onSelectionChange={setSelectedClaims}
                />
              </div>
            </details>
          )}

          <details className="advanced-options" >
            <summary>{t('verifier.statusPolicy')}</summary>
            <div className="advanced-options-content">
              <p className="field-hint">{t('verifier.statusPolicyHint')}</p>
              <div className="option-group">
                <label>
                  <input type="checkbox" checked={requireStatus} onChange={e => setRequireStatus(e.target.checked)} />
                  {t('verifier.requireStatus')}
                </label>
                <label>
                  <input type="checkbox" checked={acceptRevoked} onChange={e => setAcceptRevoked(e.target.checked)} />
                  {t('verifier.acceptRevoked')}
                </label>
                <label>
                  <input type="checkbox" checked={acceptSuspended} onChange={e => setAcceptSuspended(e.target.checked)} />
                  {t('verifier.acceptSuspended')}
                </label>
              </div>
            </div>
          </details>

          <div className="form-field">
            <label>Profile</label>
            <select value={profile} onChange={e => applyProfile(e.target.value as ProfileId)}>
              {VERIFIER_PROFILES.map(p => (
                <option key={p.id} value={p.id}>{p.label}</option>
              ))}
              <option value="custom">Custom (override below)</option>
            </select>
            <p className="field-hint">
              Pre-fills wallet target, response_mode, request_uri_method, and client_id_scheme
              for the matching OIDF conformance plan. Editing any field below switches to Custom.
            </p>
          </div>

          <WalletTargetField
            presets={VERIFIER_PRESETS}
            value={walletTarget}
            onChange={setWalletTarget}
            customPlaceholder="https://wallet.example.com/oid4vp"
          />

          <div className="option-group">
            <h3>Client ID scheme (OID4VP §5.9.3)</h3>
            <label>
              <input
                type="radio"
                name="client_id_scheme"
                value="x509_hash"
                checked={clientIdScheme === 'x509_hash'}
                onChange={() => setClientIdScheme('x509_hash')}
              />
              x509_hash (cert-pinned, HAIP)
            </label>
            <label>
              <input
                type="radio"
                name="client_id_scheme"
                value="x509_san_dns"
                checked={clientIdScheme === 'x509_san_dns'}
                onChange={() => setClientIdScheme('x509_san_dns')}
              />
              x509_san_dns (SAN dNSName-pinned)
            </label>
            <label>
              <input
                type="radio"
                name="client_id_scheme"
                value="did:web"
                checked={clientIdScheme === 'did:web'}
                onChange={() => setClientIdScheme('did:web')}
              />
              did:web (DID-bound)
            </label>
          </div>

          <AdvancedOptions>
            <div className="option-group">
              <h3>{t('advanced.requestUriMethod')}</h3>
              <label>
                <input
                  type="radio"
                  name="request_uri_method"
                  value="get"
                  checked={requestUriMethod === 'get'}
                  onChange={() => setRequestUriMethod('get')}
                />
                GET
              </label>
              <label>
                <input
                  type="radio"
                  name="request_uri_method"
                  value="post"
                  checked={requestUriMethod === 'post'}
                  onChange={() => setRequestUriMethod('post')}
                />
                POST
              </label>
            </div>

            <div className="option-group">
              <h3>{t('advanced.responseMode')}</h3>
              <label>
                <input
                  type="radio"
                  name="response_mode"
                  value="direct_post"
                  checked={responseMode === 'direct_post'}
                  onChange={() => setResponseMode('direct_post')}
                />
                direct_post
              </label>
              <label>
                <input
                  type="radio"
                  name="response_mode"
                  value="direct_post.jwt"
                  checked={responseMode === 'direct_post.jwt'}
                  onChange={() => setResponseMode('direct_post.jwt')}
                />
                direct_post.jwt
              </label>
            </div>
            {/* TODO(conformance): structured editor for transaction_data (OID4VP 1.0 §5.4) */}
          </AdvancedOptions>

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

          <details className="session-details">
            <summary>{t('details.toggle')}</summary>
            <pre className="session-details-payload">
              {JSON.stringify({ authRequest, status }, null, 2)}
            </pre>
          </details>
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

      {step === 'error' && (
        <section className="error-section">
          <div className="error-icon">&#10007;</div>
          <h2>{t('verifier.failed')}</h2>
          <p className="error-message">{status?.error?.message ?? t('verifier.failedDesc')}</p>
          {status?.error?.code && <p className="field-hint">{status.error.code}</p>}

          <details className="session-details">
            <summary>{t('details.toggle')}</summary>
            <pre className="session-details-payload">
              {JSON.stringify({ authRequest, status }, null, 2)}
            </pre>
          </details>

          <div className="success-actions">
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
