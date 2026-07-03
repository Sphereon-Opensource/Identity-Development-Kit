import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {CheckboxField} from './CheckboxField'

describe('CheckboxField', () => {
  it('renders the checkbox and its inline label', () => {
    render(<CheckboxField label="Accept terms" />)
    expect(screen.getByRole('checkbox')).toBeDefined()
    expect(screen.getByText('Accept terms')).toBeDefined()
  })

  it('fires onChange(true) when toggled from unchecked', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CheckboxField label="Accept" onChange={onChange} />)
    await user.click(screen.getByRole('checkbox'))
    expect(onChange).toHaveBeenCalledWith(true)
  })

  it('fires onChange(false) when toggled from checked', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CheckboxField label="Accept" isChecked onChange={onChange} />)
    await user.click(screen.getByRole('checkbox'))
    expect(onChange).toHaveBeenCalledWith(false)
  })

  it('renders error with role=alert and sets aria-invalid on the checkbox when errorMessage is set', () => {
    render(<CheckboxField label="Accept" errorMessage="This field is required" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toBe('This field is required')
    expect(screen.getByRole('checkbox').getAttribute('aria-invalid')).toBe('true')
  })

  it('does not render role=alert when no errorMessage', () => {
    render(<CheckboxField label="Accept" />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('aria-describedby on the checkbox includes description and error ids', () => {
    render(
      <CheckboxField
        label="Accept"
        description="Please read before accepting"
        errorMessage="Required"
      />,
    )
    const checkbox = screen.getByRole('checkbox')
    const describedBy = checkbox.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    const ids = describedBy!.split(' ')
    expect(ids).toHaveLength(2)
    // description id comes first, error id second
    expect(document.getElementById(ids[0])?.textContent).toBe('Please read before accepting')
    expect(document.getElementById(ids[1])?.textContent).toBe('Required')
  })

  it('aria-describedby on the checkbox includes only description id when no errorMessage', () => {
    render(<CheckboxField label="Accept" description="Helpful hint" />)
    const checkbox = screen.getByRole('checkbox')
    const describedBy = checkbox.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    const ids = describedBy!.split(' ')
    expect(ids).toHaveLength(1)
    expect(document.getElementById(ids[0])?.textContent).toBe('Helpful hint')
  })

  it('sets aria-checked="mixed" when isIndeterminate', () => {
    render(<CheckboxField label="All items" isIndeterminate />)
    expect(screen.getByRole('checkbox').getAttribute('aria-checked')).toBe('mixed')
  })

  it('is disabled when isDisabled', () => {
    render(<CheckboxField label="Accept" isDisabled />)
    expect(screen.getByRole('checkbox')).toBeDisabled()
  })

  it('does not call onChange when disabled', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CheckboxField label="Accept" isDisabled onChange={onChange} />)
    await user.click(screen.getByRole('checkbox'))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('sets aria-required on the checkbox when isRequired', () => {
    render(<CheckboxField label="Accept" isRequired />)
    expect(screen.getByRole('checkbox').getAttribute('aria-required')).toBe('true')
  })
})
