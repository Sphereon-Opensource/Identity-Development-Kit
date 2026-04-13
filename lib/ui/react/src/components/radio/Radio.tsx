import { forwardRef, useContext, useEffect, type ReactNode } from 'react'
import { RadioGroupContext } from './use-radio'
import styles from './Radio.module.css'

export interface RadioProps {
  value: string
  children?: ReactNode
  isDisabled?: boolean
  /** When used outside RadioGroup, manually control checked state */
  checked?: boolean
  name?: string
  onChange?: () => void
  className?: string
}

export const Radio = forwardRef<HTMLInputElement, RadioProps>(function Radio(
  { value, children, isDisabled, checked: checkedProp, name: nameProp, onChange: onChangeProp, className },
  ref,
) {
  const ctx = useContext(RadioGroupContext)

  // Register/unregister with group
  useEffect(() => {
    if (ctx) {
      ctx.registerValue(value)
      return () => ctx.unregisterValue(value)
    }
  }, [ctx, value])

  // Determine state from context or props
  const isChecked = ctx ? ctx.selectedValue === value : (checkedProp ?? false)
  const name = ctx ? (ctx as any).name || nameProp : nameProp
  const handleChange = ctx ? () => ctx.onSelect(value) : onChangeProp

  // Roving tabindex
  const tabIndex = ctx
    ? (ctx.selectedValue === value || (ctx.selectedValue === '' && ctx.values[0] === value) ? 0 : -1)
    : undefined

  const handleKeyDown = ctx
    ? (e: React.KeyboardEvent<HTMLInputElement>) => {
        const values = ctx.values
        if (values.length === 0) return
        const currentIndex = values.indexOf(value)
        const isHorizontal = ctx.orientation === 'horizontal'
        const prevKey = isHorizontal ? 'ArrowLeft' : 'ArrowUp'
        const nextKey = isHorizontal ? 'ArrowRight' : 'ArrowDown'
        let newIndex = currentIndex

        switch (e.key) {
          case prevKey:
            e.preventDefault()
            newIndex = (currentIndex - 1 + values.length) % values.length
            break
          case nextKey:
            e.preventDefault()
            newIndex = (currentIndex + 1) % values.length
            break
          case 'Home':
            e.preventDefault()
            newIndex = 0
            break
          case 'End':
            e.preventDefault()
            newIndex = values.length - 1
            break
          default:
            return
        }

        ctx.onSelect(values[newIndex])
        // Move focus to newly selected radio
        const container = e.currentTarget.closest('[role="radiogroup"]')
        const radios = container?.querySelectorAll<HTMLInputElement>('input[type="radio"]')
        radios?.[newIndex]?.focus()
      }
    : undefined

  return (
    <label className={`${styles.wrapper} ${className ?? ''}`.trim()}>
      <input
        ref={ref}
        type="radio"
        name={name}
        value={value}
        checked={isChecked}
        disabled={isDisabled}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        tabIndex={tabIndex}
        className={styles.input}
      />
      <span className={`${styles.control} ${isChecked ? styles.selected : ''}`} aria-hidden="true">
        {isChecked ? <span className={styles.indicator} /> : null}
      </span>
      {children ? <span className={styles.label}>{children}</span> : null}
    </label>
  )
})
