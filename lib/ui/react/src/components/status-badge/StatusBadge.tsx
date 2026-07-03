import type {CSSProperties, ReactNode} from 'react'
import styles from './StatusBadge.module.css'
import {
  type StatusKind,
  STATUS_TOKENS,
  SuccessStatusIcon,
  WarningStatusIcon,
  InfoStatusIcon,
  ErrorStatusIcon,
  NeutralStatusIcon,
} from './status-icons'

export type {StatusKind}

export interface StatusBadgeProps {
  status: StatusKind
  variant?: 'tonal' | 'solid' | 'outline'
  children: ReactNode
  icon?: ReactNode
  className?: string
}

const DEFAULT_ICONS: Record<StatusKind, ReactNode> = {
  success: <SuccessStatusIcon className={styles.icon} />,
  warning: <WarningStatusIcon className={styles.icon} />,
  info: <InfoStatusIcon className={styles.icon} />,
  error: <ErrorStatusIcon className={styles.icon} />,
  neutral: <NeutralStatusIcon className={styles.icon} />,
}

export function StatusBadge({
  status,
  variant = 'tonal',
  children,
  icon,
  className,
}: StatusBadgeProps) {
  const tokens = STATUS_TOKENS[status]
  const cssVars = {
    '--badge-bg': tokens.bg,
    '--badge-text': tokens.text,
    '--badge-strong': tokens.strong,
    '--badge-on-strong': tokens.onStrong,
  } as CSSProperties

  return (
    <span
      className={`${styles.badge} ${styles[variant]} ${className ?? ''}`.trim()}
      data-status={status}
      data-variant={variant}
      style={cssVars}
    >
      <span className={styles.iconCell} aria-hidden="true">
        {icon ?? DEFAULT_ICONS[status]}
      </span>
      <span className={styles.label}>{children}</span>
    </span>
  )
}
