import {type ReactNode} from 'react'
import {useField} from '../field/use-field'
import {useCheckbox} from '../checkbox/use-checkbox'
import checkboxStyles from '../checkbox/Checkbox.module.css'
import fieldStyles from '../field/Field.module.css'
import styles from './CheckboxField.module.css'

export interface CheckboxFieldProps {
  /** Inline label rendered beside the checkbox control. */
  label: ReactNode
  description?: string
  errorMessage?: string
  isChecked?: boolean
  defaultChecked?: boolean
  isIndeterminate?: boolean
  isDisabled?: boolean
  isRequired?: boolean
  id?: string
  name?: string
  className?: string
  onChange?: (checked: boolean) => void
}

/**
 * A single checkbox with an inline label, optional description, and error message.
 * The checkbox carries its own label — no duplicate field-style label is rendered.
 * Composes `useField` for description/error/ARIA wiring and `useCheckbox` for state.
 *
 * @example
 * ```tsx
 * <CheckboxField label="Accept terms" isRequired onChange={setAccepted} />
 * <CheckboxField label="All items" isIndeterminate isChecked={someChecked} onChange={setAll} />
 * ```
 */
export function CheckboxField({
  label,
  description,
  errorMessage,
  isChecked: isCheckedProp,
  defaultChecked,
  isIndeterminate = false,
  isDisabled = false,
  isRequired = false,
  id,
  name,
  className,
  onChange,
}: CheckboxFieldProps) {
  const {fieldId, controlProps, descriptionProps, errorProps} = useField({
    id,
    isRequired,
    isDisabled,
    errorMessage,
    description,
  })

  // Pass the fieldId into useCheckbox so both hooks share the same element id.
  const {inputProps, inputRef, isChecked} = useCheckbox({
    id: fieldId,
    isChecked: isCheckedProp,
    defaultChecked,
    isIndeterminate,
    isDisabled,
    onChange,
  })

  const visualState = isIndeterminate
    ? checkboxStyles.indeterminate
    : isChecked
      ? checkboxStyles.checked
      : ''

  // Merge field-level ARIA attributes (describedby, invalid, required) onto the hidden input.
  const mergedInputProps = {
    ...inputProps,
    ...(controlProps['aria-describedby'] != null
      ? {'aria-describedby': controlProps['aria-describedby']}
      : {}),
    ...(controlProps['aria-invalid'] ? {'aria-invalid': true as const} : {}),
    ...(controlProps['aria-required'] ? {'aria-required': true as const} : {}),
  }

  return (
    <div className={`${styles.root}${className ? ` ${className}` : ''}`}>
      {/* Label wraps the control — association by containment (htmlFor is redundant but explicit). */}
      <label className={checkboxStyles.wrapper} htmlFor={fieldId}>
        <input {...mergedInputProps} ref={inputRef} name={name} className={checkboxStyles.input} />
        <span className={`${checkboxStyles.control} ${visualState}`} aria-hidden="true">
          {isIndeterminate ? (
            <svg viewBox="0 0 12 2" fill="none" className={checkboxStyles.icon}>
              <path d="M1 1H11" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          ) : isChecked ? (
            <svg viewBox="0 0 12 10" fill="none" className={checkboxStyles.icon}>
              <path
                d="M1 5L4.5 8.5L11 1.5"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          ) : null}
        </span>
        {label != null ? <span className={checkboxStyles.label}>{label}</span> : null}
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
