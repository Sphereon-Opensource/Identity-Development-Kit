import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {SelectField} from './SelectField'
import {optionsFromEnum} from './options'

// ── fixtures ──────────────────────────────────────────────────────────────────

const VALUES = ['client_secret_basic', 'private_key_jwt'] as const
type AuthMethod = (typeof VALUES)[number]

const labelMap: Record<AuthMethod, string> = {
  client_secret_basic: 'Client secret (Basic)',
  private_key_jwt: 'Private key JWT',
}

const descriptionMap: Record<AuthMethod, string> = {
  client_secret_basic: 'Sends the secret in the HTTP Basic Authorization header',
  private_key_jwt: 'Signs a JWT assertion with a private key',
}

// ── optionsFromEnum ───────────────────────────────────────────────────────────

describe('optionsFromEnum', () => {
  it('maps enum values to options with friendly labels', () => {
    const options = optionsFromEnum(VALUES, (v) => labelMap[v])
    expect(options).toEqual([
      {value: 'client_secret_basic', label: 'Client secret (Basic)'},
      {value: 'private_key_jwt', label: 'Private key JWT'},
    ])
  })

  it('includes description when the description accessor is provided', () => {
    const options = optionsFromEnum(VALUES, (v) => labelMap[v], (v) => descriptionMap[v])
    expect(options[0].description).toBe('Sends the secret in the HTTP Basic Authorization header')
    expect(options[1].description).toBe('Signs a JWT assertion with a private key')
  })

  it('omits description key entirely when no description accessor is given', () => {
    const options = optionsFromEnum(VALUES, (v) => labelMap[v])
    expect('description' in options[0]).toBe(false)
    expect('description' in options[1]).toBe(false)
  })

  it('works with numeric enum values', () => {
    const options = optionsFromEnum([1, 2, 3] as const, (v) => `Level ${v}`)
    expect(options).toEqual([
      {value: 1, label: 'Level 1'},
      {value: 2, label: 'Level 2'},
      {value: 3, label: 'Level 3'},
    ])
  })
})

// ── SelectField ───────────────────────────────────────────────────────────────

