import { useLocale } from '../i18n/LocaleContext'

interface QrCodeDisplayProps {
  qrUri?: string
  deepLink?: string
  pinCode?: string
}

export function QrCodeDisplay({ qrUri, deepLink, pinCode }: QrCodeDisplayProps) {
  const { t } = useLocale()

  return (
    <div className="qr-section">
      {qrUri && (
        <div className="qr-code">
          <img src={qrUri} alt="QR" />
        </div>
      )}

      {deepLink && (
        <div className="deeplink-section">
          <a
            href={deepLink}
            className="btn btn-primary deeplink-btn"
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

      <p className="qr-hint">{t('qr.scanHint')}</p>
    </div>
  )
}
