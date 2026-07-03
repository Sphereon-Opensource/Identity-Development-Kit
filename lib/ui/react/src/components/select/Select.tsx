import {forwardRef, type ReactNode} from 'react'
import {useSelect, type UseSelectProps} from './use-select'
import styles from './Select.module.css'

export interface SelectProps<T> extends UseSelectProps<T> {
  placeholder?: string
  className?: string
  children?: never
  // triggerProps is inherited from UseSelectProps — listed here for documentation clarity.
  // Pass id, aria-invalid, aria-describedby, aria-required, aria-label to reach the trigger.
}

export interface SelectOptionProps {
  children: ReactNode
  className?: string
}

function SelectInner<T>(
  {
    placeholder = 'Select...',
    className,
    ...props
  }: SelectProps<T>,
  ref: React.ForwardedRef<HTMLButtonElement>,
) {
  const { triggerProps, menuProps, menuRef, getOptionProps, isOpen, highlightedIndex, selectedItem } = useSelect(props)
  const { getItemLabel = (item: T) => String(item) } = props

  return (
    <div className={`${styles.wrapper} ${className ?? ''}`.trim()}>
      <button
        {...triggerProps}
        ref={ref}
        type="button"
        className={styles.trigger}
      >
        <span className={selectedItem ? styles.value : styles.placeholder}>
          {selectedItem ? getItemLabel(selectedItem) : placeholder}
        </span>
        <span className={styles.chevron} aria-hidden="true">
          {isOpen ? '\u25B2' : '\u25BC'}
        </span>
      </button>
      {isOpen ? (
        <ul {...menuProps} ref={menuRef} className={styles.menu}>
          {props.items.map((item, i) => (
            <li
              key={i}
              {...getOptionProps(item, i)}
              className={`${styles.option} ${highlightedIndex === i ? styles.highlighted : ''} ${selectedItem === item ? styles.selected : ''}`.trim()}
            >
              {getItemLabel(item)}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

export const Select = forwardRef(SelectInner) as <T>(
  props: SelectProps<T> & { ref?: React.Ref<HTMLButtonElement> },
) => React.ReactElement | null

export function SelectOption({ children, className }: SelectOptionProps) {
  return <li className={className}>{children}</li>
}
