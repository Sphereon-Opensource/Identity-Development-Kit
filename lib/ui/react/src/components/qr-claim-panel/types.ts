export interface QRValueResult {
  id: string
  uriValue: string
  preAuthorizedCode?: string
  expiryInSec?: number
  onExpiry: (expired: QRValueResult) => Promise<void>
}

export type ClaimPollingState =
  | 'idle'
  | 'waiting-scan'
  | 'authenticating'
  | 'issuing'
  | 'issued'
  | 'failed'
  | 'expired'

export interface ClaimPollingResult {
  state: ClaimPollingState
  detail?: string
}

export type StatusPoller = (args: {
  preAuthorizedCode: string
  signal: AbortSignal
}) => AsyncIterable<ClaimPollingResult>

export interface CredentialPreviewItem {
  id: string
  displayName: string
  type?: string
  logoUrl?: string
  mandatory?: boolean
}

export interface QrRendering {
  size?: number
  fgColor?: string
  bgColor?: string
  level?: 'L' | 'M' | 'Q' | 'H'
}

export interface QrClaimStatusLabels {
  waitingScan: string
  authenticating: string
  issuing: string
  issued: string
  failed: string
  expired: string
}

export interface QrClaimLabels {
  qrTabLabel: string
  urlTabLabel: string
  walletUrlPlaceholder: string
  /** Helper label shown above the URL input. */
  walletUrlLabel?: string
  openInWallet: string
  copyUrl: string
  copied: string
  /** Label for the retry button shown on terminal error states. */
  retry?: string
  status: QrClaimStatusLabels
}

export const TERMINAL_CLAIM_STATES: ReadonlyArray<ClaimPollingState> = ['issued', 'failed', 'expired']

export function isTerminalClaimState(state: ClaimPollingState): boolean {
  return TERMINAL_CLAIM_STATES.includes(state)
}
