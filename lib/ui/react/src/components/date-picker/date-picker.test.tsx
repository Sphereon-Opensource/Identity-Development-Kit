import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {DatePicker} from './DatePicker'

describe('DatePicker', () => {
  it('renders with label', () => {
    render(<DatePicker label="DOB" />)
    expect(screen.getByLabelText('DOB')).toBeDefined()
  })

  it('renders type=date', () => {
    render(<DatePicker label="DOB" />)
    expect(screen.getByLabelText('DOB').getAttribute('type')).toBe('date')
  })

  it('emits the ISO-8601 value on change', () => {
    const onChange = vi.fn()
    render(<DatePicker label="DOB" onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('DOB'), {target: {value: '2026-04-05'}})
    expect(onChange).toHaveBeenLastCalledWith('2026-04-05')
  })

  it('forwards min/max bounds', () => {
    render(<DatePicker label="DOB" min="1900-01-01" max="2099-12-31" />)
    const input = screen.getByLabelText('DOB')
    expect(input.getAttribute('min')).toBe('1900-01-01')
    expect(input.getAttribute('max')).toBe('2099-12-31')
  })

  it('emits empty string when cleared', () => {
    const onChange = vi.fn()
    render(<DatePicker label="DOB" value="2026-04-05" onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('DOB'), {target: {value: ''}})
    expect(onChange).toHaveBeenLastCalledWith('')
  })
})
