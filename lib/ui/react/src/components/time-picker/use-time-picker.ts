import {type InputHTMLAttributes, type LabelHTMLAttributes, useCallback} from 'react'
import {useStableId} from '../../utils/use-id'

export interface UseTimePickerProps {
  id?: string
  name?: string
  isDisabled?: boolean
  isReadOnly?: boolean
  isRequired?: boolean
  isInvalid?: boolean
  errorMessage?: string
  /** ISO-8601 lower bound (`HH:mm[:ss]`). */
  min?: string
  /** ISO-8601 upper bound (`HH:mm[:ss]`). */
  max?: string
  /** Step in seconds (default 60 — minute precision). */
  step?: number
  'aria-label'?: string
  /** Called with the time string (`HH:mm[:ss]`) or `''` when cleared. */
  onChange?: (value: string) => void
  onBlur?: () => void
}

export interface UseTimePickerReturn {
  inputProps: InputHTMLAttributes<HTMLInputElement>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  errorId: string
  isInvalid: boolean
}

/** Headless time-picker hook wrapping native `<input type="time">`. */
export function useTimePicker(props: UseTimePickerProps = {}): UseTimePickerReturn {
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
    step,
    onChange,
    onBlur,
  } = props
  const ariaLabel = props['aria-label']
  const autoId = useStableId('time')
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
      type: 'time',
      min,
      max,
      step,
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
