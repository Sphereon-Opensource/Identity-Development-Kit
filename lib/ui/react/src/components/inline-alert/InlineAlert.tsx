import type {CSSProperties, ReactNode} from 'react'
import styles from './InlineAlert.module.css'
import {
  type StatusKind,
  STATUS_TOKENS,
  SuccessStatusIcon,
  WarningStatusIcon,
  InfoStatusIcon,
  ErrorStatusIcon,
  NeutralStatusIcon,
} from '../status-badge/status-icons'

export type {StatusKind}

export interface InlineAlertProps {
  status: StatusKind
  title?: string
  children?: ReactNode
  icon?: ReactNode
  onDismiss?: () => void
  className?: string
}

const DEFAULT_ICONS: Record<StatusKind, ReactNode> = {
  success: <SuccessStatusIcon className={styles.icon} />,
  warning: <WarningStatusIcon className={styles.icon} />,
  info: <InfoStatusIcon className={styles.icon} />,
  error: <ErrorStatusIcon className={styles.icon} />,
  neutral: <NeutralStatusIcon className={styles.icon} />,
}

export function InlineAlert({
  status,
  title,
  children,
  icon,
  onDismiss,
  className,
}: InlineAlertProps) {
  const tokens = STATUS_TOKENS[status]
  const cssVars = {
    '--badge-bg': tokens.bg,
    '--badge-text': tokens.text,
    '--badge-strong': tokens.strong,
  } as CSSProperties

  const role = status === 'error' ? 'alert' : 'status'

  return (
    <div
      className={`${styles.alert} ${className ?? ''}`.trim()}
      role={role}
      style={cssVars}
    >
      <span className={styles.iconWrapper} aria-hidden="true">
        {icon ?? DEFAULT_ICONS[status]}
      </span>
      <div className={styles.content}>
        {title != null ? <p className={styles.title}>{title}</p> : null}
        {children != null ? <div className={styles.message}>{children}</div> : null}
      </div>
      {onDismiss != null ? (
        <button
          type="button"
          className={styles.dismiss}
          aria-label="Dismiss"
          onClick={onDismiss}
        >
          ×
        </button>
      ) : null}
    </div>
  )
}
