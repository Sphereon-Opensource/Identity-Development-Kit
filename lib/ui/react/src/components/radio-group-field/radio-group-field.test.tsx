import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {RadioGroupField} from './RadioGroupField'

const OPTIONS = [
  {value: 'a', label: 'Option A'},
  {value: 'b', label: 'Option B'},
  {value: 'c', label: 'Option C', description: 'Extra info about C'},
] as const

describe('RadioGroupField', () => {
  it('renders all options', () => {
    render(<RadioGroupField options={[...OPTIONS]} />)
    expect(screen.getByText('Option A')).toBeDefined()
    expect(screen.getByText('Option B')).toBeDefined()
    expect(screen.getByText('Option C')).toBeDefined()
  })

  it('renders the group label', () => {
    render(<RadioGroupField label="Pick one" options={[...OPTIONS]} />)
    expect(screen.getByText('Pick one')).toBeDefined()
  })

  it('group has role="radiogroup"', () => {
    render(<RadioGroupField options={[...OPTIONS]} />)
    expect(screen.getByRole('radiogroup')).toBeDefined()
  })

  it('group aria-labelledby matches the label element id', () => {
    render(<RadioGroupField label="Pick one" options={[...OPTIONS]} />)
    const group = screen.getByRole('radiogroup')
    const labelledBy = group.getAttribute('aria-labelledby')
    expect(labelledBy).toBeTruthy()
    const labelEl = document.getElementById(labelledBy!)
    expect(labelEl?.textContent).toContain('Pick one')
  })

  it('does not set aria-labelledby when no label is provided', () => {
    render(<RadioGroupField options={[...OPTIONS]} />)
    const group = screen.getByRole('radiogroup')
    expect(group.getAttribute('aria-labelledby')).toBeNull()
  })

  it('selecting an option fires onChange with its value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<RadioGroupField options={[...OPTIONS]} onChange={onChange} />)
    await user.click(screen.getByRole('radio', {name: 'Option B'}))
    expect(onChange).toHaveBeenCalledWith('b')
  })

  it('the selected option is checked (aria-checked)', async () => {
    const user = userEvent.setup()
    render(<RadioGroupField options={[...OPTIONS]} />)
    const radioB = screen.getByRole('radio', {name: 'Option B'})
    expect(radioB).not.toBeChecked()
    await user.click(radioB)
    expect(radioB).toBeChecked()
  })

  it('the previously selected option is unchecked after selecting another', async () => {
    const user = userEvent.setup()
    render(<RadioGroupField options={[...OPTIONS]} defaultValue="a" />)
    const radioA = screen.getByRole('radio', {name: 'Option A'})
    const radioB = screen.getByRole('radio', {name: 'Option B'})
    expect(radioA).toBeChecked()
    await user.click(radioB)
    expect(radioA).not.toBeChecked()
    expect(radioB).toBeChecked()
  })

  it('renders error with role=alert when errorMessage is set', () => {
    render(<RadioGroupField options={[...OPTIONS]} errorMessage="Please select an option" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toBe('Please select an option')
  })

  it('does not render role=alert when no errorMessage', () => {
    render(<RadioGroupField options={[...OPTIONS]} />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('renders description text', () => {
    render(<RadioGroupField options={[...OPTIONS]} description="Choose wisely" />)
    expect(screen.getByText('Choose wisely')).toBeDefined()
  })

  it('renders per-option description', () => {
    render(<RadioGroupField options={[...OPTIONS]} />)
    expect(screen.getByText('Extra info about C')).toBeDefined()
  })

  it('respects controlled value', () => {
    render(<RadioGroupField options={[...OPTIONS]} value="b" onChange={() => {}} />)
    expect(screen.getByRole('radio', {name: 'Option B'})).toBeChecked()
    expect(screen.getByRole('radio', {name: 'Option A'})).not.toBeChecked()
  })

  it('renders required asterisk when isRequired', () => {
    render(<RadioGroupField label="Pick one" options={[...OPTIONS]} isRequired />)
    expect(screen.getByText('*')).toBeDefined()
  })
})
