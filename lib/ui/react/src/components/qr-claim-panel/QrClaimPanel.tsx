import {type ChangeEvent, useEffect, useRef, useState} from 'react'
import QRCode from 'react-qr-code'
import {useQrClaimPolling} from '../../headless/useQrClaimPolling'
import {CredentialMiniCard} from './CredentialMiniCard'
import {SuccessIcon, ErrorIcon, PendingIcon} from './StatusIcons'
import {
  type ClaimPollingState,
  type CredentialPreviewItem,
  type QrClaimLabels,
  type QRValueResult,
  type QrRendering,
  type StatusPoller,
} from './types'
import styles from './QrClaimPanel.module.css'

export interface QrClaimPanelProps {
  initialTab: 'qr' | 'url'
  qrValueGenerator: () => Promise<QRValueResult>
  statusPoller?: StatusPoller
  /**
   * Optional hook for the "Open in wallet" CTA on the URL tab. When omitted
   * the button defaults to `window.open(walletUrl, '_blank', 'noopener')` so
   * the recipient lands on the web-wallet in a new tab. Pass an implementation
   * when the host needs custom routing (e.g. SPA navigation).
   */
  onSubmitUrl?: (walletUrl: string) => Promise<void>
  onSuccess?: () => Promise<void>
  /** Optional callback invoked when the user clicks Retry on a terminal error card. */
  onRetry?: () => void
  onClose: () => Promise<void>
  credentials?: CredentialPreviewItem[]
  rendering?: QrRendering
  labels: QrClaimLabels
  className?: string
  mandatoryLabel?: string
}

