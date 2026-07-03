import {useCallback, useRef, useState} from 'react'
import type {KeyboardEvent} from 'react'

export interface RovingItemProps {
  tabIndex: number
  onKeyDown: (event: KeyboardEvent<HTMLElement>) => void
  ref: (node: HTMLElement | null) => void
}

export interface UseRovingTabindex {
  containerProps: {onFocus: () => void}
  getItemProps: (index: number) => RovingItemProps
}

/** Roving-tabindex keyboard navigation: one tab stop, arrow keys move focus + tabbability. */
export function useRovingTabindex(
  itemCount: number,
  orientation: 'vertical' | 'horizontal' = 'vertical',
): UseRovingTabindex {
  const [activeIndex, setActiveIndex] = useState(0)
  const nodes = useRef<(HTMLElement | null)[]>([])

  const focusIndex = useCallback(
    (index: number) => {
      const clamped = (index + itemCount) % itemCount
      setActiveIndex(clamped)
      nodes.current[clamped]?.focus()
    },
    [itemCount],
  )

  const getItemProps = useCallback(
    (index: number): RovingItemProps => {
      // Clamp at render: if itemCount shrinks below activeIndex (e.g. capability-driven
      // NavRail re-renders with fewer items), fall back to 0 so the rail stays reachable.
      const safeActive = activeIndex < itemCount ? activeIndex : 0
      return {
        tabIndex: index === safeActive ? 0 : -1,
        ref: (node) => {
          nodes.current[index] = node
        },
        onKeyDown: (event) => {
          const next = orientation === 'vertical' ? 'ArrowDown' : 'ArrowRight'
          const prev = orientation === 'vertical' ? 'ArrowUp' : 'ArrowLeft'
          if (event.key === next) {
            event.preventDefault()
            focusIndex(index + 1)
          } else if (event.key === prev) {
            event.preventDefault()
            focusIndex(index - 1)
          } else if (event.key === 'Home') {
            event.preventDefault()
            focusIndex(0)
          } else if (event.key === 'End') {
            event.preventDefault()
            focusIndex(itemCount - 1)
          }
        },
      }
    },
    [activeIndex, focusIndex, itemCount, orientation],
  )

  return {containerProps: {onFocus: () => {}}, getItemProps}
}