describe('SelectField', () => {
  const options = optionsFromEnum(VALUES, (v) => labelMap[v])

  // ── label & placeholder ───────────────────────────────────────────────────

  it('renders a visible label', () => {
    render(<SelectField options={options} label="Auth Method" />)
    expect(screen.getByText('Auth Method')).toBeDefined()
  })

  it('shows the placeholder when no value is selected', () => {
    render(<SelectField options={options} placeholder="Choose an auth method" />)
    expect(screen.getByText('Choose an auth method')).toBeDefined()
  })

  it('shows the default Select placeholder when none is provided', () => {
    render(<SelectField options={options} />)
    expect(screen.getByText('Select...')).toBeDefined()
  })

  // ── controlled value display ──────────────────────────────────────────────

  it('trigger shows the selected option LABEL — not the raw enum value', () => {
    render(<SelectField options={options} value="client_secret_basic" />)
    expect(screen.getByText('Client secret (Basic)')).toBeDefined()
    expect(screen.queryByText('client_secret_basic')).toBeNull()
  })

  it('trigger shows the correct label when a different option is selected', () => {
    render(<SelectField options={options} value="private_key_jwt" />)
    expect(screen.getByText('Private key JWT')).toBeDefined()
    expect(screen.queryByText('private_key_jwt')).toBeNull()
  })

  // ── menu / open state ─────────────────────────────────────────────────────

  it('opening the menu lists option LABELS (not raw enum values)', async () => {
    const user = userEvent.setup()
    render(<SelectField options={options} />)
    await user.click(screen.getByRole('button'))
    expect(screen.getByRole('listbox')).toBeDefined()
    expect(screen.getByText('Client secret (Basic)')).toBeDefined()
    expect(screen.getByText('Private key JWT')).toBeDefined()
    expect(screen.queryByText('client_secret_basic')).toBeNull()
    expect(screen.queryByText('private_key_jwt')).toBeNull()
  })

  it('shows all options in the menu', async () => {
    const user = userEvent.setup()
    render(<SelectField options={options} />)
    await user.click(screen.getByRole('button'))
    expect(screen.getAllByRole('option')).toHaveLength(2)
  })

  // ── onChange value contract ───────────────────────────────────────────────

  it('onChange is called with the option VALUE (not the label) on selection', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SelectField options={options} onChange={onChange} />)
    await user.click(screen.getByRole('button'))
    await user.click(screen.getByText('Client secret (Basic)'))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange).toHaveBeenCalledWith('client_secret_basic')
    expect(onChange).not.toHaveBeenCalledWith('Client secret (Basic)')
  })

  it('onChange is called with the correct value for the second option', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SelectField options={options} onChange={onChange} />)
    await user.click(screen.getByRole('button'))
    await user.click(screen.getByText('Private key JWT'))
    expect(onChange).toHaveBeenCalledWith('private_key_jwt')
  })

  it('menu closes after selection', async () => {
    const user = userEvent.setup()
    render(<SelectField options={options} />)
    await user.click(screen.getByRole('button'))
    await user.click(screen.getByText('Client secret (Basic)'))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('trigger shows label after uncontrolled selection', async () => {
    const user = userEvent.setup()
    render(<SelectField options={options} placeholder="Pick one" />)
    await user.click(screen.getByRole('button'))
    await user.click(screen.getByText('Private key JWT'))
    expect(screen.getByText('Private key JWT')).toBeDefined()
    expect(screen.queryByText('Pick one')).toBeNull()
  })

  // ── defaultValue (uncontrolled) ───────────────────────────────────────────

  it('respects defaultValue as initial selection', () => {
    render(<SelectField options={options} defaultValue="private_key_jwt" />)
    expect(screen.getByText('Private key JWT')).toBeDefined()
    expect(screen.queryByText('Select...')).toBeNull()
  })

  // ── error / invalid state ─────────────────────────────────────────────────

  it('renders errorMessage as role=alert', () => {
    render(<SelectField options={options} errorMessage="This field is required" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toBe('This field is required')
  })

  it('does not render role=alert when there is no errorMessage', () => {
    render(<SelectField options={options} />)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('invalid wrapper div carries data-invalid when errorMessage is set', () => {
    const {container} = render(<SelectField options={options} errorMessage="Oops" />)
    // The styled Select does not forward aria-invalid to its trigger button.
    // We expose the invalid state via data-invalid on a wrapper div so that
    // consumers can assert it.  The follow-up task is to add triggerProps
    // pass-through to Select.
    expect(container.querySelector('[data-invalid]')).not.toBeNull()
  })

  it('data-invalid is absent when the field is valid', () => {
    const {container} = render(<SelectField options={options} />)
    expect(container.querySelector('[data-invalid]')).toBeNull()
  })

  it('data-invalid present when isInvalid=true (no errorMessage)', () => {
    const {container} = render(<SelectField options={options} isInvalid />)
    expect(container.querySelector('[data-invalid]')).not.toBeNull()
  })

  // ── required indicator ────────────────────────────────────────────────────

  it('renders required asterisk when isRequired', () => {
    render(<SelectField options={options} label="Auth Method" isRequired />)
    expect(screen.getByText('*')).toBeDefined()
  })

  it('does not render asterisk when not required', () => {
    render(<SelectField options={options} label="Auth Method" />)
    expect(screen.queryByText('*')).toBeNull()
  })

  // ── disabled state ────────────────────────────────────────────────────────

  it('does not open when disabled', async () => {
    const user = userEvent.setup()
    render(<SelectField options={options} isDisabled />)
    await user.click(screen.getByRole('button'))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('trigger carries aria-disabled when isDisabled (aria-disabled IS wired via useSelect)', () => {
    render(<SelectField options={options} isDisabled />)
    const trigger = screen.getByRole('button')
    expect(trigger.getAttribute('aria-disabled')).toBe('true')
  })

  // ── description ───────────────────────────────────────────────────────────

  it('renders description text when provided', () => {
    render(<SelectField options={options} description="Choose how clients authenticate" />)
    expect(screen.getByText('Choose how clients authenticate')).toBeDefined()
  })

  // ── a11y wiring — label association and aria attributes on trigger ─────────

  it('label htmlFor matches the trigger button id', () => {
    render(<SelectField options={options} label="Auth Method" />)
    const label = screen.getByText('Auth Method').closest('label') as HTMLLabelElement
    const trigger = screen.getByRole('button')
    expect(label.htmlFor).toBeTruthy()
    expect(label.htmlFor).toBe(trigger.id)
  })

  it('trigger has aria-invalid when errorMessage is set', () => {
    render(<SelectField options={options} errorMessage="This field is required" />)
    const trigger = screen.getByRole('button')
    expect(trigger.getAttribute('aria-invalid')).toBe('true')
  })

  it('trigger has aria-invalid when isInvalid=true (no errorMessage)', () => {
    render(<SelectField options={options} isInvalid />)
    const trigger = screen.getByRole('button')
    expect(trigger.getAttribute('aria-invalid')).toBe('true')
  })

  it('trigger aria-invalid is absent when the field is valid', () => {
    render(<SelectField options={options} />)
    const trigger = screen.getByRole('button')
    expect(trigger.getAttribute('aria-invalid')).toBeNull()
  })

  it('trigger aria-describedby includes the description element id when description is provided', () => {
    render(<SelectField options={options} description="Choose how clients authenticate" />)
    const trigger = screen.getByRole('button')
    const descEl = screen.getByText('Choose how clients authenticate')
    const descId = descEl.id
    expect(descId).toBeTruthy()
    const describedBy = trigger.getAttribute('aria-describedby') ?? ''
    expect(describedBy.split(' ')).toContain(descId)
  })

  it('trigger aria-required is set when isRequired=true', () => {
    render(<SelectField options={options} isRequired />)
    const trigger = screen.getByRole('button')
    expect(trigger.getAttribute('aria-required')).toBe('true')
  })

  it('trigger aria-required is absent when isRequired is not set', () => {
    render(<SelectField options={options} />)
    const trigger = screen.getByRole('button')
    expect(trigger.getAttribute('aria-required')).toBeNull()
  })

  it('label htmlFor matches trigger id when an explicit id is passed', () => {
    render(<SelectField options={options} label="Method" id="my-field" />)
    const label = screen.getByText('Method').closest('label') as HTMLLabelElement
    const trigger = screen.getByRole('button')
    expect(trigger.id).toBe('my-field')
    expect(label.htmlFor).toBe('my-field')
  })
})
