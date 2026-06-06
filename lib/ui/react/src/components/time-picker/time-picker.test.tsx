import {fireEvent, render, screen} from '@testing-library/react'
import {describe, expect, it, vi} from 'vitest'
import {TimePicker} from './TimePicker'

describe('TimePicker', () => {
  it('renders with label', () => {
    render(<TimePicker label="Start" />)
    expect(screen.getByLabelText('Start')).toBeDefined()
  })

  it('renders type=time', () => {
    render(<TimePicker label="Start" />)
    expect(screen.getByLabelText('Start').getAttribute('type')).toBe('time')
  })

  it('emits the time value on change', () => {
    const onChange = vi.fn()
    render(<TimePicker label="Start" onChange={onChange} />)
    fireEvent.change(screen.getByLabelText('Start'), {target: {value: '08:30'}})
    expect(onChange).toHaveBeenLastCalledWith('08:30')
  })

  it('forwards step', () => {
    render(<TimePicker label="Start" step={1} />)
    expect(screen.getByLabelText('Start').getAttribute('step')).toBe('1')
  })
})
