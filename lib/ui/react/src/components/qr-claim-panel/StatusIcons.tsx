import {type SVGProps} from 'react'
import styles from './StatusIcons.module.css'

export interface StatusIconProps extends Omit<SVGProps<SVGSVGElement>, 'children'> {
  className?: string
}

export function SuccessIcon({className, ...rest}: StatusIconProps) {
  return (
    <svg
      {...rest}
      className={`${styles.icon} ${styles.success} ${className ?? ''}`.trim()}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  )
}

export function ErrorIcon({className, ...rest}: StatusIconProps) {
  return (
    <svg
      {...rest}
      className={`${styles.icon} ${styles.error} ${className ?? ''}`.trim()}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <path d="M15 9l-6 6M9 9l6 6" />
    </svg>
  )
}

export function PendingIcon({className, ...rest}: StatusIconProps) {
  return (
    <svg
      {...rest}
      className={`${styles.icon} ${styles.pending} ${styles.spinning} ${className ?? ''}`.trim()}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" opacity="0.25" />
      <path d="M22 12a10 10 0 0 1-10 10" />
    </svg>
  )
}

export function ScanningIcon({className, ...rest}: StatusIconProps) {
  return (
    <svg
      {...rest}
      className={`${styles.icon} ${styles.pending} ${className ?? ''}`.trim()}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
    >
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <path d="M14 14h7v7" />
    </svg>
  )
}
