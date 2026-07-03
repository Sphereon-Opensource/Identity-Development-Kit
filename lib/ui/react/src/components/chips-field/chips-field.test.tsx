import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {ChipsField, UriListField} from './ChipsField'

describe('ChipsField', () => {
  it('typing a value and pressing Enter adds a chip and calls onChange', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Scopes" onChange={onChange} />)
    const input = screen.getByLabelText('Scopes')
    await user.type(input, 'openid{Enter}')
    expect(screen.getByText('openid')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['openid'])
  })

  it('comma commits the current input as a chip', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Scopes" onChange={onChange} />)
    const input = screen.getByLabelText('Scopes')
    await user.type(input, 'profile,')
    expect(screen.getByText('profile')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['profile'])
  })

  it('duplicate value is ignored when dedupe=true (default)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Tags" onChange={onChange} />)
    const input = screen.getByLabelText('Tags')
    await user.type(input, 'foo{Enter}')
    onChange.mockClear()
    await user.type(input, 'foo{Enter}')
    // onChange should NOT be called for the duplicate
    expect(onChange).not.toHaveBeenCalled()
    // Only one chip with the value
    expect(screen.getAllByText('foo')).toHaveLength(1)
  })

  it('validateItem returning an error blocks adding and shows the item error', async () => {
    const validateItem = vi.fn().mockReturnValue('Invalid value')
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Tags" validateItem={validateItem} onChange={onChange} />)
    const input = screen.getByLabelText('Tags')
    await user.type(input, 'bad{Enter}')
    expect(screen.getByText('Invalid value')).toBeDefined()
    expect(onChange).not.toHaveBeenCalled()
    // The input is not cleared (user can fix the value)
    expect((input as HTMLInputElement).value).toBe('bad')
  })

  it('validateItem returning null accepts the value', async () => {
    const validateItem = vi.fn().mockReturnValue(null)
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Tags" validateItem={validateItem} onChange={onChange} />)
    const input = screen.getByLabelText('Tags')
    await user.type(input, 'good{Enter}')
    expect(screen.getByText('good')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['good'])
  })

  it('clicking a chip remove button removes it and calls onChange', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Scopes" defaultValue={['openid', 'profile']} onChange={onChange} />)
    const removeBtn = screen.getByRole('button', {name: 'Remove openid'})
    await user.click(removeBtn)
    expect(screen.queryByText('openid')).toBeNull()
    expect(screen.getByText('profile')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['profile'])
  })

  it('Backspace on an empty input removes the last chip', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Scopes" defaultValue={['openid', 'profile']} onChange={onChange} />)
    const input = screen.getByLabelText('Scopes')
    await user.click(input)
    await user.keyboard('{Backspace}')
    expect(screen.queryByText('profile')).toBeNull()
    expect(screen.getByText('openid')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['openid'])
  })

  it('Backspace on a non-empty input does not remove last chip', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Scopes" defaultValue={['openid']} onChange={onChange} />)
    const input = screen.getByLabelText('Scopes')
    await user.type(input, 'pro{Backspace}')
    expect(screen.getByText('openid')).toBeDefined()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('Field-level errorMessage renders role=alert and marks input aria-invalid', () => {
    render(<ChipsField label="Redirect URIs" errorMessage="At least one URI required" />)
    expect(screen.getByRole('alert').textContent).toBe('At least one URI required')
    const input = screen.getByLabelText('Redirect URIs')
    expect(input.getAttribute('aria-invalid')).toBe('true')
  })

  it('label is associated with the input via htmlFor', () => {
    render(<ChipsField label="Scopes" />)
    const input = screen.getByLabelText('Scopes')
    const label = screen.getByText('Scopes', {selector: 'label'})
    expect(input.id).toBeTruthy()
    expect(label.getAttribute('for')).toBe(input.id)
  })

  it('isRequired passes aria-required to the input', () => {
    render(<ChipsField label="Scopes" isRequired />)
    expect(screen.getByRole('textbox').getAttribute('aria-required')).toBe('true')
  })

  it('isDisabled disables the input and remove buttons', () => {
    render(<ChipsField label="Scopes" defaultValue={['openid']} isDisabled />)
    expect(screen.getByRole('textbox')).toBeDisabled()
    expect(screen.getByRole('button', {name: 'Remove openid'})).toBeDisabled()
  })

  it('renders with defaultValue chips pre-populated', () => {
    render(<ChipsField label="Scopes" defaultValue={['openid', 'profile', 'email']} />)
    expect(screen.getByText('openid')).toBeDefined()
    expect(screen.getByText('profile')).toBeDefined()
    expect(screen.getByText('email')).toBeDefined()
  })

  it('controlled mode: value prop drives chips', () => {
    const {rerender} = render(<ChipsField label="Scopes" value={['openid']} />)
    expect(screen.getByText('openid')).toBeDefined()
    rerender(<ChipsField label="Scopes" value={['openid', 'profile']} />)
    expect(screen.getByText('profile')).toBeDefined()
  })

  it('trims whitespace before committing', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Tags" onChange={onChange} />)
    const input = screen.getByLabelText('Tags')
    await user.type(input, '  hello  {Enter}')
    expect(screen.getByText('hello')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['hello'])
  })

  it('pressing Enter on empty input does nothing', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ChipsField label="Tags" onChange={onChange} />)
    const input = screen.getByLabelText('Tags')
    await user.click(input)
    await user.keyboard('{Enter}')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('placeholder appears on the input', () => {
    render(<ChipsField label="Scopes" placeholder="Add a scope…" />)
    expect(screen.getByPlaceholderText('Add a scope…')).toBeDefined()
  })

  it('description renders and is in aria-describedby', () => {
    render(<ChipsField label="Tags" description="Separate by Enter or comma" />)
    expect(screen.getByText('Separate by Enter or comma')).toBeDefined()
    const input = screen.getByLabelText('Tags')
    expect(input.getAttribute('aria-describedby')).toBeTruthy()
  })
})

