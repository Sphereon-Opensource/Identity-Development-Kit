import {type InputHTMLAttributes, type LabelHTMLAttributes, useCallback} from 'react'
import {useStableId} from '../../utils/use-id'

export interface UseInputProps {
  id?: string
  name?: string
  isDisabled?: boolean
  isReadOnly?: boolean
  isRequired?: boolean
  isInvalid?: boolean
  errorMessage?: string
  'aria-label'?: string
  onChange?: (value: string) => void
}

export interface UseInputReturn {
  inputProps: InputHTMLAttributes<HTMLInputElement>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  errorId: string
  isInvalid: boolean
}

/**
 * Headless input hook providing label association, validation, and ARIA attributes.
 *
 * @example
 * ```tsx
 * const { inputProps, labelProps, errorId } = useInput({ isRequired: true })
 * return (
 *   <div>
 *     <label {...labelProps}>Email</label>
 *     <input {...inputProps} />
 *   </div>
 * )
 * ```
 */
export function useInput(props: UseInputProps = {}): UseInputReturn {
  const { id: providedId, name, isDisabled = false, isReadOnly = false, isRequired = false, isInvalid = false, errorMessage, onChange } = props
  const ariaLabel = props['aria-label']
  const autoId = useStableId('input')
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
      disabled: isDisabled,
      readOnly: isReadOnly,
      required: isRequired,
      'aria-invalid': isInvalid || undefined,
      'aria-describedby': isInvalid && errorMessage ? errorId : undefined,
      'aria-required': isRequired || undefined,
      'aria-label': ariaLabel,
      onChange: handleChange,
    },
    labelProps: {
      htmlFor: inputId,
    },
    errorId,
    isInvalid,
  }
}
