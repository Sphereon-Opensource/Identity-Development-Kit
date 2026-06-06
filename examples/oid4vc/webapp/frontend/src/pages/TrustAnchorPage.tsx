import { useState, useEffect } from 'react'
import QRCode from 'qrcode'
import { QrCodeDisplay } from '../components/QrCodeDisplay'
import { IDK_E2E_CA_PEM, IDK_E2E_CA_INFO, sha256Fingerprint } from '../api/trustAnchor'
import { api, DEFAULT_STATUS_LIST_ID, type StatusListEntryStatus } from '../api/client'
import { useLocale } from '../i18n/LocaleContext'

export function TrustAnchorPage() {
  const { t } = useLocale()
  const [qrUri, setQrUri] = useState<string | undefined>(undefined)
  const [showQr, setShowQr] = useState(false)
  const [copied, setCopied] = useState(false)
  const [fingerprint, setFingerprint] = useState<string>('')

  // Status-list lookup / revoke
  const [statusIndex, setStatusIndex] = useState('')
  const [statusResult, setStatusResult] = useState<StatusListEntryStatus | null>(null)
  const [statusBusy, setStatusBusy] = useState(false)
  const [revokeBusy, setRevokeBusy] = useState(false)
  const [statusError, setStatusError] = useState<string>('')
  const [confirmClear, setConfirmClear] = useState(false)
  const [clearBusy, setClearBusy] = useState(false)
  const [cleared, setCleared] = useState(false)

  const parsedIndex = (): number | null => {
    const n = Number(statusIndex.trim())
    return Number.isInteger(n) && n >= 0 ? n : null
  }

  const handleCheckStatus = async () => {
    const idx = parsedIndex()
    if (idx === null) {
      setStatusError(t('trustAnchor.statusIndexInvalid'))
      return
    }
    setStatusBusy(true)
    setStatusError('')
    setStatusResult(null)
    try {
      setStatusResult(await api.getStatusListEntry(DEFAULT_STATUS_LIST_ID, idx))
    } catch (e) {
      setStatusError(e instanceof Error ? e.message : String(e))
    } finally {
      setStatusBusy(false)
    }
  }

  const handleRevoke = async () => {
    const idx = parsedIndex()
    if (idx === null) return
    setRevokeBusy(true)
    setStatusError('')
    try {
      setStatusResult(await api.revokeStatusListEntry(DEFAULT_STATUS_LIST_ID, idx))
    } catch (e) {
      setStatusError(e instanceof Error ? e.message : String(e))
    } finally {
      setRevokeBusy(false)
    }
  }

  const handleClear = async () => {
    if (!confirmClear) {
      setConfirmClear(true)
      return
    }
    setClearBusy(true)
    setStatusError('')
    setCleared(false)
    try {
      await api.clearStatusList(DEFAULT_STATUS_LIST_ID)
      setConfirmClear(false)
      setCleared(true)
      // The previously shown result is now stale (everything is valid again).
      setStatusResult(null)
    } catch (e) {
      setStatusError(e instanceof Error ? e.message : String(e))
    } finally {
      setClearBusy(false)
    }
  }

  const statusLabel = (r: StatusListEntryStatus): string => {
    if (r.status === 'REVOKED') return t('trustAnchor.statusRevoked')
    if (r.status === 'SUSPENDED') return t('trustAnchor.statusSuspended')
    if (r.status === 'VALID') return t('trustAnchor.statusValid')
    return r.status
  }

  const statusColor = (r: StatusListEntryStatus): string => {
    if (r.status === 'REVOKED') return 'var(--color-error)'
    if (r.status === 'SUSPENDED') return 'var(--color-warning, #b8860b)'
    return 'var(--color-success)'
  }

  useEffect(() => {
    QRCode.toDataURL(IDK_E2E_CA_PEM, { width: 320, errorCorrectionLevel: 'M' })
      .then(setQrUri)
      .catch(() => setQrUri(undefined))
    sha256Fingerprint(IDK_E2E_CA_PEM)
      .then(setFingerprint)
      .catch(() => setFingerprint(''))
  }, [])

  const handleCopy = () => {
    navigator.clipboard.writeText(IDK_E2E_CA_PEM).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  const handleDownload = () => {
    const blob = new Blob([IDK_E2E_CA_PEM], { type: 'application/x-pem-file' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'idk-e2e-ca.pem'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="page-content">
      <p className="page-subtitle">{t('trustAnchor.description')}</p>

      <section className="configure-section">
        <h2>{t('trustAnchor.details')}</h2>
        <div className="verified-claim-row">
          <span className="verified-claim-label">{t('trustAnchor.subject')}</span>
          <span className="verified-claim-value">{IDK_E2E_CA_INFO.subject}</span>
        </div>
        <div className="verified-claim-row">
          <span className="verified-claim-label">{t('trustAnchor.type')}</span>
          <span className="verified-claim-value">{IDK_E2E_CA_INFO.type}</span>
        </div>
        <div className="verified-claim-row">
          <span className="verified-claim-label">{t('trustAnchor.fingerprint')}</span>
          <span className="verified-claim-value" style={{ wordBreak: 'break-all', textAlign: 'right' }}>
            {fingerprint || '…'}
          </span>
        </div>
      </section>

      <section className="configure-section">
        <h2>{t('trustAnchor.caLabel')}</h2>
        <div className="deeplink-section">
          <button className="btn btn-secondary" onClick={handleCopy}>
            {copied ? t('trustAnchor.copied') : t('trustAnchor.copy')}
          </button>
          <button className="btn btn-secondary" onClick={handleDownload}>
            {t('trustAnchor.download')}
          </button>
          <button className="btn btn-secondary" onClick={() => setShowQr((v) => !v)}>
            {showQr ? t('trustAnchor.hideQr') : t('trustAnchor.showQr')}
          </button>
        </div>

        {showQr && (
          <section className="offer-section">
            <QrCodeDisplay qrUri={qrUri} />
            <p className="qr-hint">{t('trustAnchor.qrNote')}</p>
          </section>
        )}

        <pre className="session-details-payload">{IDK_E2E_CA_PEM}</pre>
      </section>

      <section className="configure-section">
        <h2>{t('trustAnchor.statusListTitle')}</h2>
        <p className="field-hint">{t('trustAnchor.statusListDescription')}</p>

        <div className="form-field">
          <label htmlFor="status-index">{t('trustAnchor.statusIndexLabel')}</label>
          <div className="field-hint">{t('trustAnchor.statusIndexHint')}</div>
          <input
            id="status-index"
            type="number"
            min={0}
            inputMode="numeric"
            placeholder={t('trustAnchor.statusIndexPlaceholder')}
            value={statusIndex}
            onChange={(e) => setStatusIndex(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleCheckStatus()
            }}
          />
        </div>

        <div className="deeplink-section">
          <button className="btn btn-primary" onClick={handleCheckStatus} disabled={statusBusy || statusIndex.trim() === ''}>
            {statusBusy ? t('trustAnchor.checking') : t('trustAnchor.checkStatus')}
          </button>
        </div>

        {statusResult && (
          <>
            <div className="verified-claim-row">
              <span className="verified-claim-label">{t('trustAnchor.statusResultLabel')}</span>
              <span className="verified-claim-value" style={{ color: statusColor(statusResult), fontWeight: 600 }}>
                {statusLabel(statusResult)}
              </span>
            </div>
            <div className="verified-claim-row">
              <span className="verified-claim-label">{t('trustAnchor.statusIndexLabel')}</span>
              <span className="verified-claim-value">{statusResult.index}</span>
            </div>

            {!statusResult.revoked && (
              <div className="deeplink-section">
                <button className="btn btn-secondary" onClick={handleRevoke} disabled={revokeBusy}>
                  {revokeBusy ? t('trustAnchor.revoking') : t('trustAnchor.revoke')}
                </button>
              </div>
            )}
          </>
        )}

        {statusError && <p className="error-message">{statusError}</p>}

        <div style={{ marginTop: 20, borderTop: '1px solid var(--color-border)', paddingTop: 16 }}>
          <div className="deeplink-section">
            {!confirmClear ? (
              <button className="btn btn-secondary" onClick={handleClear} disabled={clearBusy}>
                {t('trustAnchor.clearAll')}
              </button>
            ) : (
              <>
                <button className="btn btn-secondary" onClick={() => setConfirmClear(false)} disabled={clearBusy}>
                  {t('trustAnchor.cancel')}
                </button>
                <button
                  className="btn btn-primary"
                  onClick={handleClear}
                  disabled={clearBusy}
                  style={{ background: 'var(--color-error)' }}
                >
                  {clearBusy ? t('trustAnchor.clearing') : t('trustAnchor.confirmClear')}
                </button>
              </>
            )}
          </div>
          {cleared && (
            <p className="field-hint" style={{ textAlign: 'center', marginTop: 8, color: 'var(--color-success)' }}>
              {t('trustAnchor.cleared')}
            </p>
          )}
          <p className="field-hint" style={{ marginTop: 12 }}>
            {t('trustAnchor.statusListNotPersistent')}
          </p>
        </div>
      </section>
    </div>
  )
}
