import {type HTMLAttributes, type KeyboardEvent, useCallback, useRef} from 'react'
import {useControllableState} from '../../utils/use-controllable-state'
import {useStableId} from '../../utils/use-id'

export interface UseTabsProps {
  selectedIndex?: number
  defaultIndex?: number
  onChange?: (index: number) => void
  orientation?: 'horizontal' | 'vertical'
  /** Total number of tabs. Required for keyboard navigation. */
  tabCount: number
}

export interface UseTabsReturn {
  tabListProps: HTMLAttributes<HTMLDivElement>
  getTabProps: (index: number) => HTMLAttributes<HTMLButtonElement>
  getPanelProps: (index: number) => HTMLAttributes<HTMLDivElement>
  selectedIndex: number
}

/**
 * Headless tabs hook with roving tabindex and keyboard navigation (Arrow, Home, End).
 * Follows WAI-ARIA tabs pattern with automatic activation.
 *
 * @param props.tabCount - Total number of tabs (required for keyboard navigation)
 */
export function useTabs(props: UseTabsProps): UseTabsReturn {
  const { selectedIndex: controlled, defaultIndex = 0, onChange, orientation = 'horizontal', tabCount } = props
  const baseId = useStableId('tabs')
  const tabListRef = useRef<HTMLDivElement>(null)

  const [selectedIndex, setSelectedIndex] = useControllableState({
    value: controlled,
    defaultValue: defaultIndex,
    onChange,
  })

  const focusTab = useCallback((index: number) => {
    // Find the tab element by ID and focus it
    const tabEl = document.getElementById(`${baseId}-tab-${index}`)
    tabEl?.focus()
  }, [baseId])

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
      const isHorizontal = orientation === 'horizontal'
      const prevKey = isHorizontal ? 'ArrowLeft' : 'ArrowUp'
      const nextKey = isHorizontal ? 'ArrowRight' : 'ArrowDown'

      let newIndex: number | null = null

      if (e.key === prevKey) {
        e.preventDefault()
        newIndex = (index - 1 + tabCount) % tabCount
      } else if (e.key === nextKey) {
        e.preventDefault()
        newIndex = (index + 1) % tabCount
      } else if (e.key === 'Home') {
        e.preventDefault()
        newIndex = 0
      } else if (e.key === 'End') {
        e.preventDefault()
        newIndex = tabCount - 1
      }

      if (newIndex !== null) {
        setSelectedIndex(newIndex)
        focusTab(newIndex)
      }
    },
    [orientation, tabCount, setSelectedIndex, focusTab],
  )

  const getTabProps = useCallback(
    (index: number): HTMLAttributes<HTMLButtonElement> => ({
      id: `${baseId}-tab-${index}`,
      role: 'tab',
      'aria-selected': selectedIndex === index,
      'aria-controls': `${baseId}-panel-${index}`,
      tabIndex: selectedIndex === index ? 0 : -1,
      onClick: () => setSelectedIndex(index),
      onKeyDown: ((e: KeyboardEvent<HTMLButtonElement>) => {
        handleKeyDown(e, index)
      }) as unknown as HTMLAttributes<HTMLButtonElement>['onKeyDown'],
    }),
    [baseId, selectedIndex, setSelectedIndex, handleKeyDown],
  )

  const getPanelProps = useCallback(
    (index: number): HTMLAttributes<HTMLDivElement> => ({
      id: `${baseId}-panel-${index}`,
      role: 'tabpanel',
      'aria-labelledby': `${baseId}-tab-${index}`,
      hidden: selectedIndex !== index || undefined,
      tabIndex: selectedIndex === index ? 0 : undefined,
    }),
    [baseId, selectedIndex],
  )

  return {
    tabListProps: {
      role: 'tablist',
      'aria-orientation': orientation,
    },
    getTabProps,
    getPanelProps,
    selectedIndex,
  }
}