describe('UriListField', () => {
  it('rejects a non-URL value with the URL error message', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<UriListField label="Redirect URIs" onChange={onChange} />)
    const input = screen.getByLabelText('Redirect URIs')
    await user.type(input, 'not-a-url{Enter}')
    expect(screen.getByText('Enter an absolute URL (https://…)')).toBeDefined()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('accepts a valid https URL', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<UriListField label="Redirect URIs" onChange={onChange} />)
    const input = screen.getByLabelText('Redirect URIs')
    await user.type(input, 'https://app.test/cb{Enter}')
    expect(screen.getByText('https://app.test/cb')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['https://app.test/cb'])
  })

  it('accepts a valid http URL', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<UriListField label="Redirect URIs" onChange={onChange} />)
    const input = screen.getByLabelText('Redirect URIs')
    await user.type(input, 'http://localhost:3000/callback{Enter}')
    expect(screen.getByText('http://localhost:3000/callback')).toBeDefined()
    expect(onChange).toHaveBeenCalledWith(['http://localhost:3000/callback'])
  })

  it('rejects a non-http/https URL (e.g. ftp://)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<UriListField label="Redirect URIs" onChange={onChange} />)
    const input = screen.getByLabelText('Redirect URIs')
    await user.type(input, 'ftp://files.example.com{Enter}')
    expect(screen.getByText('Enter an absolute URL (https://…)')).toBeDefined()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('consumer can override validateItem', async () => {
    const validateItem = vi.fn().mockReturnValue(null)
    const user = userEvent.setup()
    render(<UriListField label="URIs" validateItem={validateItem} />)
    const input = screen.getByLabelText('URIs')
    await user.type(input, 'not-a-url{Enter}')
    // Custom validator accepted it — no error shown
    expect(screen.queryByText('Enter an absolute URL (https://…)')).toBeNull()
    expect(screen.getByText('not-a-url')).toBeDefined()
  })
})
