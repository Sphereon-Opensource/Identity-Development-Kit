import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {NumberInput} from './NumberInput'

describe('NumberInput', () => {
  it('renders with label', () => {
    render(<NumberInput label="Age" />)
    expect(screen.getByLabelText('Age')).toBeDefined()
  })

  it('emits number for parseable input', () => {
    const onChange = vi.fn()
    render(<NumberInput label="Age" onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('Age'), {target: {value: '42'}})
    expect(onChange).toHaveBeenLastCalledWith(42)
  })

  it('emits undefined for empty input', () => {
    const onChange = vi.fn()
    render(<NumberInput label="Age" value={5} onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('Age'), {target: {value: ''}})
    expect(onChange).toHaveBeenLastCalledWith(undefined)
  })

  it('renders type=number with inputmode=decimal', () => {
    render(<NumberInput label="Age" />)
    const input = screen.getByLabelText('Age')
    expect(input.getAttribute('type')).toBe('number')
    expect(input.getAttribute('inputmode')).toBe('decimal')
  })

  it('forwards min/max/step', () => {
    render(<NumberInput label="Age" min={0} max={120} step={1} />)
    const input = screen.getByLabelText('Age')
    expect(input.getAttribute('min')).toBe('0')
    expect(input.getAttribute('max')).toBe('120')
    expect(input.getAttribute('step')).toBe('1')
  })

  it('shows error message when invalid', () => {
    render(<NumberInput label="Age" isInvalid errorMessage="Out of range" />)
    expect(screen.getByText('Out of range')).toBeDefined()
  })
})
