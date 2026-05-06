import { useLocale } from '../i18n/LocaleContext'

interface QrCodeDisplayProps {
  qrUri?: string
  deepLink?: string
  pinCode?: string
}

export function QrCodeDisplay({ qrUri, deepLink, pinCode }: QrCodeDisplayProps) {
  const { t } = useLocale()

  // QR codes only make sense when the deeplink is a custom-scheme URL the user has to scan
  // from a phone wallet (e.g. `openid-credential-offer://`, `haip://`, `oid4vp://`). When the
  // wallet target is an HTTPS universal-link / app-link the user is already on a browser and
  // can just click the button — printing a QR adds no value and clutters the layout.
  const isWebDeeplink = !!deepLink && /^https?:\/\//i.test(deepLink)
  const showQr = !!qrUri && !isWebDeeplink

  return (
    <div className="qr-section">
      {showQr && (
        <div className="qr-code">
          <img src={qrUri} alt="QR" />
        </div>
      )}

      {deepLink && (
        <div className="deeplink-section">
          <a
            href={deepLink}
            className="btn btn-primary deeplink-btn"
            // Wallet links navigate the current tab to the wallet (custom-scheme handler or
            // HTTPS app-link). Open in a new tab so the demo session — including the polling
            // status the user is watching — stays visible behind the wallet redirect.
            target="_blank"
            rel="noopener noreferrer"
          >
            {t('qr.openWallet')}
          </a>
          <button
            className="btn btn-secondary"
            onClick={() => navigator.clipboard.writeText(deepLink)}
          >
            {t('qr.copyLink')}
          </button>
        </div>
      )}

      {pinCode && (
        <div className="pin-section">
          <p className="pin-label">{t('qr.pinLabel')}</p>
          <span className="pin-code">{pinCode}</span>
        </div>
      )}

      {showQr && <p className="qr-hint">{t('qr.scanHint')}</p>}
    </div>
  )
}
