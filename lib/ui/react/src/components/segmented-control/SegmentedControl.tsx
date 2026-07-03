import {type ReactNode} from 'react'
import {useSegmentedControl, type SegmentOption} from './use-segmented-control'
import styles from './SegmentedControl.module.css'

export type {SegmentOption} from './use-segmented-control'

export interface SegmentedControlProps<T extends string> {
  options: SegmentOption<T>[]
  value?: T
  defaultValue?: T
  onChange?: (value: T) => void
  'aria-label'?: string
  'aria-labelledby'?: string
  isDisabled?: boolean
  id?: string
  className?: string
  /**
   * Visual density. `'md'` (default) is the standard pill; `'sm'` is a compact variant with
   * reduced padding/font and intrinsic (non-stretching) width, for dense tables and inline rows.
   */
  size?: 'sm' | 'md'
  /** className applied to a segment for variant coloring (e.g. TriState semantics) */
  getSegmentClassName?: (value: T, isActive: boolean) => string | undefined
}

export function SegmentedControl<T extends string>({
  options,
  value,
  defaultValue,
  onChange,
  isDisabled,
  id,
  className,
  size = 'md',
  getSegmentClassName,
  ...ariaProps
}: SegmentedControlProps<T>) {
  const {groupRef, groupProps, getSegmentProps, selectedValue} = useSegmentedControl({
    options,
    value,
    defaultValue,
    onChange,
    isDisabled,
    id,
    'aria-label': ariaProps['aria-label'],
    'aria-labelledby': ariaProps['aria-labelledby'],
  })

  const trackClassName = [styles.track, size === 'sm' ? styles.sm : '', className]
    .filter(Boolean)
    .join(' ')

  return (
    <div
      {...groupProps}
      ref={groupRef}
      className={trackClassName}
    >
      {options.map((option) => {
        const segmentProps = getSegmentProps(option)
        const isActive = selectedValue === option.value
        const extraClass = getSegmentClassName?.(option.value, isActive)
        const classNames = [styles.segment, isActive ? styles.active : '', extraClass ?? '']
          .filter(Boolean)
          .join(' ')

        return (
          <button key={option.value} type="button" {...segmentProps} className={classNames}>
            {option.icon}
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
