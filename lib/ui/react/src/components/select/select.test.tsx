import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { Select } from './Select'

describe('Select', () => {
  const items = ['Apple', 'Banana', 'Cherry']

  it('renders trigger with placeholder', () => {
    render(<Select items={items} placeholder="Pick fruit" />)
    expect(screen.getByText('Pick fruit')).toBeDefined()
  })

  it('renders default placeholder when none provided', () => {
    render(<Select items={items} />)
    expect(screen.getByText('Select...')).toBeDefined()
  })

  it('trigger is a button with aria-haspopup', () => {
    render(<Select items={items} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger).toBeDefined()
    expect(trigger.getAttribute('aria-haspopup')).toBe('listbox')
  })

  it('has aria-haspopup listbox', () => {
    render(<Select items={items} />)
    expect(screen.getByRole('button', { name: /select/i }).getAttribute('aria-haspopup')).toBe('listbox')
  })

  it('is not expanded initially', () => {
    render(<Select items={items} />)
    expect(screen.getByRole('button', { name: /select/i }).getAttribute('aria-expanded')).toBe('false')
  })

  it('opens menu on click', async () => {
    const user = userEvent.setup()
    render(<Select items={items} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getByRole('listbox')).toBeDefined()
  })

  it('sets aria-expanded true when open', async () => {
    const user = userEvent.setup()
    render(<Select items={items} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getByRole('button', { name: /select/i }).getAttribute('aria-expanded')).toBe('true')
  })

  it('shows all options when open', async () => {
    const user = userEvent.setup()
    render(<Select items={items} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getAllByRole('option')).toHaveLength(3)
  })

  it('selects item on click', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<Select items={items} onSelect={onSelect} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    await user.click(screen.getByText('Banana'))
    expect(onSelect).toHaveBeenCalledWith('Banana')
  })

  it('closes menu after selection', async () => {
    const user = userEvent.setup()
    render(<Select items={items} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    await user.click(screen.getByText('Banana'))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('shows selected value after selection', async () => {
    const user = userEvent.setup()
    render(<Select items={items} placeholder="Pick" />)
    await user.click(screen.getByRole('button', { name: /pick/i }))
    await user.click(screen.getByText('Cherry'))
    expect(screen.getByText('Cherry')).toBeDefined()
    expect(screen.queryByText('Pick')).toBeNull()
  })

  it('does not open when disabled', async () => {
    const user = userEvent.setup()
    render(<Select items={items} isDisabled />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('sets aria-disabled when disabled', () => {
    render(<Select items={items} isDisabled />)
    expect(screen.getByRole('button', { name: /select/i }).getAttribute('aria-disabled')).toBe('true')
  })

  it('supports custom getItemLabel', async () => {
    const items = [{ id: 1, name: 'Foo' }, { id: 2, name: 'Bar' }]
    const user = userEvent.setup()
    render(<Select items={items} getItemLabel={(item) => item.name} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getByText('Foo')).toBeDefined()
    expect(screen.getByText('Bar')).toBeDefined()
  })
})
