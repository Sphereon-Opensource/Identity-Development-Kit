import {useState} from 'react'
import {Field} from '../field'
import {useControllableState} from '../../utils/use-controllable-state'
import styles from './SecretField.module.css'

export interface SecretFieldProps {
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
  // secret
  value?: string
  defaultValue?: string
  placeholder?: string
  onChange?: (value: string) => void
  /** When provided, a "Generate" button appears; calls this fn and sets the returned value */
  onGenerate?: () => string
  /** Show the reveal toggle; default true */
  canReveal?: boolean
  /** Start revealed; default false (masked) */
  defaultRevealed?: boolean
  autoComplete?: string
}

function EyeIcon() {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <circle cx="8" cy="8" r="2" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M1 1l14 14M6.5 6.677A2 2 0 0 0 9.323 9.5M3.5 3.723C2.173 4.797 1 6.5 1 8s2.5 5 7 5c1.473 0 2.764-.41 3.845-1.045M6.164 3.09A7.2 7.2 0 0 1 8 3c4.5 0 7 5 7 5a12.5 12.5 0 0 1-1.702 2.544"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * A field-bound secret input: masked by default, with an optional reveal toggle and generate button.
 * Composes the Field render-prop wrapper for label, description, error, and ARIA attributes.
 *
 * @example
 * ```tsx
 * <SecretField label="Client Secret" onGenerate={() => crypto.randomUUID()} />
 * <SecretField label="API Key" canReveal={false} />
 * ```
 */
export function SecretField({
  label,
  description,
  errorMessage,
  isRequired,
  isInvalid: isInvalidProp,
  isDisabled,
  id,
  className,
  name,
  value: valueProp,
  defaultValue = '',
  placeholder,
  onChange,
  onGenerate,
  canReveal = true,
  defaultRevealed = false,
  autoComplete = 'off',
}: SecretFieldProps) {
  const isInvalid = isInvalidProp ?? Boolean(errorMessage)
  const [value, setValue] = useControllableState({value: valueProp, defaultValue, onChange})
  const [revealed, setRevealed] = useState(defaultRevealed)

  const inputClassName = `${styles.input}${isInvalid ? ` ${styles.invalid}` : ''}`

  function handleRevealToggle() {
    setRevealed((prev) => !prev)
  }

  function handleGenerate() {
    if (!onGenerate) return
    const generated = onGenerate()
    setValue(generated)
    setRevealed(true)
  }

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
        <div className={styles.row}>
          <input
            {...controlProps}
            className={inputClassName}
            name={name}
            type={revealed ? 'text' : 'password'}
            value={value}
            placeholder={placeholder}
            autoComplete={autoComplete}
            disabled={isDisabled}
            onChange={(e) => setValue(e.currentTarget.value)}
          />
          {canReveal && (
            <button
              type="button"
              className={styles.iconButton}
              aria-label={revealed ? 'Hide secret' : 'Show secret'}
              aria-pressed={revealed}
              disabled={isDisabled}
              onClick={handleRevealToggle}
            >
              {revealed ? <EyeOffIcon /> : <EyeIcon />}
            </button>
          )}
          {onGenerate && (
            <button
              type="button"
              className={styles.generateButton}
              disabled={isDisabled}
              onClick={handleGenerate}
            >
              Generate
            </button>
          )}
        </div>
      )}
    </Field>
  )
}
