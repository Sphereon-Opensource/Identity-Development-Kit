import { forwardRef, type ReactNode } from 'react'
import { useInput, type UseInputProps } from './use-input'
import styles from './Input.module.css'

export interface InputProps extends UseInputProps {
  label?: ReactNode
  placeholder?: string
  value?: string
  defaultValue?: string
  type?: string
  className?: string
}

/**
 * Styled text input with label, placeholder, and error message support.
 *
 * @example
 * ```tsx
 * <Input label="Email" placeholder="you@example.com" isRequired />
 * <Input label="Name" isInvalid errorMessage="Name is required" />
 * ```
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, placeholder, value, defaultValue, type = 'text', className, errorMessage, name, ...rest },
  ref,
) {
  const { inputProps, labelProps, errorId, isInvalid } = useInput({ ...rest, errorMessage, name })
  return (
    <div className={`${styles.wrapper} ${className ?? ''}`.trim()}>
      {label ? <label {...labelProps} className={styles.label}>{label}</label> : null}
      <input
        {...inputProps}
        ref={ref}
        type={type}
        placeholder={placeholder}
        value={value}
        defaultValue={defaultValue}
        className={`${styles.input} ${isInvalid ? styles.invalid : ''}`.trim()}
      />
      {isInvalid && errorMessage ? (
        <span id={errorId} className={styles.error}>
          {errorMessage}
        </span>
      ) : null}
    </div>
  )
})
