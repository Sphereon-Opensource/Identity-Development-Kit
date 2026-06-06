import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {DateTimePicker} from './DateTimePicker'

describe('DateTimePicker', () => {
  it('renders with label', () => {
    render(<DateTimePicker label="When" />)
    expect(screen.getByLabelText('When')).toBeDefined()
  })

  it('renders type=datetime-local', () => {
    render(<DateTimePicker label="When" />)
    expect(screen.getByLabelText('When').getAttribute('type')).toBe('datetime-local')
  })

  it('emits the local datetime value on change', () => {
    const onChange = vi.fn()
    render(<DateTimePicker label="When" onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('When'), {target: {value: '2026-04-05T08:30'}})
    expect(onChange).toHaveBeenLastCalledWith('2026-04-05T08:30')
  })
})
