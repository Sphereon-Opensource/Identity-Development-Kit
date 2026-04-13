import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {Input} from './Input'

describe('Input', () => {
  it('renders with label', () => {
    render(<Input label="Email" />)
    expect(screen.getByLabelText('Email')).toBeDefined()
  })

  it('renders with placeholder', () => {
    render(<Input placeholder="Enter email" />)
    expect(screen.getByPlaceholderText('Enter email')).toBeDefined()
  })

  it('calls onChange when typed', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Input label="Name" onChange={onChange} />)
    await user.type(screen.getByLabelText('Name'), 'hello')
    expect(onChange).toHaveBeenCalled()
    expect(onChange).toHaveBeenLastCalledWith('hello')
  })

  it('sets aria-invalid when invalid', () => {
    render(<Input label="Email" isInvalid />)
    expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBe('true')
  })

  it('does not set aria-invalid when valid', () => {
    render(<Input label="Email" />)
    expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBeNull()
  })

  it('shows error message when invalid', () => {
    render(<Input label="Email" isInvalid errorMessage="Required" />)
    expect(screen.getByText('Required')).toBeDefined()
  })

  it('does not show error message when valid', () => {
    render(<Input label="Email" errorMessage="Required" />)
    expect(screen.queryByText('Required')).toBeNull()
  })

  it('links error message via aria-describedby', () => {
    render(<Input label="Email" isInvalid errorMessage="Required" />)
    const input = screen.getByLabelText('Email')
    const errorId = input.getAttribute('aria-describedby')
    expect(errorId).toBeTruthy()
    expect(document.getElementById(errorId!)).not.toBeNull()
  })

  it('sets aria-required when required', () => {
    render(<Input label="Email" isRequired />)
    expect(screen.getByLabelText('Email').getAttribute('aria-required')).toBe('true')
  })

  it('does not set aria-required when not required', () => {
    render(<Input label="Email" />)
    expect(screen.getByLabelText('Email').getAttribute('aria-required')).toBeNull()
  })

  it('is disabled when isDisabled', () => {
    render(<Input label="Email" isDisabled />)
    expect(screen.getByLabelText('Email')).toBeDisabled()
  })

  it('is readonly when isReadOnly', () => {
    render(<Input label="Email" isReadOnly />)
    expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
  })

  it('renders with default type text', () => {
    render(<Input label="Email" />)
    expect(screen.getByLabelText('Email').getAttribute('type')).toBe('text')
  })

  it('renders with custom type', () => {
    render(<Input label="Password" type="password" />)
    expect(screen.getByLabelText('Password').getAttribute('type')).toBe('password')
  })
})
