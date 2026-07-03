import {type ChangeEvent, type KeyboardEvent, useRef, useState} from 'react'
import {Field} from '../field'
import {useControllableState} from '../../utils/use-controllable-state'
import styles from './ChipsField.module.css'

export interface ChipsFieldProps {
  // field
  label?: string
  description?: string
  errorMessage?: string
  isRequired?: boolean
  isInvalid?: boolean
  isDisabled?: boolean
  id?: string
  className?: string
  name?: string
  // chips
  value?: string[]
  defaultValue?: string[]
  onChange?: (value: string[]) => void
  placeholder?: string
  /** validate a candidate item; return an error string to reject, or null/undefined to accept */
  validateItem?: (item: string) => string | null | undefined
  /** keys that commit the current input as a chip; default ['Enter', ','] */
  addKeys?: string[]
  /** drop duplicate values (case-sensitive); default true */
  dedupe?: boolean
}

/**
 * Field-bound string-array editor: removable chips + an add input with optional per-item
 * validation and deduplication. Composes Field for label, description, error, and ARIA.
 *
 * @example
 * ```tsx
 * <ChipsField label="Scopes" placeholder="Add scope…" onChange={setScopes} />
 * <ChipsField label="Tags" addKeys={['Enter']} validateItem={(v) => v.length > 20 ? 'Too long' : null} />
 * ```
 */
export function ChipsField({
  label,
  description,
  errorMessage,
  isRequired,
  isInvalid,
  isDisabled,
  id,
  className,
  name,
  value,
  defaultValue,
  onChange,
  placeholder,
  validateItem,
  addKeys = ['Enter', ','],
  dedupe = true,
}: ChipsFieldProps) {
  const [chips, setChips] = useControllableState<string[]>({
    value,
    defaultValue: defaultValue ?? [],
    onChange,
  })
  const [inputValue, setInputValue] = useState('')
  const [itemError, setItemError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  function tryCommit(raw: string): boolean {
    const trimmed = raw.trim()
    if (!trimmed) return true // empty → silently ignore, treat as success to clear

    if (dedupe && chips.includes(trimmed)) {
      // already present — ignore silently
      return true
    }

    if (validateItem) {
      const err = validateItem(trimmed)
      if (err) {
        setItemError(err)
        return false
      }
    }

    setItemError(null)
    setChips((prev) => [...prev, trimmed])
    return true
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (addKeys.includes(e.key)) {
      e.preventDefault()
      const ok = tryCommit(inputValue)
      if (ok) setInputValue('')
    } else if (e.key === 'Backspace' && inputValue === '') {
      e.preventDefault()
      setChips((prev) => prev.slice(0, -1))
    }
  }

  function handleInputChange(e: ChangeEvent<HTMLInputElement>) {
    const val = e.currentTarget.value
    // Paste splitting: split on newlines and (if comma is an add key) commas
    const splitOn = addKeys.includes(',') ? /[,\n]/ : /\n/
    if (splitOn.test(val)) {
      const parts = val.split(splitOn)
      const last = parts.pop() ?? ''
      let allOk = true
      for (const part of parts) {
        const ok = tryCommit(part)
        if (!ok) {
          allOk = false
          break
        }
      }
      if (allOk) setInputValue(last)
      return
    }
    setInputValue(val)
    setItemError(null)
  }

  function removeChip(chip: string) {
    setChips((prev) => prev.filter((c) => c !== chip))
  }

  const isFieldInvalid = isInvalid ?? Boolean(errorMessage)

  return (
    <Field
      label={label}
      description={description}
      errorMessage={errorMessage}
      isRequired={isRequired}
      isInvalid={isFieldInvalid}
      isDisabled={isDisabled}
      id={id}
      className={className}
    >
      {(controlProps) => (
        <div>
          <div
            className={`${styles.container}${isFieldInvalid ? ` ${styles.invalid}` : ''}`}
            onClick={() => inputRef.current?.focus()}
          >
            {chips.map((chip) => (
              <span key={chip} className={styles.chip}>
                <span className={styles.chipText}>{chip}</span>
                <button
                  type="button"
                  className={styles.removeButton}
                  aria-label={`Remove ${chip}`}
                  disabled={isDisabled}
                  onClick={(e) => {
                    e.stopPropagation()
                    removeChip(chip)
                  }}
                >
                  ×
                </button>
              </span>
            ))}
            <input
              {...controlProps}
              ref={inputRef}
              type="text"
              className={styles.input}
              name={name}
              placeholder={chips.length === 0 ? placeholder : undefined}
              value={inputValue}
              disabled={isDisabled}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
            />
          </div>
          {itemError && (
            <p className={styles.itemError} role="status" aria-live="polite">
              {itemError}
            </p>
          )}
        </div>
      )}
    </Field>
  )
}

function validateUri(item: string): string | null {
  try {
    const url = new URL(item)
    if (url.protocol === 'http:' || url.protocol === 'https:') return null
  } catch {
    // not a valid URL
  }
  return 'Enter an absolute URL (https://…)'
}

/**
 * ChipsField preset for redirect URIs / absolute-URL lists.
 * Accepts any http/https URL; rejects anything else with a clear error.
 * The `validateItem` prop can be overridden by the consumer.
 *
 * @example
 * ```tsx
 * <UriListField label="Redirect URIs" onChange={setRedirectUris} />
 * ```
 */
export function UriListField(props: Omit<ChipsFieldProps, 'validateItem'> & {validateItem?: ChipsFieldProps['validateItem']}) {
  return <ChipsField {...props} validateItem={props.validateItem ?? validateUri} />
}