const URL_REGEX =
  /^(https?:\/\/)(([a-z\d]([a-z\d-]*[a-z\d])?\.)+[a-z]{2,}|localhost|([a-z\d]([a-z\d-]*[a-z\d])?\.local)|\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(:\d+)?(\/[-a-z\d%_.~+]*)*(\?[;&a-z\d%_.~+=-]*)?(#[-a-z\d_]*)?$/i

const COPIED_TIMEOUT_MS = 2000

function buildWalletUrl(walletAddress: string, qrUri: string): string {
  if (!URL_REGEX.test(walletAddress)) {
    throw new Error('Web wallet address must be a valid https:// url')
  }
  const webWalletUrl = new URL(walletAddress)
  const walletParams = new URLSearchParams(webWalletUrl.search)
  const queryStart = qrUri.indexOf('?')
  if (queryStart !== -1) {
    const qrParams = new URLSearchParams(qrUri.substring(queryStart))
    qrParams.forEach((value, key) => {
      walletParams.set(key, value)
    })
  }
  webWalletUrl.search = walletParams.toString()
  return webWalletUrl.toString()
}

function statusLabel(state: ClaimPollingState, labels: QrClaimLabels['status']): string | null {
  switch (state) {
    case 'waiting-scan':
      return labels.waitingScan
    case 'authenticating':
      return labels.authenticating
    case 'issuing':
      return labels.issuing
    case 'issued':
      return labels.issued
    case 'failed':
      return labels.failed
    case 'expired':
      return labels.expired
    default:
      return null
  }
}

/**
 * Display phase the panel renders. Driven by the polling state machine:
 *  - `pick`: caller hasn't connected a wallet yet → show QR / web-URL tabs
 *  - `progress`: wallet retrieved the offer → hide QR (prevents shoulder-scan
 *    after the wallet already has the URI) + show large spinner + status
 *  - `success`: terminal happy path → green check card
 *  - `error`: terminal failure (failed / expired) → error card
 *
 * Mapping deliberately uses the polling state as single source of truth; the
 * tabs/QR never re-appear once the wallet has the URI.
 */
type DisplayPhase = 'pick' | 'progress' | 'success' | 'error'

function displayPhaseFor(state: ClaimPollingState): DisplayPhase {
  switch (state) {
    case 'idle':
    case 'waiting-scan':
      return 'pick'
    case 'authenticating':
    case 'issuing':
      return 'progress'
    case 'issued':
      return 'success'
    case 'failed':
    case 'expired':
      return 'error'
    default:
      return 'pick'
  }
}

export function QrClaimPanel(props: QrClaimPanelProps) {
  const {
    initialTab,
    qrValueGenerator,
    statusPoller,
    onSubmitUrl,
    onSuccess,
    onRetry,
    credentials,
    rendering,
    labels,
    className,
    mandatoryLabel,
  } = props

  const [activeTab, setActiveTab] = useState<'qr' | 'url'>(initialTab)
  const [qrValue, setQrValue] = useState<QRValueResult | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const [walletAddress, setWalletAddress] = useState('')
  const [copied, setCopied] = useState(false)
  const issuedRef = useRef(false)
  const onSuccessRef = useRef(onSuccess)

  useEffect(() => {
    onSuccessRef.current = onSuccess
  }, [onSuccess])

  useEffect(() => {
    let cancelled = false
    if (!qrValue) {
      qrValueGenerator()
        .then((value) => {
          if (!cancelled) setQrValue(value)
        })
        .catch((err: unknown) => {
          if (!cancelled) setError(err instanceof Error ? err : new Error(String(err)))
        })
    }
    return () => {
      cancelled = true
    }
  }, [qrValue, qrValueGenerator])

  // onExpiry only fires when the QR was replaced or the panel unmounted without
  // a successful issuance.
  useEffect(() => {
    if (!qrValue) return
    return () => {
      if (!issuedRef.current && typeof qrValue.onExpiry === 'function') {
        void qrValue.onExpiry(qrValue)
      }
    }
  }, [qrValue])

  const {state: pollingState, detail: pollingDetail} = useQrClaimPolling({
    preAuthorizedCode: qrValue?.preAuthorizedCode,
    statusPoller,
    onTerminal: (state) => {
      if (state === 'issued') {
        issuedRef.current = true
        void onSuccessRef.current?.()
      }
    },
  })

  const isUrlValid = walletAddress.length > 0 && URL_REGEX.test(walletAddress)
  const qrUri = qrValue?.uriValue ?? ''
  const phase = displayPhaseFor(pollingState)

  const handleAddressChange = (e: ChangeEvent<HTMLInputElement>) => {
    setWalletAddress(e.target.value.trim())
    setCopied(false)
  }

  const handleCopyUrl = async () => {
    if (!isUrlValid || !qrUri) return
    try {
      const url = buildWalletUrl(walletAddress, qrUri)
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), COPIED_TIMEOUT_MS)
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)))
    }
  }

  const handleOpenInWallet = async () => {
    try {
      const target =
        activeTab === 'url' && isUrlValid && qrUri ? buildWalletUrl(walletAddress, qrUri) : qrUri
      if (!target) return
      if (onSubmitUrl) {
        await onSubmitUrl(target)
      } else if (typeof window !== 'undefined') {
        // Default: open the wallet in a new tab so the recipient keeps the
        // claim page open as a fallback if the wallet hand-off fails.
        window.open(target, '_blank', 'noopener,noreferrer')
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)))
    }
  }

  const qrSize = rendering?.size ?? 232
  const qrLevel = rendering?.level ?? 'M'

  const statusText = statusLabel(pollingState, labels.status)

  return (
    <div className={`${styles.root} ${className ?? ''}`.trim()} data-phase={phase}>
      {credentials && credentials.length > 0 ? (
        <div className={styles.credentialList} role="list">
          {credentials.map((cred) => (
            <div role="listitem" key={cred.id}>
              <CredentialMiniCard credential={cred} mandatoryLabel={mandatoryLabel} />
            </div>
          ))}
        </div>
      ) : null}

      {phase === 'pick' ? (
        <>
          <div className={styles.tabs} role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'qr'}
              className={`${styles.tab} ${activeTab === 'qr' ? styles.tabActive : ''}`.trim()}
              onClick={() => setActiveTab('qr')}
            >
              {labels.qrTabLabel}
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'url'}
              className={`${styles.tab} ${activeTab === 'url' ? styles.tabActive : ''}`.trim()}
              onClick={() => setActiveTab('url')}
            >
              {labels.urlTabLabel}
            </button>
          </div>

          <div className={styles.panel} role="tabpanel">
            {activeTab === 'qr' ? (
              <div className={styles.qrWrap}>
                {qrUri ? (
                  <div
                    className={styles.qrBox}
                    style={{background: rendering?.bgColor}}
                  >
                    <QRCode
                      value={qrUri}
                      size={qrSize}
                      level={qrLevel}
                      bgColor={rendering?.bgColor ?? 'transparent'}
                      fgColor={rendering?.fgColor ?? 'currentColor'}
                    />
                  </div>
                ) : !error ? (
                  <div className={styles.loading}>
                    <PendingIcon />
                  </div>
                ) : null}
              </div>
            ) : (
              <div className={styles.urlWrap}>
                {labels.walletUrlLabel ? (
                  <label className={styles.urlLabel}>{labels.walletUrlLabel}</label>
                ) : null}
                <input
                  type="url"
                  className={styles.urlInput}
                  placeholder={labels.walletUrlPlaceholder}
                  value={walletAddress}
                  onChange={handleAddressChange}
                />
                {isUrlValid && qrUri ? (
                  <code className={styles.urlPreviewText}>{buildWalletUrl(walletAddress, qrUri)}</code>
                ) : null}
                <div className={styles.urlActions}>
                  <button
                    type="button"
                    className={styles.copyButton}
                    onClick={handleCopyUrl}
                    disabled={!isUrlValid || !qrUri}
                  >
                    {copied ? labels.copied : labels.copyUrl}
                  </button>
                  <button
                    type="button"
                    className={styles.openButton}
                    onClick={handleOpenInWallet}
                    disabled={!isUrlValid || !qrUri}
                  >
                    {labels.openInWallet}
                  </button>
                </div>
              </div>
            )}
          </div>

          {statusText && activeTab === 'qr' ? (
            <div className={styles.status} data-state={pollingState} role="status" aria-live="polite">
              <PendingIcon />
              <span className={styles.statusText}>{statusText}</span>
            </div>
          ) : null}
        </>
      ) : null}

      {phase === 'progress' ? (
        <div className={styles.progressCard} data-state={pollingState} role="status" aria-live="polite">
          <div className={styles.spinner} aria-hidden="true" />
          <div className={styles.progressText}>
            <span className={styles.progressTitle}>{statusText}</span>
            {pollingDetail ? <span className={styles.progressDetail}>{pollingDetail}</span> : null}
          </div>
        </div>
      ) : null}

      {phase === 'success' ? (
        <div className={styles.terminalCard} data-state="issued" role="status" aria-live="polite">
          <SuccessIcon />
          <div className={styles.terminalText}>
            <span className={styles.terminalTitle}>{statusText}</span>
          </div>
        </div>
      ) : null}

      {phase === 'error' ? (
        <div className={styles.terminalCard} data-state={pollingState} role="alert">
          <ErrorIcon />
          <div className={styles.terminalText}>
            <span className={styles.terminalTitle}>{statusText}</span>
            {pollingDetail ? <span className={styles.terminalDetail}>{pollingDetail}</span> : null}
          </div>
          {onRetry ? (
            <button
              type="button"
              className={styles.retryButton}
              onClick={() => onRetry()}
            >
              {labels.retry ?? 'Retry'}
            </button>
          ) : null}
        </div>
      ) : null}

      {error ? (
        <div className={styles.error} role="alert">
          <ErrorIcon />
          <span>{error.message}</span>
        </div>
      ) : null}
    </div>
  )
}
