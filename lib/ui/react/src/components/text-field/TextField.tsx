import type {InputHTMLAttributes} from 'react'
import {Field} from '../field'
import styles from './TextField.module.css'

export interface TextFieldProps {
  // field
  label?: string
  description?: string
  errorMessage?: string
  isRequired?: boolean
  isInvalid?: boolean
  isDisabled?: boolean
  isReadOnly?: boolean
  id?: string
  className?: string
  // input
  name?: string
  type?: 'text' | 'email' | 'password' | 'url' | 'tel' | 'number'
  value?: string
  defaultValue?: string
  placeholder?: string
  autoComplete?: string
  inputMode?: string
  // number-ish
  min?: number
  max?: number
  step?: number
  // multiline
  multiline?: boolean
  rows?: number
  // events
  onChange?: (value: string) => void
  onBlur?: () => void
}

/**
 * Flexible field-bound text input supporting text/email/password/url/tel/number and multiline.
 * Composes the Field render-prop wrapper for label, description, error, and ARIA attributes.
 *
 * @example
 * ```tsx
 * <TextField label="Email" type="email" isRequired />
 * <TextField label="Notes" multiline rows={5} />
 * <TextField label="Age" type="number" min={0} max={120} />
 * <TextField label="Password" type="password" errorMessage="Too short" />
 * ```
 */
export function TextField({
  label,
  description,
  errorMessage,
  isRequired,
  isInvalid: isInvalidProp,
  isDisabled,
  isReadOnly,
  id,
  className,
  name,
  type = 'text',
  value,
  defaultValue,
  placeholder,
  autoComplete,
  inputMode: inputModeProp,
  min,
  max,
  step,
  multiline,
  rows,
  onChange,
  onBlur,
}: TextFieldProps) {
  const isInvalid = isInvalidProp ?? Boolean(errorMessage)
  const inputMode = (inputModeProp ?? (type === 'number' ? 'numeric' : undefined)) as
    | InputHTMLAttributes<HTMLInputElement>['inputMode']
    | undefined

  const inputClassName = `${styles.input}${isInvalid ? ` ${styles.invalid}` : ''}`

  return (
    <Field
      label={label}
      description={description}
      errorMessage={errorMessage}
      isRequired={isRequired}
      isInvalid={isInvalid}
      isDisabled={isDisabled}
      id={id}
      className={className}
    >
      {(controlProps) =>
        multiline ? (
          <textarea
            {...controlProps}
            className={inputClassName}
            name={name}
            value={value}
            defaultValue={defaultValue}
            placeholder={placeholder}
            rows={rows}
            disabled={isDisabled}
            readOnly={isReadOnly}
            onChange={(e) => onChange?.(e.currentTarget.value)}
            onBlur={onBlur}
          />
        ) : (
          <input
            {...controlProps}
            className={inputClassName}
            name={name}
            type={type}
            value={value}
            defaultValue={defaultValue}
            placeholder={placeholder}
            autoComplete={autoComplete}
            inputMode={inputMode}
            min={min}
            max={max}
            step={step}
            disabled={isDisabled}
            readOnly={isReadOnly}
            onChange={(e) => onChange?.(e.currentTarget.value)}
            onBlur={onBlur}
          />
        )
      }
    </Field>
  )
}

/**
 * Convenience wrapper for numeric input. All TextField props except `type` are forwarded.
 *
 * @example
 * ```tsx
 * <NumberField label="Age" min={0} max={120} />
 * ```
 */
export function NumberField(props: Omit<TextFieldProps, 'type'>) {
  return <TextField {...props} type="number" />
}

/**
 * Convenience wrapper for multiline textarea. All TextField props except `multiline` are forwarded.
 *
 * @example
 * ```tsx
 * <TextAreaField label="Description" rows={5} />
 * ```
 */
export function TextAreaField(props: Omit<TextFieldProps, 'multiline'>) {
  return <TextField {...props} multiline />
}
