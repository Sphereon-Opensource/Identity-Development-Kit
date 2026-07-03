import {Field} from '../field'
import {SegmentedControl} from '../segmented-control'
import {type SegmentOption} from '../segmented-control/use-segmented-control'
import styles from './TriState.module.css'

export type TriStateValue = 'disabled' | 'supported' | 'required'

export interface TriStateProps {
  label?: string
  description?: string
  errorMessage?: string
  isRequired?: boolean
  isDisabled?: boolean
  id?: string
  className?: string
  value?: TriStateValue
  defaultValue?: TriStateValue
  onChange?: (value: TriStateValue) => void
  /** Compact density for dense tables/rows (forwarded to the SegmentedControl). Default 'md'. */
  size?: 'sm' | 'md'
  /**
   * Accessible name for the control when no visible `label` is rendered (e.g. the name lives in a
   * separate table cell). Forwarded to the underlying radiogroup.
   */
  'aria-label'?: string
  /**
   * Id of an external element that labels the control. Wins over the Field's own generated label id,
   * so a visible name rendered outside the TriState can be the radiogroup's accessible name.
   */
  'aria-labelledby'?: string
}

function OffCircleIcon() {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx={8} cy={8} r={6} />
      <path d="M4.2 4.2 11.8 11.8" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3.5 8.5 6.5 11.5 12.5 4.5" />
    </svg>
  )
}

function LockIcon() {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x={3.5} y={7} width={9} height={6.3} rx={1.3} />
      <path d="M5.5 7V5.2a2.5 2.5 0 0 1 5 0V7" />
    </svg>
  )
}

const TRISTATE_OPTIONS: SegmentOption<TriStateValue>[] = [
  {value: 'disabled', label: 'Disabled', icon: <OffCircleIcon />},
  {value: 'supported', label: 'Supported', icon: <CheckIcon />},
  {value: 'required', label: 'Required', icon: <LockIcon />},
]

function getTriStateSegmentClassName(value: TriStateValue, isActive: boolean): string | undefined {
  if (!isActive) return undefined
  if (value === 'supported') return styles.supported
  if (value === 'required') return styles.required
  return undefined
}

export function TriState({
  label,
  description,
  errorMessage,
  isRequired,
  isDisabled,
  id,
  className,
  value,
  defaultValue = 'disabled',
  onChange,
  size,
  'aria-label': ariaLabel,
  'aria-labelledby': ariaLabelledby,
}: TriStateProps) {
  return (
    <Field
      label={label}
      description={description}
      errorMessage={errorMessage}
      isRequired={isRequired}
      isDisabled={isDisabled}
      id={id}
      className={className}
    >
      {(controlProps) => (
        <SegmentedControl<TriStateValue>
          options={TRISTATE_OPTIONS}
          value={value}
          defaultValue={defaultValue}
          onChange={onChange}
          isDisabled={isDisabled}
          size={size}
          aria-label={ariaLabel}
          aria-labelledby={ariaLabelledby ?? (label ? `${controlProps.id}-label` : undefined)}
          getSegmentClassName={getTriStateSegmentClassName}
        />
      )}
    </Field>
  )
}
