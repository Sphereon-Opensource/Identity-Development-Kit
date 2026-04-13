import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { Checkbox } from './Checkbox'

describe('Checkbox', () => {
  it('renders with label', () => {
    render(<Checkbox>Accept terms</Checkbox>)
    expect(screen.getByText('Accept terms')).toBeDefined()
  })

  it('renders checkbox role', () => {
    render(<Checkbox>Test</Checkbox>)
    expect(screen.getByRole('checkbox')).toBeDefined()
  })

  it('renders as unchecked by default', () => {
    render(<Checkbox>Test</Checkbox>)
    const input = screen.getByRole('checkbox')
    expect(input).not.toBeChecked()
  })

  it('renders as checked when defaultChecked', () => {
    render(<Checkbox defaultChecked>Test</Checkbox>)
    expect(screen.getByRole('checkbox')).toBeChecked()
  })

  it('toggles on click (uncontrolled)', async () => {
    const user = userEvent.setup()
    render(<Checkbox>Test</Checkbox>)
    const checkbox = screen.getByRole('checkbox')
    expect(checkbox).not.toBeChecked()
    await user.click(checkbox)
    expect(checkbox).toBeChecked()
  })

  it('toggles back to unchecked on second click', async () => {
    const user = userEvent.setup()
    render(<Checkbox>Test</Checkbox>)
    const checkbox = screen.getByRole('checkbox')
    await user.click(checkbox)
    await user.click(checkbox)
    expect(checkbox).not.toBeChecked()
  })

  it('calls onChange with new value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Checkbox onChange={onChange}>Test</Checkbox>)
    await user.click(screen.getByRole('checkbox'))
    expect(onChange).toHaveBeenCalledWith(true)
  })

  it('respects controlled isChecked', () => {
    render(<Checkbox isChecked={true}>Test</Checkbox>)
    expect(screen.getByRole('checkbox')).toBeChecked()
  })

  it('respects controlled isChecked=false', () => {
    render(<Checkbox isChecked={false}>Test</Checkbox>)
    expect(screen.getByRole('checkbox')).not.toBeChecked()
  })

  it('sets aria-checked mixed for indeterminate', () => {
    render(<Checkbox isIndeterminate>Test</Checkbox>)
    expect(screen.getByRole('checkbox').getAttribute('aria-checked')).toBe('mixed')
  })

  it('is disabled when isDisabled', () => {
    render(<Checkbox isDisabled>Test</Checkbox>)
    expect(screen.getByRole('checkbox')).toBeDisabled()
  })

  it('does not toggle when disabled', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<Checkbox isDisabled onChange={onChange}>Test</Checkbox>)
    await user.click(screen.getByRole('checkbox'))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('renders without children', () => {
    const { container } = render(<Checkbox />)
    expect(container.querySelector('input[type="checkbox"]')).not.toBeNull()
  })
})
