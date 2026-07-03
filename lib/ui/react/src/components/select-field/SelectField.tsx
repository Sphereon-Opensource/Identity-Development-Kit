import {Field} from '../field'
import {Select} from '../select'
import type {SelectOption} from './options'
import styles from './SelectField.module.css'

export interface SelectFieldProps<T extends string | number> {
  // ── Field props ──────────────────────────────────────────────────────────
  label?: string
  description?: string
  errorMessage?: string
  isRequired?: boolean
  isInvalid?: boolean
  isDisabled?: boolean
  id?: string
  className?: string
  /** Name for a hidden <input> to support plain HTML form submission. */
  name?: string
  // ── Select props ─────────────────────────────────────────────────────────
  options: SelectOption<T>[]
  /** Controlled selected value. */
  value?: T
  /** Uncontrolled default value. */
  defaultValue?: T
  placeholder?: string
  /** Called with the option's `value` (not its label) on selection. */
  onChange?: (value: T) => void
}

/**
 * Field-bound select that NEVER renders raw enum strings.
 * Composes Field (render-prop wrapper for label/description/error/ARIA) and the existing
 * styled Select (value-based option model via getItemLabel).
 *
 * **Aria wiring** (all attributes now forwarded to the trigger via `triggerProps`):
 * - `isDisabled`       → `aria-disabled` on trigger ✓  (via UseSelectProps.isDisabled)
 * - `id`               → trigger `id` ✓  (Field controlProps.id → Select triggerProps.id)
 * - `aria-invalid`     → trigger `aria-invalid` ✓  (Field controlProps → Select triggerProps)
 * - `aria-describedby` → trigger `aria-describedby` ✓  (same)
 * - `aria-required`    → trigger `aria-required` ✓  (same)
 * - label `htmlFor`    → matches trigger `id` ✓  (Field label.htmlFor = controlProps.id)
 *
 * @example
 * ```tsx
 * const options = optionsFromEnum(
 *   ['client_secret_basic', 'private_key_jwt'] as const,
 *   (v) => labelMap[v],
 * )
 * <SelectField label="Auth method" options={options} value={method} onChange={setMethod} />
 * ```
 */
export function SelectField<T extends string | number>({
  label,
  description,
  errorMessage,
  isRequired,
  isInvalid: isInvalidProp,
  isDisabled,
  id,
  className,
  name,
  options,
  value,
  defaultValue,
  placeholder,
  onChange,
}: SelectFieldProps<T>) {
  const isInvalid = isInvalidProp ?? Boolean(errorMessage)

  const selectedItem = value !== undefined ? options.find((o) => o.value === value) : undefined
  const defaultSelectedItem =
    defaultValue !== undefined ? options.find((o) => o.value === defaultValue) : undefined

  return (
    <Field
      label={label}
      description={description}
      errorMessage={errorMessage}
      isRequired={isRequired}
      isInvalid={isInvalid}
      isDisabled={isDisabled}
      id={id}
      className={className}
    >
      {(controlProps) => (
        // controlProps carries id, aria-invalid, aria-describedby, aria-required from Field.
        // Forward them to the Select trigger so that:
        //   - label htmlFor === trigger id  (label can focus the select)
        //   - trigger reflects aria-invalid/describedby/required
        // isDisabled is still passed separately because useSelect needs it for
        // behavioural gating (not just the aria attribute).
        <div data-invalid={isInvalid || undefined}>
          <Select<SelectOption<T>>
            items={options}
            selectedItem={selectedItem}
            defaultSelectedItem={defaultSelectedItem}
            getItemLabel={(o) => o.label}
            onSelect={(o) => onChange?.(o.value)}
            isDisabled={isDisabled}
            placeholder={placeholder}
            className={isInvalid ? styles.invalid : undefined}
            triggerProps={controlProps}
          />
          {name !== undefined && (
            <input type="hidden" name={name} value={selectedItem?.value ?? ''} readOnly />
          )}
        </div>
      )}
    </Field>
  )
}
