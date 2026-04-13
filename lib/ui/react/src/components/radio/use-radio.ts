import {createContext, type HTMLAttributes, type KeyboardEvent, useCallback, useContext, useRef} from 'react'
import {useControllableState} from '../../utils/use-controllable-state'
import {useStableId} from '../../utils/use-id'

export interface UseRadioGroupProps {
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  orientation?: 'horizontal' | 'vertical'
  name?: string
  'aria-label'?: string
  'aria-labelledby'?: string
}

export interface UseRadioGroupReturn {
  groupProps: HTMLAttributes<HTMLDivElement>
  getRadioProps: (radioValue: string) => RadioItemProps
  selectedValue: string
}

export interface RadioItemProps {
  role: 'radio'
  name: string
  value: string
  checked: boolean
  'aria-checked': boolean
  tabIndex: number
  onChange: () => void
  onKeyDown: (e: KeyboardEvent<HTMLInputElement>) => void
}

export interface UseRadioProps {
  value: string
  isDisabled?: boolean
}

// Context for passing group state to Radio children
export interface RadioGroupContextValue {
  name: string
  selectedValue: string
  onSelect: (value: string) => void
  orientation: 'horizontal' | 'vertical'
  registerValue: (value: string) => void
  unregisterValue: (value: string) => void
  values: string[]
}

export const RadioGroupContext = createContext<RadioGroupContextValue | null>(null)

/**
 * Headless radio group hook with roving tabindex and arrow key navigation.
 * Provides context for child Radio components via RadioGroupContext.
 */
export function useRadioGroup(props: UseRadioGroupProps = {}): UseRadioGroupReturn {
  const { value: controlledValue, defaultValue = '', onChange, orientation = 'vertical', name: providedName } = props
  const autoName = useStableId('radio-group')
  const groupName = providedName ?? autoName
  const valuesRef = useRef<string[]>([])

  const [selectedValue, setSelectedValue] = useControllableState({
    value: controlledValue,
    defaultValue,
    onChange,
  })

  const registerValue = useCallback((v: string) => {
    if (!valuesRef.current.includes(v)) valuesRef.current.push(v)
  }, [])

  const unregisterValue = useCallback((v: string) => {
    valuesRef.current = valuesRef.current.filter((x) => x !== v)
  }, [])

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLInputElement>) => {
      const values = valuesRef.current
      if (values.length === 0) return

      const currentIndex = values.indexOf(selectedValue)
      const isHorizontal = orientation === 'horizontal'
      const prevKey = isHorizontal ? 'ArrowLeft' : 'ArrowUp'
      const nextKey = isHorizontal ? 'ArrowRight' : 'ArrowDown'
      let newIndex = currentIndex

      switch (e.key) {
        case prevKey:
          e.preventDefault()
          newIndex = (currentIndex - 1 + values.length) % values.length
          break
        case nextKey:
          e.preventDefault()
          newIndex = (currentIndex + 1) % values.length
          break
        case 'Home':
          e.preventDefault()
          newIndex = 0
          break
        case 'End':
          e.preventDefault()
          newIndex = values.length - 1
          break
        default:
          return
      }

      setSelectedValue(values[newIndex])
      // Move focus to the newly selected radio
      const radioEl = document.querySelector<HTMLInputElement>(
        `input[name="${groupName}"][value="${values[newIndex]}"]`
      )
      radioEl?.focus()
    },
    [selectedValue, orientation, groupName, setSelectedValue],
  )

  const getRadioProps = useCallback(
    (radioValue: string): RadioItemProps => ({
      role: 'radio' as const,
      name: groupName,
      value: radioValue,
      checked: selectedValue === radioValue,
      'aria-checked': selectedValue === radioValue,
      // Roving tabindex: only selected (or first if none selected) is tabbable
      tabIndex: selectedValue === radioValue || (selectedValue === '' && valuesRef.current[0] === radioValue) ? 0 : -1,
      onChange: () => setSelectedValue(radioValue),
      onKeyDown: handleKeyDown,
    }),
    [groupName, selectedValue, setSelectedValue, handleKeyDown],
  )

  return {
    groupProps: {
      role: 'radiogroup',
      'aria-orientation': orientation,
      'aria-label': props['aria-label'],
      'aria-labelledby': props['aria-labelledby'],
    },
    getRadioProps,
    selectedValue,
  }
}

/**
 * Headless radio hook that reads group context from {@link RadioGroupContext}
 * to determine selection state and provide input/label props.
 */
export function useRadio(props: UseRadioProps) {
  const { value, isDisabled = false } = props
  const ctx = useContext(RadioGroupContext)
  const autoId = useStableId('radio')

  if (ctx) {
    return {
      inputProps: {
        id: `${autoId}-${value}`,
        disabled: isDisabled,
      },
      labelProps: {
        htmlFor: `${autoId}-${value}`,
      },
      isSelected: ctx.selectedValue === value,
    }
  }

  return {
    inputProps: {
      id: `${autoId}-${value}`,
      disabled: isDisabled,
    },
    labelProps: {
      htmlFor: `${autoId}-${value}`,
    },
    isSelected: false,
  }
}
