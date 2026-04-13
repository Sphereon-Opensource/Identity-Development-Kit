import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { Radio } from './Radio'
import { RadioGroup } from './RadioGroup'

describe('Radio', () => {
  it('renders with label', () => {
    render(<Radio value="a">Option A</Radio>)
    expect(screen.getByText('Option A')).toBeDefined()
  })

  it('renders radio input', () => {
    render(<Radio value="a" name="test" />)
    expect(screen.getByRole('radio')).toBeDefined()
  })

  it('can be checked', () => {
    render(<Radio value="a" name="test" checked onChange={() => {}} />)
    expect(screen.getByRole('radio')).toBeChecked()
  })

  it('is unchecked by default', () => {
    render(<Radio value="a" name="test" onChange={() => {}} />)
    expect(screen.getByRole('radio')).not.toBeChecked()
  })

  it('is disabled when isDisabled', () => {
    render(<Radio value="a" name="test" isDisabled />)
    expect(screen.getByRole('radio')).toBeDisabled()
  })

  it('calls onChange on click', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Radio value="a" name="test" onChange={onChange} />)
    await user.click(screen.getByRole('radio'))
    expect(onChange).toHaveBeenCalled()
  })

  it('renders without children', () => {
    const { container } = render(<Radio value="a" name="test" />)
    expect(container.querySelector('input[type="radio"]')).not.toBeNull()
  })
})

describe('RadioGroup', () => {
  it('renders with radiogroup role', () => {
    render(
      <RadioGroup>
        <Radio value="a" name="g">A</Radio>
        <Radio value="b" name="g">B</Radio>
      </RadioGroup>,
    )
    expect(screen.getByRole('radiogroup')).toBeDefined()
  })

  it('renders children', () => {
    render(
      <RadioGroup>
        <Radio value="a" name="g">A</Radio>
        <Radio value="b" name="g">B</Radio>
      </RadioGroup>,
    )
    expect(screen.getByText('A')).toBeDefined()
    expect(screen.getByText('B')).toBeDefined()
  })

  it('has default vertical orientation', () => {
    render(
      <RadioGroup>
        <Radio value="a" name="g">A</Radio>
      </RadioGroup>,
    )
    expect(screen.getByRole('radiogroup').getAttribute('aria-orientation')).toBe('vertical')
  })

  it('accepts horizontal orientation', () => {
    render(
      <RadioGroup orientation="horizontal">
        <Radio value="a" name="g">A</Radio>
      </RadioGroup>,
    )
    expect(screen.getByRole('radiogroup').getAttribute('aria-orientation')).toBe('horizontal')
  })
})
