import {type ButtonHTMLAttributes, type KeyboardEvent, useCallback, useState} from 'react'

/** Props for the {@link useButton} hook. */
export interface UseButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost' | 'outline'
  size?: 'sm' | 'md' | 'lg'
  isDisabled?: boolean
  isLoading?: boolean
  onClick?: () => void
}

/** Return value of the {@link useButton} hook. */
export interface UseButtonReturn {
  buttonProps: ButtonHTMLAttributes<HTMLButtonElement>
  isPressed: boolean
}

/**
 * Headless button hook providing behavior, keyboard interaction, and ARIA attributes.
 * Returns props to spread onto a button element.
 *
 * @example
 * ```tsx
 * const { buttonProps } = useButton({ onClick: () => console.log('clicked') })
 * return <button {...buttonProps}>Click me</button>
 * ```
 */
export function useButton(props: UseButtonProps = {}): UseButtonReturn {
  const { isDisabled = false, isLoading = false, onClick } = props
  const [isPressed, setIsPressed] = useState(false)

  const handleClick = useCallback(() => {
    if (!isDisabled && !isLoading) {
      onClick?.()
    }
  }, [isDisabled, isLoading, onClick])

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.key === ' ' || e.key === 'Enter') {
      e.preventDefault()
      setIsPressed(true)
    }
  }, [])

  const handleKeyUp = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === ' ' || e.key === 'Enter') {
        setIsPressed(false)
        handleClick()
      }
    },
    [handleClick],
  )

  return {
    buttonProps: {
      type: 'button',
      disabled: isDisabled || isLoading,
      'aria-busy': isLoading || undefined,
      onClick: handleClick,
      onKeyDown: handleKeyDown as unknown as ButtonHTMLAttributes<HTMLButtonElement>['onKeyDown'],
      onKeyUp: handleKeyUp as unknown as ButtonHTMLAttributes<HTMLButtonElement>['onKeyUp'],
      onPointerDown: () => setIsPressed(true),
      onPointerUp: () => setIsPressed(false),
      onPointerLeave: () => setIsPressed(false),
    },
    isPressed,
  }
}
