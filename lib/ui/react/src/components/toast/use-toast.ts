import { useCallback, useRef, useState } from 'react'

export interface ToastOptions {
  message: string
  variant?: 'default' | 'success' | 'error' | 'warning' | 'info'
  duration?: number
  dismissible?: boolean
}

export interface ToastItem extends ToastOptions {
  id: string
}

export interface UseToastReturn {
  toast: (opts: ToastOptions) => string
  dismiss: (id: string) => void
  dismissAll: () => void
  pauseTimer: (id: string) => void
  resumeTimer: (id: string) => void
  toasts: ToastItem[]
}

interface TimerState {
  timeout: ReturnType<typeof setTimeout> | null
  startedAt: number
  remaining: number
}

/**
 * Toast notification hook with auto-dismiss timers that pause on hover/focus.
 * Use with ToastProvider for portal rendering, or standalone for headless usage.
 */
export function useToast(): UseToastReturn {
  const counterRef = useRef(0)
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const timersRef = useRef<Map<string, TimerState>>(new Map())

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
    const timer = timersRef.current.get(id)
    if (timer?.timeout) {
      clearTimeout(timer.timeout)
    }
    timersRef.current.delete(id)
  }, [])

  const startTimer = useCallback(
    (id: string, duration: number) => {
      const timeout = setTimeout(() => dismiss(id), duration)
      timersRef.current.set(id, {
        timeout,
        startedAt: Date.now(),
        remaining: duration,
      })
    },
    [dismiss],
  )

  const pauseTimer = useCallback((id: string) => {
    const timer = timersRef.current.get(id)
    if (timer?.timeout) {
      clearTimeout(timer.timeout)
      timer.timeout = null
      timer.remaining = timer.remaining - (Date.now() - timer.startedAt)
      if (timer.remaining < 0) timer.remaining = 0
    }
  }, [])

  const resumeTimer = useCallback(
    (id: string) => {
      const timer = timersRef.current.get(id)
      if (timer && !timer.timeout && timer.remaining > 0) {
        timer.startedAt = Date.now()
        timer.timeout = setTimeout(() => dismiss(id), timer.remaining)
      }
    },
    [dismiss],
  )

  const toast = useCallback(
    (opts: ToastOptions): string => {
      const id = `toast-${++counterRef.current}`
      const item: ToastItem = {
        id,
        variant: 'default',
        duration: 5000,
        dismissible: true,
        ...opts,
      }
      setToasts((prev) => [...prev, item])

      if (item.duration && item.duration > 0) {
        startTimer(id, item.duration)
      }

      return id
    },
    [startTimer],
  )

  const dismissAll = useCallback(() => {
    setToasts([])
    for (const timer of timersRef.current.values()) {
      if (timer.timeout) clearTimeout(timer.timeout)
    }
    timersRef.current.clear()
  }, [])

  return { toast, dismiss, dismissAll, pauseTimer, resumeTimer, toasts }
}
