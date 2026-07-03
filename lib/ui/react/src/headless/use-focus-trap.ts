import {useEffect, useRef} from 'react'
import type {RefObject} from 'react'

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

/**
 * Trap Tab focus within a container while `active`, restore focus to the previously
 * focused element on deactivate/unmount, and invoke `onEscape` on the Escape key.
 * Mirrors the focus management in @sphereon/ui-react's Modal (use-modal.ts).
 */
export function useFocusTrap<T extends HTMLElement>(active: boolean, onEscape?: () => void): RefObject<T | null> {
  const containerRef = useRef<T>(null)
  const previousFocus = useRef<HTMLElement | null>(null)
  const onEscapeRef = useRef(onEscape)
  onEscapeRef.current = onEscape

  useEffect(() => {
    if (!active) return
    const container = containerRef.current
    if (!container) return

    previousFocus.current = (document.activeElement as HTMLElement | null) ?? null
    const getFocusables = (): HTMLElement[] =>
      Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE))

    const initial = getFocusables()[0] ?? container
    initial.focus()

    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        onEscapeRef.current?.()
        return
      }
      if (event.key !== 'Tab') return
      const items = getFocusables()
      if (items.length === 0) {
        event.preventDefault()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    container.addEventListener('keydown', onKeyDown)
    return () => {
      container.removeEventListener('keydown', onKeyDown)
      previousFocus.current?.focus?.()
    }
  }, [active])

  return containerRef
}
