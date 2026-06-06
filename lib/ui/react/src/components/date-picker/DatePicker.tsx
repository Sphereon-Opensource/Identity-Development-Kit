import {forwardRef, type ReactNode} from 'react'
import {useDatePicker, type UseDatePickerProps} from './use-date-picker'
import styles from './DatePicker.module.css'

export interface DatePickerProps extends UseDatePickerProps {
  label?: ReactNode
  /** Controlled ISO-8601 date string (`YYYY-MM-DD`). */
  value?: string
  defaultValue?: string
  className?: string
}

/**
 * Styled calendar-date input. v1 wraps the native `<input type="date">`; richer calendar UX is a
 * separate ticket.
 *
 * @example
 * ```tsx
 * <DatePicker label="Date of birth" max={today} onChange={(value) => setDob(value)} />
 * ```
 */
export const DatePicker = forwardRef<HTMLInputElement, DatePickerProps>(function DatePicker(
  {label, value, defaultValue, className, errorMessage, name, ...rest},
  ref,
) {
  const {inputProps, labelProps, errorId, isInvalid} = useDatePicker({...rest, errorMessage, name})
  return (
    <div className={`${styles.wrapper} ${className ?? ''}`.trim()}>
      {label ? (
        <label {...labelProps} className={styles.label}>
          {label}
        </label>
      ) : null}
      <input
        {...inputProps}
        ref={ref}
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
