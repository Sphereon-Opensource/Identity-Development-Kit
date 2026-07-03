import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {ToggleField} from './ToggleField'

describe('ToggleField', () => {
  it('renders a control with role="switch"', () => {
    render(<ToggleField label="Enable feature" />)
    expect(screen.getByRole('switch')).toBeDefined()
  })

  it('renders the inline label', () => {
    render(<ToggleField label="Dark mode" />)
    expect(screen.getByText('Dark mode')).toBeDefined()
  })

  it('is off (aria-checked="false") by default', () => {
    render(<ToggleField label="Toggle" />)
    expect(screen.getByRole('switch').getAttribute('aria-checked')).toBe('false')
  })

  it('fires onChange(true) when toggled from off', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ToggleField label="Toggle" onChange={onChange} />)
    await user.click(screen.getByRole('switch'))
    expect(onChange).toHaveBeenCalledWith(true)
  })

  it('fires onChange(false) when toggled from on', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ToggleField label="Toggle" isChecked onChange={onChange} />)
    await user.click(screen.getByRole('switch'))
    expect(onChange).toHaveBeenCalledWith(false)
  })

  it('flips aria-checked when toggled (uncontrolled)', async () => {
    const user = userEvent.setup()
    render(<ToggleField label="Toggle" />)
    const sw = screen.getByRole('switch')
    expect(sw.getAttribute('aria-checked')).toBe('false')
    await user.click(sw)
    expect(sw.getAttribute('aria-checked')).toBe('true')
    await user.click(sw)
    expect(sw.getAttribute('aria-checked')).toBe('false')
  })

  it('is disabled when isDisabled', () => {
    render(<ToggleField label="Toggle" isDisabled />)
    expect(screen.getByRole('switch')).toBeDisabled()
  })

  it('does not call onChange when disabled', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ToggleField label="Toggle" isDisabled onChange={onChange} />)
    await user.click(screen.getByRole('switch'))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('renders error with role=alert and sets aria-invalid when errorMessage is set', () => {
    render(<ToggleField label="Toggle" errorMessage="This must be enabled" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toBe('This must be enabled')
    expect(screen.getByRole('switch').getAttribute('aria-invalid')).toBe('true')
  })

  it('does not render role=alert when no errorMessage', () => {
    render(<ToggleField label="Toggle" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('renders description text', () => {
    render(<ToggleField label="Toggle" description="This controls the feature" />)
    expect(screen.getByText('This controls the feature')).toBeDefined()
  })

  it('aria-describedby on the switch includes description and error ids', () => {
    render(
      <ToggleField
        label="Toggle"
        description="Helpful hint"
        errorMessage="Must enable"
      />,
    )
    const sw = screen.getByRole('switch')
    const describedBy = sw.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    const ids = describedBy!.split(' ')
    expect(ids).toHaveLength(2)
    expect(document.getElementById(ids[0])?.textContent).toBe('Helpful hint')
    expect(document.getElementById(ids[1])?.textContent).toBe('Must enable')
  })
})
