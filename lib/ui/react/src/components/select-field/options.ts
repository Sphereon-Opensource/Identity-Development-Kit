/**
 * Value-based option model for SelectField.
 * The UI always shows `label`; `value` is the machine-readable key returned by onChange.
 */
export interface SelectOption<T extends string | number> {
  value: T
  label: string
  description?: string
  isDisabled?: boolean
}

/**
 * Maps an enum (or any readonly array of values) to SelectOption objects using a label accessor
 * and an optional description accessor. This prevents raw enum strings from ever reaching the UI.
 *
 * @example
 * ```ts
 * const options = optionsFromEnum(
 *   ['client_secret_basic', 'private_key_jwt'] as const,
 *   (v) => ({ client_secret_basic: 'Client secret (Basic)', private_key_jwt: 'Private key JWT' }[v]),
 * )
 * // → [{ value: 'client_secret_basic', label: 'Client secret (Basic)' }, ...]
 * ```
 */
export function optionsFromEnum<T extends string | number>(
  values: readonly T[],
  label: (value: T) => string,
  description?: (value: T) => string,
): SelectOption<T>[] {
  return values.map((value) => ({
    value,
    label: label(value),
    ...(description !== undefined ? {description: description(value)} : {}),
  }))
}
