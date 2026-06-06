import {type InputHTMLAttributes, type LabelHTMLAttributes, useCallback} from 'react'
import {useStableId} from '../../utils/use-id'

export interface UseNumberInputProps {
  id?: string
  name?: string
  isDisabled?: boolean
  isReadOnly?: boolean
  isRequired?: boolean
  isInvalid?: boolean
  errorMessage?: string
  min?: number
  max?: number
  step?: number
  'aria-label'?: string
  /**
   * Called with the parsed number, or `undefined` when the input is empty / not parseable.
   * Splitting "no value" from `0` keeps the consumer's domain-typed value (e.g. `FieldValue.Number`
   * vs `FieldValue.Absent`) accurate.
   */
  onChange?: (value: number | undefined) => void
  onBlur?: () => void
}

export interface UseNumberInputReturn {
  inputProps: InputHTMLAttributes<HTMLInputElement>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  errorId: string
  isInvalid: boolean
}

/**
 * Headless number-input hook with label/aria/validation wiring. Uses `inputmode="decimal"` so
 * mobile keyboards present a numeric pad while still allowing the locale-aware decimal separator.
 */
export function useNumberInput(props: UseNumberInputProps = {}): UseNumberInputReturn {
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
  const autoId = useStableId('number')
  const inputId = providedId ?? autoId
  const errorId = `${inputId}-error`

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (!onChange) {
        return
      }
      const raw = e.target.value
      if (raw === '') {
        onChange(undefined)
        return
      }
      const numeric = Number(raw)
      onChange(Number.isNaN(numeric) ? undefined : numeric)
    },
    [onChange],
  )

  return {
    inputProps: {
      id: inputId,
      name,
      type: 'number',
      inputMode: 'decimal',
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
