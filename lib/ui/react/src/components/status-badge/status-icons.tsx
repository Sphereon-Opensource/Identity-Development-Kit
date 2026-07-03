import type {SVGProps} from 'react'

export type StatusIconSVGProps = SVGProps<SVGSVGElement>

export function SuccessStatusIcon(props: StatusIconSVGProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d="M3 8.5 6.5 12 13 4.5" />
    </svg>
  )
}

export function WarningStatusIcon(props: StatusIconSVGProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d="M8 2 1.5 14h13L8 2Z" />
      <path d="M8 6.5v3.5" />
      <path d="M8 12h.01" strokeWidth="2" />
    </svg>
  )
}

export function InfoStatusIcon(props: StatusIconSVGProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <circle cx="8" cy="8" r="6.3" />
      <path d="M8 7.3v4" />
      <path d="M8 5h.01" strokeWidth="2" />
    </svg>
  )
}

export function ErrorStatusIcon(props: StatusIconSVGProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <circle cx="8" cy="8" r="6.3" />
      <path d="M8 5v4" />
      <path d="M8 11h.01" strokeWidth="2" />
    </svg>
  )
}

export function NeutralStatusIcon(props: StatusIconSVGProps) {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
      {...props}
    >
      <circle cx="8" cy="8" r="3" />
    </svg>
  )
}

export type StatusKind = 'success' | 'warning' | 'info' | 'error' | 'neutral'

export interface StatusTokens {
  bg: string
  text: string
  strong: string
  onStrong: string
}

export const STATUS_TOKENS: Record<StatusKind, StatusTokens> = {
  success: {
    bg: 'var(--color-feedback-success-container)',
    text: 'var(--color-feedback-on-success-container)',
    strong: 'var(--color-feedback-success)',
    onStrong: 'var(--color-feedback-on-success)',
  },
  warning: {
    bg: 'var(--color-feedback-warning-container)',
    text: 'var(--color-feedback-on-warning-container)',
    strong: 'var(--color-feedback-warning)',
    onStrong: 'var(--color-feedback-on-warning)',
  },
  info: {
    bg: 'var(--color-feedback-info-container)',
    text: 'var(--color-feedback-on-info-container)',
    strong: 'var(--color-feedback-info)',
    onStrong: 'var(--color-feedback-on-info)',
  },
  error: {
    bg: 'var(--color-error-container)',
    text: 'var(--color-on-error-container)',
    strong: 'var(--color-error)',
    onStrong: 'var(--color-on-error)',
  },
  neutral: {
    bg: 'var(--color-surface-variant)',
    text: 'var(--color-text-secondary)',
    strong: 'var(--color-text-secondary)',
    onStrong: 'var(--color-text-secondary)',
  },
}
