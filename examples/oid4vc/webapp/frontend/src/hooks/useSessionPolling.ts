import { useState, useEffect, useRef, useCallback } from 'react'

export function useSessionPolling<T>(
  pollFn: () => Promise<T>,
  isTerminal: (data: T) => boolean,
  intervalMs = 2000
) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [polling, setPolling] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stop = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    setPolling(false)
  }, [])

  const start = useCallback(() => {
    setPolling(true)
    setError(null)

    const poll = async () => {
      try {
        const result = await pollFn()
        setData(result)
        if (isTerminal(result)) {
          stop()
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
        stop()
      }
    }

    poll()
    timerRef.current = setInterval(poll, intervalMs)
  }, [pollFn, isTerminal, intervalMs, stop])

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [])

  return { data, error, polling, start, stop }
}
