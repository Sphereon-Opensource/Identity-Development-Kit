import {type ReactNode} from 'react'
import {useField} from '../field/use-field'
import {useCheckbox} from '../checkbox/use-checkbox'
import fieldStyles from '../field/Field.module.css'
import styles from './ToggleField.module.css'

export interface ToggleFieldProps {
  /** Inline label rendered beside the switch track. */
  label: ReactNode
  description?: string
  errorMessage?: string
  isChecked?: boolean
  defaultChecked?: boolean
  isDisabled?: boolean
  isRequired?: boolean
  id?: string
  name?: string
  className?: string
  onChange?: (checked: boolean) => void
}

/**
 * A switch (toggle) control with an inline label, optional description, and error message.
 * Renders a track+thumb visual with `role="switch"` and `aria-checked` on the hidden input.
 * Composes `useField` for description/error/ARIA wiring and `useCheckbox` for state.
 *
 * @example
 * ```tsx
 * <ToggleField label="Enable notifications" isChecked={enabled} onChange={setEnabled} />
 * <ToggleField label="Dark mode" defaultChecked description="Changes apply immediately" />
 * ```
 */
export function ToggleField({
  label,
  description,
  errorMessage,
  isChecked: isCheckedProp,
  defaultChecked,
  isDisabled = false,
  isRequired = false,
  id,
  name,
  className,
  onChange,
}: ToggleFieldProps) {
  const {fieldId, controlProps, descriptionProps, errorProps} = useField({
    id,
    isRequired,
    isDisabled,
    errorMessage,
    description,
  })

  const {inputProps, inputRef, isChecked} = useCheckbox({
    id: fieldId,
    isChecked: isCheckedProp,
    defaultChecked,
    isDisabled,
    onChange,
  })

  // Build merged props: override role to "switch" and set a plain boolean aria-checked
  // (switches have no indeterminate/mixed state).
  const mergedInputProps = {
    ...inputProps,
    role: 'switch' as const,
    'aria-checked': isChecked,
    ...(controlProps['aria-describedby'] != null
      ? {'aria-describedby': controlProps['aria-describedby']}
      : {}),
    ...(controlProps['aria-invalid'] ? {'aria-invalid': true as const} : {}),
    ...(controlProps['aria-required'] ? {'aria-required': true as const} : {}),
  }

  return (
    <div className={`${styles.root}${className ? ` ${className}` : ''}`}>
      <label className={styles.wrapper} htmlFor={fieldId}>
        <input {...mergedInputProps} ref={inputRef} name={name} className={styles.input} />
        <span className={`${styles.track}${isChecked ? ` ${styles.on}` : ''}`} aria-hidden="true">
          <span className={styles.thumb} />
        </span>
        {label != null ? <span className={styles.label}>{label}</span> : null}
      </label>
      {description && (
        <p {...descriptionProps} className={fieldStyles.description}>
          {description}
        </p>
      )}
      {errorMessage && (
        <p {...errorProps} className={fieldStyles.error}>
          {errorMessage}
        </p>
      )}
    </div>
  )
}
