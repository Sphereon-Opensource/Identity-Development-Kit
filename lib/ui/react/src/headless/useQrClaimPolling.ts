import {useCallback, useEffect, useRef, useState} from 'react'
import {
  type ClaimPollingState,
  type StatusPoller,
  isTerminalClaimState,
} from '../components/qr-claim-panel/types'

export interface UseQrClaimPollingArgs {
  preAuthorizedCode: string | null | undefined
  statusPoller?: StatusPoller
  onTerminal?: (state: ClaimPollingState, detail?: string) => void
}

export interface UseQrClaimPollingReturn {
  state: ClaimPollingState
  detail?: string
  reset: () => void
}

export function useQrClaimPolling(args: UseQrClaimPollingArgs): UseQrClaimPollingReturn {
  const {preAuthorizedCode, statusPoller, onTerminal} = args
  const [state, setState] = useState<ClaimPollingState>('idle')
  const [detail, setDetail] = useState<string | undefined>(undefined)
  const onTerminalRef = useRef(onTerminal)
  const abortRef = useRef<AbortController | null>(null)
  const terminalFiredRef = useRef(false)
  const [resetCounter, setResetCounter] = useState(0)

  useEffect(() => {
    onTerminalRef.current = onTerminal
  }, [onTerminal])

  const reset = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    terminalFiredRef.current = false
    setState('idle')
    setDetail(undefined)
    setResetCounter((c) => c + 1)
  }, [])

  useEffect(() => {
    if (!preAuthorizedCode || !statusPoller) {
      return
    }

    const controller = new AbortController()
    abortRef.current = controller
    terminalFiredRef.current = false
    let cancelled = false

    setState('waiting-scan')
    setDetail(undefined)

    ;(async () => {
      try {
        const iterable = statusPoller({preAuthorizedCode, signal: controller.signal})
        for await (const result of iterable) {
          if (cancelled || controller.signal.aborted) break
          setState(result.state)
          setDetail(result.detail)
          if (isTerminalClaimState(result.state)) {
            if (!terminalFiredRef.current) {
              terminalFiredRef.current = true
              onTerminalRef.current?.(result.state, result.detail)
            }
            break
          }
        }
      } catch (err) {
        if (cancelled || controller.signal.aborted) return
        if (!terminalFiredRef.current) {
          terminalFiredRef.current = true
          setState('failed')
          const detailMsg = err instanceof Error ? err.message : undefined
          setDetail(detailMsg)
          onTerminalRef.current?.('failed', detailMsg)
        }
      }
    })()

    return () => {
      cancelled = true
      controller.abort()
      if (abortRef.current === controller) {
        abortRef.current = null
      }
    }
  }, [preAuthorizedCode, statusPoller, resetCounter])

  return {state, detail, reset}
}
