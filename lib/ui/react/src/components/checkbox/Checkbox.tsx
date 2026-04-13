import { forwardRef, type ReactNode } from 'react'
import { useCheckbox, type UseCheckboxProps } from './use-checkbox'
import { mergeRefs } from '../../utils/merge-refs'
import styles from './Checkbox.module.css'

export interface CheckboxProps extends UseCheckboxProps {
  children?: ReactNode
  className?: string
}

/**
 * Styled checkbox with custom visual, indeterminate state, and label.
 *
 * @example
 * ```tsx
 * <Checkbox onChange={(checked) => setAccepted(checked)}>Accept terms</Checkbox>
 * <Checkbox isChecked={true} isIndeterminate>Partial</Checkbox>
 * ```
 */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { children, className, ...rest },
  ref,
) {
  const { inputProps, inputRef, labelProps, isChecked } = useCheckbox(rest)
  const isIndeterminate = rest.isIndeterminate ?? false
  const visualState = isIndeterminate ? styles.indeterminate : (isChecked ? styles.checked : '')

  return (
    <label {...labelProps} className={`${styles.wrapper} ${className ?? ''}`.trim()}>
      <input {...inputProps} ref={mergeRefs(ref, inputRef)} className={styles.input} />
      <span className={`${styles.control} ${visualState}`} aria-hidden="true">
        {isIndeterminate ? (
          <svg viewBox="0 0 12 2" fill="none" className={styles.icon}>
            <path d="M1 1H11" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        ) : isChecked ? (
          <svg viewBox="0 0 12 10" fill="none" className={styles.icon}>
            <path d="M1 5L4.5 8.5L11 1.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : null}
      </span>
      {children ? <span className={styles.label}>{children}</span> : null}
    </label>
  )
})
