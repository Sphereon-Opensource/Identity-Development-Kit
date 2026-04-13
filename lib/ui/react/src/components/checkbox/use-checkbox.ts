import React, {type InputHTMLAttributes, type LabelHTMLAttributes, useCallback, useEffect, useRef} from 'react'
import {useControllableState} from '../../utils/use-controllable-state'
import {useStableId} from '../../utils/use-id'

export interface UseCheckboxProps {
  id?: string
  isChecked?: boolean
  defaultChecked?: boolean
  isIndeterminate?: boolean
  isDisabled?: boolean
  onChange?: (checked: boolean) => void
}

export interface UseCheckboxReturn {
  inputProps: InputHTMLAttributes<HTMLInputElement>
  inputRef: React.RefObject<HTMLInputElement | null>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  isChecked: boolean
}

/**
 * Headless checkbox hook with controlled/uncontrolled state and indeterminate support.
 *
 * @example
 * ```tsx
 * const { inputProps, inputRef, labelProps, isChecked } = useCheckbox({ onChange: (v) => console.log(v) })
 * ```
 */
export function useCheckbox(props: UseCheckboxProps = {}): UseCheckboxReturn {
  const { id: providedId, isChecked: controlledChecked, defaultChecked = false, isIndeterminate = false, isDisabled = false, onChange } = props
  const autoId = useStableId('checkbox')
  const inputId = providedId ?? autoId
  const inputRef = useRef<HTMLInputElement>(null)

  const [isChecked, setIsChecked] = useControllableState({
    value: controlledChecked,
    defaultValue: defaultChecked,
    onChange,
  })

  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.indeterminate = isIndeterminate
    }
  }, [isIndeterminate])

  const handleChange = useCallback(() => {
    setIsChecked(!isChecked)
  }, [isChecked, setIsChecked])

  return {
    inputProps: {
      id: inputId,
      type: 'checkbox',
      checked: isChecked,
      disabled: isDisabled,
      'aria-checked': isIndeterminate ? 'mixed' : isChecked,
      onChange: handleChange,
    },
    inputRef,
    labelProps: {
      htmlFor: inputId,
    },
    isChecked,
  }
}
