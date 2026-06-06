import {type LabelHTMLAttributes, type TextareaHTMLAttributes, useCallback} from 'react'
import {useStableId} from '../../utils/use-id'

export interface UseTextareaProps {
  id?: string
  name?: string
  isDisabled?: boolean
  isReadOnly?: boolean
  isRequired?: boolean
  isInvalid?: boolean
  errorMessage?: string
  'aria-label'?: string
  onChange?: (value: string) => void
  onBlur?: () => void
}

export interface UseTextareaReturn {
  textareaProps: TextareaHTMLAttributes<HTMLTextAreaElement>
  labelProps: LabelHTMLAttributes<HTMLLabelElement>
  errorId: string
  isInvalid: boolean
}

/**
 * Headless textarea hook providing label association, validation, and ARIA attributes.
 * Mirrors `useInput` for the multi-line case.
 */
export function useTextarea(props: UseTextareaProps = {}): UseTextareaReturn {
  const {
    id: providedId,
    name,
    isDisabled = false,
    isReadOnly = false,
    isRequired = false,
    isInvalid = false,
    errorMessage,
    onChange,
    onBlur,
  } = props
  const ariaLabel = props['aria-label']
  const autoId = useStableId('textarea')
  const inputId = providedId ?? autoId
  const errorId = `${inputId}-error`

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange?.(e.target.value)
    },
    [onChange],
  )

  return {
    textareaProps: {
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
      onBlur,
    },
    labelProps: {
      htmlFor: inputId,
    },
    errorId,
    isInvalid,
  }
}
