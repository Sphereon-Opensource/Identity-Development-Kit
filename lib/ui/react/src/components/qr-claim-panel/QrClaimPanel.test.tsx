import {act, render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {QrClaimPanel} from './QrClaimPanel'
import {
  type ClaimPollingResult,
  type QrClaimLabels,
  type QRValueResult,
  type StatusPoller,
} from './types'

const labels: QrClaimLabels = {
  qrTabLabel: 'QR',
  urlTabLabel: 'URL',
  walletUrlPlaceholder: 'https://wallet.example/...',
  openInWallet: 'Open in wallet',
  copyUrl: 'Copy',
  copied: 'Copied!',
  status: {
    waitingScan: 'Waiting for scan',
    authenticating: 'Authenticating',
    issuing: 'Issuing credential',
    issued: 'Credential issued',
    failed: 'Issuance failed',
    expired: 'Offer expired',
  },
}

function makeQrValue(overrides?: Partial<QRValueResult>): QRValueResult {
  return {
    id: 'qr-1',
    uriValue: 'openid-credential-offer://issuer.example?credential_offer=abc',
    preAuthorizedCode: 'pac-123',
    onExpiry: vi.fn(async () => undefined),
    ...overrides,
  }
}

function deferredPoller(): {
  poller: StatusPoller
  push: (r: ClaimPollingResult) => void
  end: () => void
} {
  const queue: ClaimPollingResult[] = []
  const waiters: Array<(v: IteratorResult<ClaimPollingResult>) => void> = []
  let done = false

  const push = (r: ClaimPollingResult) => {
    if (waiters.length > 0) {
      const w = waiters.shift()!
      w({value: r, done: false})
    } else {
      queue.push(r)
    }
  }
  const end = () => {
    done = true
    while (waiters.length > 0) {
      const w = waiters.shift()!
      w({value: undefined as never, done: true})
    }
  }

  const poller: StatusPoller = ({signal}) => ({
    [Symbol.asyncIterator]() {
      return {
        next(): Promise<IteratorResult<ClaimPollingResult>> {
          if (signal.aborted) return Promise.resolve({value: undefined as never, done: true})
          if (queue.length > 0) {
            return Promise.resolve({value: queue.shift()!, done: false})
          }
          if (done) return Promise.resolve({value: undefined as never, done: true})
          return new Promise((resolve) => {
            waiters.push(resolve)
            signal.addEventListener('abort', () => {
              const idx = waiters.indexOf(resolve)
              if (idx >= 0) {
                waiters.splice(idx, 1)
                resolve({value: undefined as never, done: true})
              }
            })
          })
        },
      }
    },
  })

  return {poller, push, end}
}

describe('QrClaimPanel', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders both tabs', async () => {
    render(
      <QrClaimPanel
        initialTab="qr"
        qrValueGenerator={async () => makeQrValue()}
        onClose={async () => undefined}
        labels={labels}
      />,
    )
    await waitFor(() => screen.getByRole('tab', {name: 'QR'}))
    expect(screen.getByRole('tab', {name: 'QR'})).toBeDefined()
    expect(screen.getByRole('tab', {name: 'URL'})).toBeDefined()
  })

  it('switching tab swaps content', async () => {
    const user = userEvent.setup()
    render(
      <QrClaimPanel
        initialTab="qr"
        qrValueGenerator={async () => makeQrValue()}
        onClose={async () => undefined}
        labels={labels}
      />,
    )
    await waitFor(() => screen.getByRole('tab', {name: 'QR', selected: true}))
    expect(screen.queryByPlaceholderText(labels.walletUrlPlaceholder)).toBeNull()
    await user.click(screen.getByRole('tab', {name: 'URL'}))
    expect(screen.getByPlaceholderText(labels.walletUrlPlaceholder)).toBeDefined()
  })

  it('surfaces status text from polling', async () => {
    const {poller, push} = deferredPoller()
    render(
      <QrClaimPanel
        initialTab="qr"
        qrValueGenerator={async () => makeQrValue()}
        statusPoller={poller}
        onClose={async () => undefined}
        labels={labels}
      />,
    )
    await waitFor(() => screen.getByText(labels.status.waitingScan))
    await act(async () => {
      push({state: 'authenticating'})
    })
    await waitFor(() => screen.getByText(labels.status.authenticating))
    await act(async () => {
      push({state: 'issuing'})
    })
    await waitFor(() => screen.getByText(labels.status.issuing))
  })

  it('fires onSuccess when state reaches issued', async () => {
    const onSuccess = vi.fn(async () => undefined)
    const {poller, push} = deferredPoller()
    render(
      <QrClaimPanel
        initialTab="qr"
        qrValueGenerator={async () => makeQrValue()}
        statusPoller={poller}
        onSuccess={onSuccess}
        onClose={async () => undefined}
        labels={labels}
      />,
    )
    await waitFor(() => screen.getByText(labels.status.waitingScan))
    await act(async () => {
      push({state: 'issued'})
    })
    await waitFor(() => screen.getByText(labels.status.issued))
    expect(onSuccess).toHaveBeenCalledOnce()
  })

  it('toggles copy button label to "copied"', async () => {
    const user = userEvent.setup()
    render(
      <QrClaimPanel
        initialTab="url"
        qrValueGenerator={async () =>
          makeQrValue({uriValue: 'openid-credential-offer://x?credential_offer=abc'})
        }
        onClose={async () => undefined}
        labels={labels}
      />,
    )
    await waitFor(() => screen.getByPlaceholderText(labels.walletUrlPlaceholder))
    const input = screen.getByPlaceholderText(labels.walletUrlPlaceholder) as HTMLInputElement
    await user.type(input, 'https://wallet.example/claim')
    await waitFor(() => screen.getByRole('button', {name: labels.copyUrl}))
    await user.click(screen.getByRole('button', {name: labels.copyUrl}))
    await waitFor(() => screen.getByRole('button', {name: labels.copied}))
  })
})
