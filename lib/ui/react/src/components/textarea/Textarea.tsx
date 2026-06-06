import {forwardRef, type ReactNode} from 'react'
import {useTextarea, type UseTextareaProps} from './use-textarea'
import styles from './Textarea.module.css'

export interface TextareaProps extends UseTextareaProps {
  label?: ReactNode
  placeholder?: string
  value?: string
  defaultValue?: string
  rows?: number
  className?: string
}

/**
 * Styled multi-line textarea with label, placeholder, and error message support.
 *
 * @example
 * ```tsx
 * <Textarea label="Description" placeholder="Tell us more..." />
 * <Textarea label="Notes" isInvalid errorMessage="Notes are required" />
 * ```
 */
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  {label, placeholder, value, defaultValue, rows = 4, className, errorMessage, name, ...rest},
  ref,
) {
  const {textareaProps, labelProps, errorId, isInvalid} = useTextarea({...rest, errorMessage, name})
  return (
    <div className={`${styles.wrapper} ${className ?? ''}`.trim()}>
      {label ? (
        <label {...labelProps} className={styles.label}>
          {label}
        </label>
      ) : null}
      <textarea
        {...textareaProps}
        ref={ref}
        rows={rows}
        placeholder={placeholder}
        value={value}
        defaultValue={defaultValue}
        className={`${styles.textarea} ${isInvalid ? styles.invalid : ''}`.trim()}
      />
      {isInvalid && errorMessage ? (
        <span id={errorId} className={styles.error}>
          {errorMessage}
        </span>
      ) : null}
    </div>
  )
})
