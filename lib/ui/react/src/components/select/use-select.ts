import React, {type HTMLAttributes, type KeyboardEvent, useCallback, useEffect, useRef, useState} from 'react'
import {useControllableState} from '../../utils/use-controllable-state'
import {useStableId} from '../../utils/use-id'

export interface UseSelectProps<T> {
  items: T[]
  selectedItem?: T
  defaultSelectedItem?: T
  onSelect?: (item: T) => void
  isOpen?: boolean
  onOpenChange?: (open: boolean) => void
  getItemLabel?: (item: T) => string
  isDisabled?: boolean
}

export interface UseSelectReturn<T> {
  triggerProps: HTMLAttributes<HTMLButtonElement>
  menuProps: HTMLAttributes<HTMLUListElement>
  menuRef: React.RefObject<HTMLUListElement | null>
  getOptionProps: (item: T, index: number) => HTMLAttributes<HTMLLIElement>
  isOpen: boolean
  highlightedIndex: number
  selectedItem: T | undefined
}

/**
 * Headless select/dropdown hook with listbox pattern, keyboard navigation,
 * type-ahead search, and outside-click dismissal.
 */
export function useSelect<T>(props: UseSelectProps<T>): UseSelectReturn<T> {
  const {
    items,
    selectedItem: controlledSelected,
    defaultSelectedItem,
    onSelect,
    isOpen: controlledOpen,
    onOpenChange,
    getItemLabel = (item: T) => String(item),
    isDisabled = false,
  } = props

  const baseId = useStableId('select')
  const [isOpen, setIsOpen] = useState(controlledOpen ?? false)
  const [highlightedIndex, setHighlightedIndex] = useState(-1)
  const menuRef = useRef<HTMLUListElement>(null)

  const [selectedItem, setSelectedItem] = useControllableState<T | undefined>({
    value: controlledSelected,
    defaultValue: defaultSelectedItem as T | undefined,
    onChange: (item) => {
      if (item !== undefined) onSelect?.(item)
    },
  })

  const open = controlledOpen ?? isOpen
  const setOpen = useCallback(
    (value: boolean) => {
      setIsOpen(value)
      onOpenChange?.(value)
      if (!value) setHighlightedIndex(-1)
      if (value) requestAnimationFrame(() => menuRef.current?.focus())
    },
    [onOpenChange],
  )

  const selectItem = useCallback(
    (item: T) => {
      setSelectedItem(item)
      setOpen(false)
    },
    [setSelectedItem, setOpen],
  )

  // Close on outside click
  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      const trigger = document.getElementById(`${baseId}-trigger`)
      if (
        menuRef.current && !menuRef.current.contains(e.target as Node) &&
        trigger && !trigger.contains(e.target as Node)
      ) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open, setOpen, baseId])

  const handleTriggerKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        setOpen(true)
        setHighlightedIndex(0)
      }
    },
    [setOpen],
  )

  const handleMenuKeyDown = useCallback(
    (e: KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault()
          setHighlightedIndex((i) => Math.min(i + 1, items.length - 1))
          break
        case 'ArrowUp':
          e.preventDefault()
          setHighlightedIndex((i) => Math.max(i - 1, 0))
          break
        case 'Enter':
        case ' ':
          e.preventDefault()
          if (highlightedIndex >= 0) selectItem(items[highlightedIndex])
          break
        case 'Escape':
          e.preventDefault()
          setOpen(false)
          break
        case 'Home':
          e.preventDefault()
          setHighlightedIndex(0)
          break
        case 'End':
          e.preventDefault()
          setHighlightedIndex(items.length - 1)
          break
        default: {
          // Type-ahead: jump to first item starting with pressed character
          if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
            const char = e.key.toLowerCase()
            const startIndex = highlightedIndex + 1
            const matchIndex = items.findIndex((_item, i) => {
              const idx = (startIndex + i) % items.length
              return getItemLabel(items[idx]).toLowerCase().startsWith(char)
            })
            if (matchIndex >= 0) {
              const actualIndex = (startIndex + matchIndex) % items.length
              setHighlightedIndex(actualIndex)
            }
          }
          break
        }
      }
    },
    [items, highlightedIndex, selectItem, setOpen, getItemLabel],
  )

  return {
    triggerProps: {
      id: `${baseId}-trigger`,
      'aria-expanded': open,
      'aria-haspopup': 'listbox',
      'aria-controls': `${baseId}-menu`,
      'aria-disabled': isDisabled || undefined,
      tabIndex: isDisabled ? -1 : 0,
      onClick: () => !isDisabled && setOpen(!open),
      onKeyDown: handleTriggerKeyDown as unknown as HTMLAttributes<HTMLButtonElement>['onKeyDown'],
    },
    menuProps: {
      id: `${baseId}-menu`,
      role: 'listbox',
      'aria-labelledby': `${baseId}-trigger`,
      'aria-activedescendant': highlightedIndex >= 0 ? `${baseId}-option-${highlightedIndex}` : undefined,
      onKeyDown: handleMenuKeyDown as unknown as HTMLAttributes<HTMLUListElement>['onKeyDown'],
      tabIndex: -1,
    },
    menuRef,
    getOptionProps: (item: T, index: number) => ({
      id: `${baseId}-option-${index}`,
      role: 'option',
      'aria-selected': selectedItem === item,
      'data-highlighted': highlightedIndex === index || undefined,
      onClick: () => selectItem(item),
      onMouseEnter: () => setHighlightedIndex(index),
    }),
    isOpen: open,
    highlightedIndex,
    selectedItem,
  }
}
