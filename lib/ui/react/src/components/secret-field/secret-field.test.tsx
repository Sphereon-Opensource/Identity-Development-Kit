import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {SecretField} from './SecretField'

describe('SecretField', () => {
  it('input is masked by default (type="password")', () => {
    render(<SecretField label="Secret" />)
    expect(screen.getByLabelText('Secret').getAttribute('type')).toBe('password')
  })

  it('reveal toggle changes type to "text" and aria-pressed becomes true', async () => {
    const user = userEvent.setup()
    render(<SecretField label="Secret" />)
    const input = screen.getByLabelText('Secret')
    const revealBtn = screen.getByRole('button', {name: 'Show secret'})

    expect(revealBtn.getAttribute('aria-pressed')).toBe('false')
    await user.click(revealBtn)

    expect(input.getAttribute('type')).toBe('text')
    expect(screen.getByRole('button', {name: 'Hide secret'}).getAttribute('aria-pressed')).toBe('true')
  })

  it('clicking reveal again re-masks the input', async () => {
    const user = userEvent.setup()
    render(<SecretField label="Secret" />)
    const input = screen.getByLabelText('Secret')

    await user.click(screen.getByRole('button', {name: 'Show secret'}))
    expect(input.getAttribute('type')).toBe('text')

    await user.click(screen.getByRole('button', {name: 'Hide secret'}))
    expect(input.getAttribute('type')).toBe('password')
    expect(screen.getByRole('button', {name: 'Show secret'}).getAttribute('aria-pressed')).toBe('false')
  })

  it('typing fires onChange with the string value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SecretField label="Secret" onChange={onChange} />)
    await user.type(screen.getByLabelText('Secret'), 'abc')
    expect(onChange).toHaveBeenCalled()
    expect(onChange).toHaveBeenLastCalledWith('abc')
  })

  it('with onGenerate: clicking Generate sets value, fires onChange, and reveals input', async () => {
    const onChange = vi.fn()
    const onGenerate = vi.fn(() => 's3cret')
    const user = userEvent.setup()
    render(<SecretField label="Secret" onChange={onChange} onGenerate={onGenerate} />)

    await user.click(screen.getByRole('button', {name: 'Generate'}))

    expect(onGenerate).toHaveBeenCalledOnce()
    expect(onChange).toHaveBeenCalledWith('s3cret')
    const input = screen.getByLabelText('Secret') as HTMLInputElement
    expect(input.value).toBe('s3cret')
    // after generating, input should be revealed
    expect(input.getAttribute('type')).toBe('text')
  })

  it('no onGenerate → no Generate button', () => {
    render(<SecretField label="Secret" />)
    expect(screen.queryByRole('button', {name: 'Generate'})).toBeNull()
  })

  it('canReveal=false → no reveal button', () => {
    render(<SecretField label="Secret" canReveal={false} />)
    expect(screen.queryByRole('button', {name: /show secret|hide secret/i})).toBeNull()
  })

  it('errorMessage shows role=alert and input has aria-invalid', () => {
    render(<SecretField label="Secret" errorMessage="Too short" />)
    expect(screen.getByRole('alert').textContent).toBe('Too short')
    expect(screen.getByLabelText('Secret').getAttribute('aria-invalid')).toBe('true')
  })

  it('isDisabled disables input and buttons', () => {
    render(<SecretField label="Secret" isDisabled onGenerate={() => 'x'} />)
    expect(screen.getByLabelText('Secret')).toBeDisabled()
    for (const btn of screen.getAllByRole('button')) {
      expect(btn).toBeDisabled()
    }
  })

  it('label is associated with the input', () => {
    render(<SecretField label="API Key" />)
    const input = screen.getByLabelText('API Key')
    const label = screen.getByText('API Key', {selector: 'label'})
    expect(label.getAttribute('for')).toBe(input.id)
  })

  it('defaultRevealed=true starts with type="text"', () => {
    render(<SecretField label="Secret" defaultRevealed />)
    expect(screen.getByLabelText('Secret').getAttribute('type')).toBe('text')
    expect(screen.getByRole('button', {name: 'Hide secret'}).getAttribute('aria-pressed')).toBe('true')
  })

  it('controlled value is reflected in the input', () => {
    render(<SecretField label="Secret" value="myvalue" onChange={() => {}} />)
    expect((screen.getByLabelText('Secret') as HTMLInputElement).value).toBe('myvalue')
  })

  it('placeholder is rendered', () => {
    render(<SecretField label="Secret" placeholder="Enter secret" />)
    expect(screen.getByPlaceholderText('Enter secret')).toBeDefined()
  })
})
