import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { useButton, type UseButtonProps } from './use-button'
import styles from './Button.module.css'

export interface ButtonProps extends UseButtonProps, Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onClick'> {
  children: ReactNode
}

/**
 * Styled button component with variant and size support.
 * Uses CSS custom properties from the theme's component tokens.
 *
 * @example
 * ```tsx
 * <Button variant="primary" onClick={() => {}}>Submit</Button>
 * <Button variant="ghost" size="sm">Cancel</Button>
 * ```
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { children, className, variant = 'primary', size = 'md', isDisabled, isLoading, onClick, ...htmlProps },
  ref,
) {
  const { buttonProps } = useButton({ variant, size, isDisabled, isLoading, onClick })
  return (
    <button
      {...htmlProps}
      {...buttonProps}
      ref={ref}
      className={`${styles.button} ${styles[variant]} ${styles[size]} ${className ?? ''}`.trim()}
      data-variant={variant}
      data-size={size}
    >
      {isLoading ? <span className={styles.spinner} aria-hidden="true" /> : null}
      {children}
    </button>
  )
})
