import {type KeyboardEvent, type ReactNode, useCallback, useRef} from 'react'
import {useControllableState} from '../../utils/use-controllable-state'

export interface SegmentOption<T extends string> {
  value: T
  label: string
  icon?: ReactNode
  isDisabled?: boolean
}

export interface UseSegmentedControlProps<T extends string> {
  options: SegmentOption<T>[]
  value?: T
  defaultValue?: T
  onChange?: (value: T) => void
  'aria-label'?: string
  'aria-labelledby'?: string
  isDisabled?: boolean
  id?: string
}

export function useSegmentedControl<T extends string>(props: UseSegmentedControlProps<T>) {
  const {options, value: controlledValue, defaultValue, onChange, isDisabled = false} = props

  const groupRef = useRef<HTMLDivElement>(null)

  const enabledOptions = options.filter((o) => !o.isDisabled && !isDisabled)
  const firstEnabledValue = (enabledOptions[0]?.value ?? options[0]?.value) as T

  const [selectedValue, setSelectedValue] = useControllableState<T>({
    value: controlledValue,
    defaultValue: defaultValue ?? firstEnabledValue,
    onChange,
  })

  const focusSegment = useCallback((value: T) => {
    const btn = groupRef.current?.querySelector<HTMLButtonElement>(`[data-sc-value="${value}"]`)
    btn?.focus()
  }, [])

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLButtonElement>) => {
      const enabled = options.filter((o) => !o.isDisabled && !isDisabled)
      if (enabled.length === 0) return

      const currentIndex = enabled.findIndex((o) => o.value === selectedValue)
      let newIndex = currentIndex

      switch (e.key) {
        case 'ArrowLeft':
        case 'ArrowUp':
          e.preventDefault()
          newIndex = (currentIndex - 1 + enabled.length) % enabled.length
          break
        case 'ArrowRight':
        case 'ArrowDown':
          e.preventDefault()
          newIndex = (currentIndex + 1) % enabled.length
          break
        case 'Home':
          e.preventDefault()
          newIndex = 0
          break
        case 'End':
          e.preventDefault()
          newIndex = enabled.length - 1
          break
        default:
          return
      }

      const newValue = enabled[newIndex].value
      setSelectedValue(newValue)
      focusSegment(newValue)
    },
    [selectedValue, options, isDisabled, setSelectedValue, focusSegment],
  )

  const getSegmentProps = useCallback(
    (option: SegmentOption<T>) => {
      const isActive = selectedValue === option.value
      const isSegmentDisabled = isDisabled || Boolean(option.isDisabled)

      // Roving tabindex: active segment is tabbable; if nothing active, first enabled is tabbable
      const enabledValues = options.filter((o) => !o.isDisabled && !isDisabled).map((o) => o.value)
      const noneActive = !enabledValues.includes(selectedValue)
      const isFirstEnabled = enabledValues[0] === option.value
      const shouldBeTabbable = isActive || (noneActive && isFirstEnabled)

      return {
        role: 'radio' as const,
        'aria-checked': isActive,
        'data-sc-value': option.value,
        disabled: isSegmentDisabled || undefined,
        tabIndex: isSegmentDisabled ? -1 : shouldBeTabbable ? 0 : -1,
        onClick: () => {
          if (!isSegmentDisabled) {
            setSelectedValue(option.value)
          }
        },
        onKeyDown: handleKeyDown,
      }
    },
    [selectedValue, options, isDisabled, setSelectedValue, handleKeyDown],
  )

  return {
    groupRef,
    groupProps: {
      role: 'radiogroup' as const,
      'aria-label': props['aria-label'],
      'aria-labelledby': props['aria-labelledby'],
      id: props.id,
    },
    getSegmentProps,
    selectedValue,
  }
}
