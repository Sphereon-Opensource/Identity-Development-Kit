import {type ReactNode} from 'react'
import {useField, type UseFieldProps, type UseFieldReturn} from './use-field'
import styles from './Field.module.css'

export interface FieldProps extends UseFieldProps {
  label?: string
  className?: string
  children: (controlProps: UseFieldReturn['controlProps']) => ReactNode
}

/**
 * Styled form-field wrapper providing label, required indicator, description, error message,
 * and correct ARIA attributes to any control via a render-prop.
 *
 * @example
 * ```tsx
 * <Field label="Email" isRequired errorMessage="Required">
 *   {(p) => <input {...p} type="email" />}
 * </Field>
 * ```
 */
export function Field({label, className, description, errorMessage, isRequired, children, ...rest}: FieldProps) {
  const {labelProps, controlProps, descriptionProps, errorProps} = useField({
    ...rest,
    isRequired,
    description,
    errorMessage,
  })

  return (
    <div className={`${styles.field}${className ? ` ${className}` : ''}`}>
      {label && (
        <label {...labelProps} className={styles.label}>
          {label}
          {isRequired && (
            <span className={styles.required} aria-hidden="true">
              *
            </span>
          )}
        </label>
      )}
      {description && (
        <p {...descriptionProps} className={styles.description}>
          {description}
        </p>
      )}
      {children(controlProps)}
      {errorMessage && (
        <p {...errorProps} className={styles.error}>
          {errorMessage}
        </p>
      )}
    </div>
  )
}
