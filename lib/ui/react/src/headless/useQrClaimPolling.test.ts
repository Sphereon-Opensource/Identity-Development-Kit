import {act, renderHook, waitFor} from '@testing-library/react'
import {afterEach, describe, expect, it, vi} from 'vitest'
import {useQrClaimPolling} from './useQrClaimPolling'
import type {ClaimPollingResult, StatusPoller} from '../components/qr-claim-panel/types'

function deferredPoller() {
  const queue: ClaimPollingResult[] = []
  const waiters: Array<(v: IteratorResult<ClaimPollingResult>) => void> = []
  let done = false
  let abortedCount = 0

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
          if (signal.aborted) {
            abortedCount++
            return Promise.resolve({value: undefined as never, done: true})
          }
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
                abortedCount++
                resolve({value: undefined as never, done: true})
              }
            })
          })
        },
      }
    },
  })

  return {poller, push, end, getAbortedCount: () => abortedCount}
}

describe('useQrClaimPolling', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts as idle when no preAuthorizedCode', () => {
    const {result} = renderHook(() =>
      useQrClaimPolling({preAuthorizedCode: null, statusPoller: undefined}),
    )
    expect(result.current.state).toBe('idle')
  })

  it('drives state through async iteration', async () => {
    const {poller, push} = deferredPoller()
    const {result} = renderHook(() =>
      useQrClaimPolling({preAuthorizedCode: 'pac-1', statusPoller: poller}),
    )
    await waitFor(() => expect(result.current.state).toBe('waiting-scan'))

    await act(async () => {
      push({state: 'authenticating'})
    })
    await waitFor(() => expect(result.current.state).toBe('authenticating'))

    await act(async () => {
      push({state: 'issuing', detail: 'building credential'})
    })
    await waitFor(() => expect(result.current.state).toBe('issuing'))
    expect(result.current.detail).toBe('building credential')
  })

  it('calls onTerminal exactly once on terminal state', async () => {
    const {poller, push} = deferredPoller()
    const onTerminal = vi.fn()
    const {result} = renderHook(() =>
      useQrClaimPolling({preAuthorizedCode: 'pac-1', statusPoller: poller, onTerminal}),
    )
    await waitFor(() => expect(result.current.state).toBe('waiting-scan'))

    await act(async () => {
      push({state: 'issued'})
    })
    await waitFor(() => expect(result.current.state).toBe('issued'))
    expect(onTerminal).toHaveBeenCalledTimes(1)
    expect(onTerminal).toHaveBeenCalledWith('issued', undefined)

    // Subsequent pushes are ignored because the loop has exited
    await act(async () => {
      push({state: 'failed'})
    })
    expect(onTerminal).toHaveBeenCalledTimes(1)
  })

  it('reset() aborts ongoing iteration and re-arms polling', async () => {
    const {poller, push, getAbortedCount} = deferredPoller()
    const {result} = renderHook(() =>
      useQrClaimPolling({preAuthorizedCode: 'pac-1', statusPoller: poller}),
    )
    await waitFor(() => expect(result.current.state).toBe('waiting-scan'))
    await act(async () => {
      push({state: 'authenticating'})
    })
    await waitFor(() => expect(result.current.state).toBe('authenticating'))

    await act(async () => {
      result.current.reset()
    })
    // After reset, the effect re-runs and re-enters 'waiting-scan' (preAuthorizedCode still set).
    await waitFor(() => expect(result.current.state).toBe('waiting-scan'))
    expect(result.current.detail).toBeUndefined()
    expect(getAbortedCount()).toBeGreaterThanOrEqual(1)
  })

  it('reset() with no preAuthorizedCode lands on idle', async () => {
    const {poller, push} = deferredPoller()
    const {result, rerender} = renderHook(
      ({code}: {code: string | null}) =>
        useQrClaimPolling({preAuthorizedCode: code, statusPoller: poller}),
      {initialProps: {code: 'pac-1'}},
    )
    await waitFor(() => expect(result.current.state).toBe('waiting-scan'))
    await act(async () => {
      push({state: 'authenticating'})
    })
    rerender({code: null})
    await act(async () => {
      result.current.reset()
    })
    expect(result.current.state).toBe('idle')
  })

  it('aborts iteration on unmount', async () => {
    const {poller, getAbortedCount} = deferredPoller()
    const {result, unmount} = renderHook(() =>
      useQrClaimPolling({preAuthorizedCode: 'pac-1', statusPoller: poller}),
    )
    await waitFor(() => expect(result.current.state).toBe('waiting-scan'))
    unmount()
    await waitFor(() => expect(getAbortedCount()).toBeGreaterThanOrEqual(1))
  })

  it('marks failed on iterator error', async () => {
    const onTerminal = vi.fn()
    const failingPoller: StatusPoller = () => ({
      [Symbol.asyncIterator]() {
        return {
          next(): Promise<IteratorResult<ClaimPollingResult>> {
            return Promise.reject(new Error('network down'))
          },
        }
      },
    })
    const {result} = renderHook(() =>
      useQrClaimPolling({preAuthorizedCode: 'pac-1', statusPoller: failingPoller, onTerminal}),
    )
    await waitFor(() => expect(result.current.state).toBe('failed'))
    expect(result.current.detail).toBe('network down')
    expect(onTerminal).toHaveBeenCalledTimes(1)
  })
})
