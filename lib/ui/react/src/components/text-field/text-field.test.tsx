import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {NumberField, TextAreaField, TextField} from './TextField'

describe('TextField', () => {
  it('renders an input with the label associated (label htmlFor === input id)', () => {
    render(<TextField label="Email" />)
    const input = screen.getByLabelText('Email')
    const label = screen.getByText('Email', {selector: 'label'})
    expect(input.id).toBeTruthy()
    expect(label.getAttribute('for')).toBe(input.id)
  })

  it('typing fires onChange with the string value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<TextField label="Name" onChange={onChange} />)
    await user.type(screen.getByLabelText('Name'), 'hello')
    expect(onChange).toHaveBeenCalled()
    expect(onChange).toHaveBeenLastCalledWith('hello')
  })

  it('errorMessage shows error text with role=alert, input has aria-invalid, and invalid class', () => {
    render(<TextField label="Email" errorMessage="Required" />)
    const input = screen.getByLabelText('Email')
    expect(screen.getByRole('alert').textContent).toBe('Required')
    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(input.className).toContain('invalid')
  })

  it('multiline renders a textarea', () => {
    render(<TextField label="Notes" multiline />)
    const control = screen.getByLabelText('Notes')
    expect(control.tagName.toLowerCase()).toBe('textarea')
  })

  it('type="number" renders input with type number', () => {
    render(<TextField label="Age" type="number" />)
    expect(screen.getByLabelText('Age').getAttribute('type')).toBe('number')
  })

  it('isDisabled disables the input', () => {
    render(<TextField label="Email" isDisabled />)
    expect(screen.getByLabelText('Email')).toBeDisabled()
  })

  it('isReadOnly sets readOnly on the input', () => {
    render(<TextField label="Email" isReadOnly />)
    expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
  })

  it('isRequired sets aria-required on the input', () => {
    render(<TextField label="Email" isRequired />)
    // label text includes the asterisk when isRequired; query by role instead
    expect(screen.getByRole('textbox').getAttribute('aria-required')).toBe('true')
  })

  it('explicit isInvalid sets aria-invalid without errorMessage', () => {
    render(<TextField label="Email" isInvalid />)
    const input = screen.getByLabelText('Email')
    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(input.className).toContain('invalid')
  })

  it('renders placeholder on input', () => {
    render(<TextField label="Email" placeholder="you@example.com" />)
    expect(screen.getByPlaceholderText('you@example.com')).toBeDefined()
  })

  it('renders description text', () => {
    render(<TextField label="Email" description="Enter your email" />)
    expect(screen.getByText('Enter your email')).toBeDefined()
  })

  it('description id is in aria-describedby', () => {
    render(<TextField label="Email" description="Helper text" />)
    const input = screen.getByLabelText('Email')
    const describedBy = input.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    expect(document.getElementById(describedBy!.split(' ')[0])?.textContent).toBe('Helper text')
  })

  it('default type is text', () => {
    render(<TextField label="Name" />)
    expect(screen.getByLabelText('Name').getAttribute('type')).toBe('text')
  })

  it('renders with email type', () => {
    render(<TextField label="Email" type="email" />)
    expect(screen.getByLabelText('Email').getAttribute('type')).toBe('email')
  })

  it('renders with password type', () => {
    render(<TextField label="Password" type="password" />)
    expect(screen.getByLabelText('Password').getAttribute('type')).toBe('password')
  })

  it('applies rows to textarea when multiline', () => {
    render(<TextField label="Notes" multiline rows={6} />)
    expect(screen.getByLabelText('Notes').getAttribute('rows')).toBe('6')
  })

  it('multiline typing fires onChange', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<TextField label="Notes" multiline onChange={onChange} />)
    await user.type(screen.getByLabelText('Notes'), 'hi')
    expect(onChange).toHaveBeenLastCalledWith('hi')
  })
})

describe('NumberField', () => {
  it('renders with type="number"', () => {
    render(<NumberField label="Age" />)
    expect(screen.getByLabelText('Age').getAttribute('type')).toBe('number')
  })

  it('forwards min/max/step', () => {
    render(<NumberField label="Age" min={0} max={120} step={1} />)
    const input = screen.getByLabelText('Age')
    expect(input.getAttribute('min')).toBe('0')
    expect(input.getAttribute('max')).toBe('120')
    expect(input.getAttribute('step')).toBe('1')
  })
})

describe('TextAreaField', () => {
  it('renders a textarea', () => {
    render(<TextAreaField label="Description" />)
    const control = screen.getByLabelText('Description')
    expect(control.tagName.toLowerCase()).toBe('textarea')
  })

  it('label is associated with textarea', () => {
    render(<TextAreaField label="Bio" />)
    const textarea = screen.getByLabelText('Bio')
    const label = screen.getByText('Bio', {selector: 'label'})
    expect(label.getAttribute('for')).toBe(textarea.id)
  })
})
