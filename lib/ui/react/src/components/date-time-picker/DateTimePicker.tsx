import {forwardRef, type ReactNode} from 'react'
import {useDateTimePicker, type UseDateTimePickerProps} from './use-date-time-picker'
import styles from './DateTimePicker.module.css'

export interface DateTimePickerProps extends UseDateTimePickerProps {
  label?: ReactNode
  /** Controlled local datetime string `YYYY-MM-DDTHH:mm[:ss]`. */
  value?: string
  defaultValue?: string
  className?: string
}

/**
 * Styled date+time input. v1 wraps the native `<input type="datetime-local">`. The native control
 * uses local time with no timezone offset; consumers needing UTC normalize before persisting.
 */
export const DateTimePicker = forwardRef<HTMLInputElement, DateTimePickerProps>(function DateTimePicker(
  {label, value, defaultValue, className, errorMessage, name, ...rest},
  ref,
) {
  const {inputProps, labelProps, errorId, isInvalid} = useDateTimePicker({...rest, errorMessage, name})
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
