import {forwardRef, type ReactNode} from 'react'
import {useNumberInput, type UseNumberInputProps} from './use-number-input'
import styles from './NumberInput.module.css'

export interface NumberInputProps extends UseNumberInputProps {
  label?: ReactNode
  placeholder?: string
  /**
   * Controlled value. Pass `undefined` for "no value entered" so the underlying input renders
   * empty; pass a `number` for a real value.
   */
  value?: number
  defaultValue?: number
  className?: string
}

/**
 * Styled numeric input. Calls `onChange` with `number` for a parseable value, or `undefined`
 * when the input is empty / not parseable.
 *
 * @example
 * ```tsx
 * <NumberInput label="Age" min={0} max={120} onChange={(n) => setAge(n)} />
 * ```
 */
export const NumberInput = forwardRef<HTMLInputElement, NumberInputProps>(function NumberInput(
  {label, placeholder, value, defaultValue, className, errorMessage, name, ...rest},
  ref,
) {
  const {inputProps, labelProps, errorId, isInvalid} = useNumberInput({...rest, errorMessage, name})
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
        placeholder={placeholder}
        value={value ?? ''}
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
