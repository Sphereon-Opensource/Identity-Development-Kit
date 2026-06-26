import {forwardRef, type HTMLAttributes, type KeyboardEvent, type ReactNode} from 'react'
import styles from './Card.module.css'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  className?: string
  interactive?: boolean
  onClick?: () => void
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { children, className, interactive = false, onClick, ...rest },
  ref,
) {
  const isInteractive = interactive || !!onClick
  const handleKeyDown = !!onClick
    ? (e: KeyboardEvent<HTMLDivElement>) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick?.()
        }
      }
    : undefined

  return (
    <div
      ref={ref}
      className={`${styles.card} ${isInteractive ? styles.interactive : ''} ${className ?? ''}`.trim()}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      {...rest}
    >
      {children}
    </div>
  )
})
