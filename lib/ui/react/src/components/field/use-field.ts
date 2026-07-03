import {useStableId} from '../../utils/use-id'

export interface UseFieldProps {
  id?: string
  isRequired?: boolean
  isDisabled?: boolean
  isInvalid?: boolean          // explicit; if omitted, derived from errorMessage presence
  errorMessage?: string
  description?: string
}

export interface UseFieldReturn {
  fieldId: string
  labelProps: { htmlFor: string; id: string }
  controlProps: {
    id: string
    'aria-required'?: true
    'aria-invalid'?: true
    'aria-describedby'?: string        // space-joined description+error ids (only those present)
    'aria-disabled'?: true
  }
  descriptionProps: { id: string }
  errorProps: { id: string; role: 'alert' }
  isInvalid: boolean
}

/**
 * Headless field hook providing label association, description, error, and ARIA attributes
 * for any form control via a render-prop pattern.
 *
 * @example
 * ```tsx
 * const { labelProps, controlProps, descriptionProps, errorProps, isInvalid } = useField({ isRequired: true, errorMessage: 'Required' })
 * return (
 *   <div>
 *     <label {...labelProps}>Email</label>
 *     <input {...controlProps} />
 *     {isInvalid && <p {...errorProps}>Required</p>}
 *   </div>
 * )
 * ```
 */
export function useField(props: UseFieldProps = {}): UseFieldReturn {
  const {
    id: providedId,
    isRequired = false,
    isDisabled = false,
    isInvalid: isInvalidProp,
    errorMessage,
    description,
  } = props

  const autoId = useStableId('field')
  const fieldId = providedId ?? autoId
  const labelId = `${fieldId}-label`
  const descriptionId = `${fieldId}-description`
  const errorId = `${fieldId}-error`

  const isInvalid = isInvalidProp ?? Boolean(errorMessage)

  const describedByParts: string[] = []
  if (description) describedByParts.push(descriptionId)
  if (errorMessage) describedByParts.push(errorId)

  const controlProps: UseFieldReturn['controlProps'] = {
    id: fieldId,
    ...(isRequired ? {'aria-required': true as const} : {}),
    ...(isInvalid ? {'aria-invalid': true as const} : {}),
    ...(describedByParts.length > 0 ? {'aria-describedby': describedByParts.join(' ')} : {}),
    ...(isDisabled ? {'aria-disabled': true as const} : {}),
  }

  return {
    fieldId,
    labelProps: {
      htmlFor: fieldId,
      id: labelId,
    },
    controlProps,
    descriptionProps: {id: descriptionId},
    errorProps: {id: errorId, role: 'alert'},
    isInvalid,
  }
}
