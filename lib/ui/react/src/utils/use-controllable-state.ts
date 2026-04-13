import { useState, useCallback, useRef } from 'react'

type SetStateAction<T> = T | ((prev: T) => T)

/**
 * Manages controlled/uncontrolled state pattern.
 * If `value` is provided, the component is controlled.
 * Otherwise, internal state is used with `defaultValue`.
 */
export function useControllableState<T>(opts: {
  value?: T
  defaultValue: T
  onChange?: (value: T) => void
}): [T, (next: SetStateAction<T>) => void] {
  const { value: controlledValue, defaultValue, onChange } = opts
  const [internalValue, setInternalValue] = useState(defaultValue)
  const isControlled = controlledValue !== undefined
  const currentValue = isControlled ? controlledValue : internalValue
  const onChangeRef = useRef(onChange)
  onChangeRef.current = onChange
  const currentValueRef = useRef(currentValue)
  currentValueRef.current = currentValue

  const setValue = useCallback(
    (next: SetStateAction<T>) => {
      const resolved = typeof next === 'function'
        ? (next as (prev: T) => T)(currentValueRef.current)
        : next
      if (!isControlled) {
        setInternalValue(resolved)
      }
      onChangeRef.current?.(resolved)
    },
    [isControlled],
  )

  return [currentValue, setValue]
}
