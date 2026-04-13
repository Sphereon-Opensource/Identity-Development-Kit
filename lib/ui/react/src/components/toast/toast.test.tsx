import {act, render, renderHook, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {ToastProvider, useToastContext} from './ToastProvider'
import {useToast} from './use-toast'

describe('useToast', () => {
  it('starts with empty toasts', () => {
    const { result } = renderHook(() => useToast())
    expect(result.current.toasts).toHaveLength(0)
  })

  it('adds toast and returns id', () => {
    const { result } = renderHook(() => useToast())
    let id: string
    act(() => {
      id = result.current.toast({ message: 'Hello' })
    })
    expect(id!).toBeDefined()
    expect(id!).toContain('toast-')
    expect(result.current.toasts).toHaveLength(1)
    expect(result.current.toasts[0].message).toBe('Hello')
  })

  it('adds toast with default variant', () => {
    const { result } = renderHook(() => useToast())
    act(() => {
      result.current.toast({ message: 'Hello' })
    })
    expect(result.current.toasts[0].variant).toBe('default')
  })

  it('adds toast with custom variant', () => {
    const { result } = renderHook(() => useToast())
    act(() => {
      result.current.toast({ message: 'Error!', variant: 'error' })
    })
    expect(result.current.toasts[0].variant).toBe('error')
  })

  it('dismisses toast by id', () => {
    const { result } = renderHook(() => useToast())
    let id: string
    act(() => {
      id = result.current.toast({ message: 'Test', duration: 0 })
    })
    expect(result.current.toasts).toHaveLength(1)
    act(() => {
      result.current.dismiss(id)
    })
    expect(result.current.toasts).toHaveLength(0)
  })

  it('dismissAll clears all toasts', () => {
    const { result } = renderHook(() => useToast())
    act(() => {
      result.current.toast({ message: 'A', duration: 0 })
      result.current.toast({ message: 'B', duration: 0 })
    })
    expect(result.current.toasts).toHaveLength(2)
    act(() => {
      result.current.dismissAll()
    })
    expect(result.current.toasts).toHaveLength(0)
  })

  it('auto-dismisses after duration', () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useToast())
    act(() => {
      result.current.toast({ message: 'Temp', duration: 1000 })
    })
    expect(result.current.toasts).toHaveLength(1)
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(result.current.toasts).toHaveLength(0)
    vi.useRealTimers()
  })

  it('does not auto-dismiss when duration is 0', () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useToast())
    act(() => {
      result.current.toast({ message: 'Sticky', duration: 0 })
    })
    act(() => {
      vi.advanceTimersByTime(10000)
    })
    expect(result.current.toasts).toHaveLength(1)
    vi.useRealTimers()
  })

  it('can add multiple toasts', () => {
    const { result } = renderHook(() => useToast())
    act(() => {
      result.current.toast({ message: 'A', duration: 0 })
      result.current.toast({ message: 'B', duration: 0 })
      result.current.toast({ message: 'C', duration: 0 })
    })
    expect(result.current.toasts).toHaveLength(3)
  })
})

describe('ToastProvider', () => {
  it('renders children', () => {
    render(<ToastProvider><div>App</div></ToastProvider>)
    expect(screen.getByText('App')).toBeDefined()
  })

  it('renders notification region', () => {
    render(<ToastProvider><div>App</div></ToastProvider>)
    expect(screen.getByRole('region', { name: 'Notifications' })).toBeDefined()
  })

  it('notification region does not have aria-live to avoid double announcements', () => {
    render(<ToastProvider><div>App</div></ToastProvider>)
    const region = screen.getByRole('region', { name: 'Notifications' })
    expect(region.getAttribute('aria-live')).toBeNull()
  })
})

describe('useToastContext', () => {
  it('throws when used outside ToastProvider', () => {
    expect(() => {
      renderHook(() => useToastContext())
    }).toThrow('useToastContext must be used within a ToastProvider')
  })

  it('returns toast API when inside ToastProvider', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <ToastProvider>{children}</ToastProvider>
    )
    const { result } = renderHook(() => useToastContext(), { wrapper })
    expect(result.current.toast).toBeDefined()
    expect(result.current.dismiss).toBeDefined()
    expect(result.current.dismissAll).toBeDefined()
    expect(result.current.toasts).toEqual([])
  })
})
