import type {ReactElement} from 'react'

export interface FieldError {
  /** id of the invalid control to anchor to. */
  id: string
  /** Human-readable label of the field. */
  label: string
  /** The validation message. */
  message: string
}

/**
 * Focusable summary of field-level validation errors with in-page anchor links.
 * Renders nothing when the errors array is empty.
 * Place above the form so keyboard and screen-reader users encounter it first.
 */
export function FormErrorSummary({
  errors,
  title = 'Please fix the following:',
}: {
  errors: FieldError[]
  title?: string
}): ReactElement | null {
  if (errors.length === 0) return null

  return (
    <div role="alert" tabIndex={-1} data-testid="form-error-summary">
      <p>{title}</p>
      <ul>
        {errors.map((error) => (
          <li key={error.id}>
            <a href={`#${error.id}`}>
              {error.label}: {error.message}
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}
