import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {Field} from './Field'

describe('Field', () => {
  it('renders the label and associates it with the control via htmlFor', () => {
    render(<Field label="Email">{(p) => <input {...p} />}</Field>)
    const input = screen.getByRole('textbox')
    const label = screen.getByText('Email', {selector: 'label'})
    expect(label.getAttribute('for')).toBe(input.id)
  })

  it('renders required asterisk when isRequired', () => {
    render(
      <Field label="Email" isRequired>
        {(p) => <input {...p} />}
      </Field>,
    )
    expect(screen.getByText('*')).toBeDefined()
  })

  it('sets aria-required on control when isRequired', () => {
    render(
      <Field label="Email" isRequired>
        {(p) => <input {...p} />}
      </Field>,
    )
    expect(screen.getByRole('textbox').getAttribute('aria-required')).toBe('true')
  })

  it('does not set aria-required when not required', () => {
    render(<Field label="Email">{(p) => <input {...p} />}</Field>)
    expect(screen.getByRole('textbox').getAttribute('aria-required')).toBeNull()
  })

  it('renders description and includes its id in aria-describedby', () => {
    render(
      <Field label="Email" description="Enter your email address">
        {(p) => <input {...p} />}
      </Field>,
    )
    expect(screen.getByText('Enter your email address')).toBeDefined()
    const input = screen.getByRole('textbox')
    const describedBy = input.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    const descEl = document.getElementById(describedBy!.split(' ')[0])
    expect(descEl?.textContent).toBe('Enter your email address')
  })

  it('renders error with role="alert" and sets aria-invalid when errorMessage is set', () => {
    render(
      <Field label="Email" errorMessage="Required">
        {(p) => <input {...p} />}
      </Field>,
    )
    const errorEl = screen.getByRole('alert')
    expect(errorEl.textContent).toBe('Required')
    expect(screen.getByRole('textbox').getAttribute('aria-invalid')).toBe('true')
  })

  it('includes error id in aria-describedby when errorMessage is set', () => {
    render(
      <Field label="Email" errorMessage="Required">
        {(p) => <input {...p} />}
      </Field>,
    )
    const input = screen.getByRole('textbox')
    const describedBy = input.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    const ids = describedBy!.split(' ')
    const errorEl = ids.map((id) => document.getElementById(id)).find((el) => el?.textContent === 'Required')
    expect(errorEl).not.toBeNull()
  })

  it('does not set aria-invalid when no errorMessage and isInvalid not set', () => {
    render(<Field label="Email">{(p) => <input {...p} />}</Field>)
    expect(screen.getByRole('textbox').getAttribute('aria-invalid')).toBeNull()
  })

  it('does not render error element when no errorMessage', () => {
    render(<Field label="Email">{(p) => <input {...p} />}</Field>)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('includes both description and error ids in aria-describedby when both present', () => {
    render(
      <Field label="Email" description="Enter email" errorMessage="Required">
        {(p) => <input {...p} />}
      </Field>,
    )
    const input = screen.getByRole('textbox')
    const describedBy = input.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    const parts = describedBy!.split(' ')
    expect(parts).toHaveLength(2)
    // first is description, second is error
    expect(document.getElementById(parts[0])?.textContent).toBe('Enter email')
    expect(document.getElementById(parts[1])?.textContent).toBe('Required')
  })

  it('sets aria-invalid via explicit isInvalid even without errorMessage', () => {
    render(
      <Field label="Email" isInvalid>
        {(p) => <input {...p} />}
      </Field>,
    )
    expect(screen.getByRole('textbox').getAttribute('aria-invalid')).toBe('true')
  })

  it('does not include error id in aria-describedby when isInvalid but no errorMessage', () => {
    render(
      <Field label="Email" isInvalid>
        {(p) => <input {...p} />}
      </Field>,
    )
    const input = screen.getByRole('textbox')
    // No error element rendered, so aria-describedby should be absent
    expect(input.getAttribute('aria-describedby')).toBeNull()
  })
})
