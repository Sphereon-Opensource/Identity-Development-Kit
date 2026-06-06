import {type InputHTMLAttributes, type LabelHTMLAttributes, useCallback} from 'react'
import {useStableId} from '../../utils/use-id'

export interface UseDatePickerProps {
  id?: string
  name?: string
  isDisabled?: boolean
  isReadOnly?: boolean
  isRequired?: boolean
  isInvalid?: boolean
  errorMessage?: string
  /** ISO-8601 lower bound (`YYYY-MM-DD`). */
  min?: string
  /** ISO-8601 upper bound (`YYYY-MM-DD`). */
  max?: string
  'aria-label'?: string
  /**
   * Called with the ISO-8601 date string (`YYYY-MM-DD`) or `''` when the user clears the field.
   * Splitting "no value" lets consumers map cleared input to a domain-typed `Absent`.
   */
  onChange?: (value: string) => void
  onBlur?: () => void
}

export interface UseDatePickerReturn {
  inputProps: InputHTMLAttributes<HTMLInputElement>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  errorId: string
  isInvalid: boolean
}

/**
 * Headless date-picker hook. Wraps a native `<input type="date">` for v1; richer calendar UX is a
 * separate ticket per the IDK component plan.
 */
export function useDatePicker(props: UseDatePickerProps = {}): UseDatePickerReturn {
  const {
    id: providedId,
    name,
    isDisabled = false,
    isReadOnly = false,
    isRequired = false,
    isInvalid = false,
    errorMessage,
    min,
    max,
    onChange,
    onBlur,
  } = props
  const ariaLabel = props['aria-label']
  const autoId = useStableId('date')
  const inputId = providedId ?? autoId
  const errorId = `${inputId}-error`

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onChange?.(e.target.value)
    },
    [onChange],
  )

  return {
    inputProps: {
      id: inputId,
      name,
      type: 'date',
      min,
      max,
      disabled: isDisabled,
      readOnly: isReadOnly,
      required: isRequired,
      'aria-invalid': isInvalid || undefined,
      'aria-describedby': isInvalid && errorMessage ? errorId : undefined,
      'aria-required': isRequired || undefined,
      'aria-label': ariaLabel,
      onChange: handleChange,
      onBlur,
    },
    labelProps: {
      htmlFor: inputId,
    },
    errorId,
    isInvalid,
  }
}
