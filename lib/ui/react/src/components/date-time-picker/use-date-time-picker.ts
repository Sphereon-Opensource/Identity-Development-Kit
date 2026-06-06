import {type InputHTMLAttributes, type LabelHTMLAttributes, useCallback} from 'react'
import {useStableId} from '../../utils/use-id'

export interface UseDateTimePickerProps {
  id?: string
  name?: string
  isDisabled?: boolean
  isReadOnly?: boolean
  isRequired?: boolean
  isInvalid?: boolean
  errorMessage?: string
  /** ISO-8601 lower bound (`YYYY-MM-DDTHH:mm`). */
  min?: string
  /** ISO-8601 upper bound (`YYYY-MM-DDTHH:mm`). */
  max?: string
  /** Step in seconds. */
  step?: number
  'aria-label'?: string
  /**
   * Called with the local datetime string `YYYY-MM-DDTHH:mm[:ss]` or `''` when cleared.
   * The native control returns local time without an offset; consumers that need UTC convert
   * before persisting.
   */
  onChange?: (value: string) => void
  onBlur?: () => void
}

export interface UseDateTimePickerReturn {
  inputProps: InputHTMLAttributes<HTMLInputElement>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  errorId: string
  isInvalid: boolean
}

/** Headless date-time-picker hook wrapping native `<input type="datetime-local">`. */
export function useDateTimePicker(props: UseDateTimePickerProps = {}): UseDateTimePickerReturn {
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
  const autoId = useStableId('datetime')
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
      type: 'datetime-local',
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
