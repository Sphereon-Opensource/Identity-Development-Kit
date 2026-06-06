import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {Textarea} from './Textarea'

describe('Textarea', () => {
  it('renders with label', () => {
    render(<Textarea label="Notes" />)
    expect(screen.getByLabelText('Notes')).toBeDefined()
  })

  it('renders with placeholder', () => {
    render(<Textarea placeholder="Type something" />)
    expect(screen.getByPlaceholderText('Type something')).toBeDefined()
  })

  it('calls onChange with the new value', () => {
    const onChange = vi.fn()
    render(<Textarea label="Notes" onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('Notes'), {target: {value: 'hello'}})
    expect(onChange).toHaveBeenLastCalledWith('hello')
  })

  it('sets aria-invalid and aria-describedby when invalid', () => {
    render(<Textarea label="Notes" isInvalid errorMessage="Required" />)
    const ta = screen.getByLabelText('Notes')
    expect(ta.getAttribute('aria-invalid')).toBe('true')
    const errorId = ta.getAttribute('aria-describedby')
    expect(errorId).toBeTruthy()
    expect(document.getElementById(errorId!)).not.toBeNull()
  })

  it('is disabled when isDisabled', () => {
    render(<Textarea label="Notes" isDisabled />)
    expect(screen.getByLabelText('Notes')).toBeDisabled()
  })

  it('is readonly when isReadOnly', () => {
    render(<Textarea label="Notes" isReadOnly />)
    expect(screen.getByLabelText('Notes')).toHaveAttribute('readonly')
  })
})
