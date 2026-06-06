import {forwardRef, type ReactNode} from 'react'
import {useTimePicker, type UseTimePickerProps} from './use-time-picker'
import styles from './TimePicker.module.css'

export interface TimePickerProps extends UseTimePickerProps {
  label?: ReactNode
  /** Controlled time string `HH:mm` or `HH:mm:ss`. */
  value?: string
  defaultValue?: string
  className?: string
}

/**
 * Styled time-of-day input. v1 wraps the native `<input type="time">`; 12h/24h toggle is a
 * separate ticket.
 */
export const TimePicker = forwardRef<HTMLInputElement, TimePickerProps>(function TimePicker(
  {label, value, defaultValue, className, errorMessage, name, ...rest},
  ref,
) {
  const {inputProps, labelProps, errorId, isInvalid} = useTimePicker({...rest, errorMessage, name})
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
