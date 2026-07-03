import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {Select} from './Select'

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

  // ── triggerProps overrides ────────────────────────────────────────────────

  it('triggerProps.id overrides the auto-generated trigger id', () => {
    render(<Select items={items} triggerProps={{ id: 'custom-trigger' }} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger.id).toBe('custom-trigger')
  })

  it('triggerProps aria-invalid appears on the trigger button', () => {
    render(<Select items={items} triggerProps={{ id: 'x', 'aria-invalid': true }} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger.getAttribute('aria-invalid')).toBe('true')
  })

  it('triggerProps aria-describedby appears on the trigger button', () => {
    render(<Select items={items} triggerProps={{ 'aria-describedby': 'desc-1' }} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger.getAttribute('aria-describedby')).toBe('desc-1')
  })

  it('triggerProps aria-required appears on the trigger button', () => {
    render(<Select items={items} triggerProps={{ 'aria-required': true }} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger.getAttribute('aria-required')).toBe('true')
  })

  it('triggerProps do not clobber internal aria-haspopup', () => {
    // @ts-expect-error — intentionally passing a bad value to verify internal wins
    render(<Select items={items} triggerProps={{ 'aria-haspopup': 'menu' }} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger.getAttribute('aria-haspopup')).toBe('listbox')
  })

  it('triggerProps do not clobber internal aria-expanded', async () => {
    // Trigger starts closed; aria-expanded should still be false regardless of consumer value
    // @ts-expect-error — intentionally passing wrong value
    render(<Select items={items} triggerProps={{ 'aria-expanded': true }} />)
    const trigger = screen.getByRole('button', { name: /select/i })
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
  })

  it('listbox aria-labelledby matches trigger id when custom triggerProps.id is provided', async () => {
    const user = userEvent.setup()
    render(<Select items={items} triggerProps={{ id: 'my-trigger' }} />)
    await user.click(screen.getByRole('button', { name: /select/i }))
    const listbox = screen.getByRole('listbox')
    expect(listbox.getAttribute('aria-labelledby')).toBe('my-trigger')
  })

  it('open/select/keyboard still work when triggerProps is provided', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(
      <Select
        items={items}
        triggerProps={{ id: 'tr', 'aria-invalid': true, 'aria-describedby': 'd' }}
        onSelect={onSelect}
      />,
    )
    await user.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getByRole('listbox')).toBeDefined()
    await user.click(screen.getByText('Apple'))
    expect(onSelect).toHaveBeenCalledWith('Apple')
    expect(screen.queryByRole('listbox')).toBeNull()
  })
})
