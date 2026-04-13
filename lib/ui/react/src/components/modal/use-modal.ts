import React, { useCallback, useEffect, useRef, type HTMLAttributes, type RefObject } from 'react'
import { useStableId } from '../../utils/use-id'

export interface UseModalProps {
  isOpen: boolean
  onClose: () => void
  closeOnEscape?: boolean
  closeOnOverlayClick?: boolean
  initialFocusRef?: RefObject<HTMLElement | null>
  'aria-label'?: string
}

export interface UseModalReturn {
  overlayProps: HTMLAttributes<HTMLDivElement>
  dialogProps: HTMLAttributes<HTMLDivElement>
  dialogRef: React.RefObject<HTMLDivElement | null>
  titleProps: HTMLAttributes<HTMLHeadingElement>
  descriptionProps: HTMLAttributes<HTMLParagraphElement>
}

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

/**
 * Headless modal/dialog hook with focus trap, focus restoration, escape-to-close,
 * and overlay click handling. Follows WAI-ARIA dialog pattern.
 */
export function useModal(props: UseModalProps): UseModalReturn {
  const { isOpen, onClose, closeOnEscape = true, closeOnOverlayClick = true, initialFocusRef } = props
  const dialogId = useStableId('modal')
  const titleId = `${dialogId}-title`
  const descId = `${dialogId}-desc`
  const dialogRef = useRef<HTMLDivElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!isOpen) return

    // Save the element that was focused before the modal opened
    previousFocusRef.current = document.activeElement as HTMLElement

    // Focus initial element or first focusable in dialog
    requestAnimationFrame(() => {
      if (initialFocusRef?.current) {
        initialFocusRef.current.focus()
      } else if (dialogRef.current) {
        const firstFocusable = dialogRef.current.querySelector<HTMLElement>(FOCUSABLE_SELECTOR)
        if (firstFocusable) {
          firstFocusable.focus()
        } else {
          // If no focusable element, focus the dialog itself
          dialogRef.current.focus()
        }
      }
    })

    const handleKeyDown = (e: KeyboardEvent) => {
      // Escape key
      if (closeOnEscape && e.key === 'Escape') {
        e.stopPropagation()
        onClose()
        return
      }

      // Focus trap: Tab and Shift+Tab cycle within the dialog
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = Array.from(
          dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
        )
        if (focusable.length === 0) {
          e.preventDefault()
          return
        }

        const first = focusable[0]
        const last = focusable[focusable.length - 1]

        if (e.shiftKey) {
          // Shift+Tab: if focus is on first element, wrap to last
          if (document.activeElement === first) {
            e.preventDefault()
            last.focus()
          }
        } else {
          // Tab: if focus is on last element, wrap to first
          if (document.activeElement === last) {
            e.preventDefault()
            first.focus()
          }
        }
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    // Prevent body scroll
    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = originalOverflow

      // Restore focus to previously focused element
      if (previousFocusRef.current && typeof previousFocusRef.current.focus === 'function') {
        previousFocusRef.current.focus()
      }
    }
  }, [isOpen, onClose, closeOnEscape, initialFocusRef])

  const handleOverlayPointerDown = useCallback(
    (e: React.PointerEvent) => {
      if (closeOnOverlayClick && e.target === e.currentTarget) {
        onClose()
      }
    },
    [closeOnOverlayClick, onClose],
  )

  return {
    overlayProps: {
      onPointerDown: handleOverlayPointerDown as unknown as HTMLAttributes<HTMLDivElement>['onPointerDown'],
    },
    dialogProps: {
      role: 'dialog',
      'aria-modal': true,
      'aria-label': props['aria-label'],
      tabIndex: -1, // Make dialog focusable as last resort
    },
    dialogRef,
    titleProps: {
      id: titleId,
    },
    descriptionProps: {
      id: descId,
    },
  }
}
