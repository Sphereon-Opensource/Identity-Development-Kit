import { forwardRef, type ReactNode, type HTMLAttributes } from 'react'
import styles from './Badge.module.css'

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  children: ReactNode
  variant?: 'default' | 'error' | 'success' | 'warning' | 'info'
  className?: string
}

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(function Badge(
  { children, variant = 'default', className, ...rest },
  ref,
) {
  return (
    <span
      ref={ref}
      className={`${styles.badge} ${styles[variant]} ${className ?? ''}`.trim()}
      data-variant={variant}
      {...rest}
    >
      {children}
    </span>
  )
})
