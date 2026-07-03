import {useField} from '../field/use-field'
import {RadioGroup} from '../radio/RadioGroup'
import {Radio} from '../radio/Radio'
import fieldStyles from '../field/Field.module.css'
import styles from './RadioGroupField.module.css'

export interface RadioOption<T extends string = string> {
  value: T
  label: string
  /** Optional per-option description rendered below the option label. */
  description?: string
  isDisabled?: boolean
}

export interface RadioGroupFieldProps<T extends string = string> {
  label?: string
  description?: string
  errorMessage?: string
  isRequired?: boolean
  isDisabled?: boolean
  id?: string
  className?: string
  name?: string
  options: RadioOption<T>[]
  value?: T
  defaultValue?: T
  orientation?: 'vertical' | 'horizontal'
  onChange?: (value: T) => void
}

/**
 * A radio group with a group label, per-option descriptions, and field-level
 * description + error message. Composes `useField` for ARIA wiring and the existing
 * `RadioGroup`/`Radio` primitives for the interactive controls.
 *
 * @example
 * ```tsx
 * <RadioGroupField
 *   label="Payment method"
 *   options={[
 *     { value: 'card', label: 'Credit card' },
 *     { value: 'bank', label: 'Bank transfer', description: 'Takes 1–3 days' },
 *   ]}
 *   value={method}
 *   onChange={setMethod}
 * />
 * ```
 */
export function RadioGroupField<T extends string = string>({
  label,
  description,
  errorMessage,
  isRequired = false,
  isDisabled = false,
  id,
  className,
  name,
  options,
  value,
  defaultValue,
  orientation = 'vertical',
  onChange,
}: RadioGroupFieldProps<T>) {
  const {labelProps, descriptionProps, errorProps} = useField({
    id,
    isRequired,
    isDisabled,
    errorMessage,
    description,
  })

  // The group label id is used as aria-labelledby on the RadioGroup div.
  const labelId = labelProps.id

  return (
    <div className={`${styles.root}${className ? ` ${className}` : ''}`}>
      {label && (
        <span id={labelId} className={styles.label}>
          {label}
          {isRequired && (
            <span className={styles.required} aria-hidden="true">
              *
            </span>
          )}
        </span>
      )}
      <RadioGroup
        aria-labelledby={label ? labelId : undefined}
        value={value}
        defaultValue={defaultValue}
        onChange={(v) => onChange?.(v as T)}
        orientation={orientation}
        name={name}
      >
        {options.map((option) => (
          <div key={option.value} className={styles.optionWrapper}>
            <Radio value={option.value} isDisabled={option.isDisabled ?? isDisabled}>
              {option.label}
            </Radio>
            {option.description && (
              <p className={styles.optionDescription}>{option.description}</p>
            )}
          </div>
        ))}
      </RadioGroup>
      {description && (
        <p {...descriptionProps} className={fieldStyles.description}>
          {description}
        </p>
      )}
      {errorMessage && (
        <p {...errorProps} className={fieldStyles.error}>
          {errorMessage}
        </p>
      )}
    </div>
  )
}
