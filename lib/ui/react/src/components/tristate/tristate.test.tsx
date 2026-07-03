import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {TriState} from './TriState'

describe('TriState', () => {
  it('renders three segments: Disabled, Supported, Required', () => {
    render(<TriState />)
    expect(screen.getByRole('radio', {name: /disabled/i})).toBeDefined()
    expect(screen.getByRole('radio', {name: /supported/i})).toBeDefined()
    expect(screen.getByRole('radio', {name: /required/i})).toBeDefined()
  })

  it('defaults to "disabled" as the active value', () => {
    render(<TriState />)
    expect(screen.getByRole('radio', {name: /disabled/i}).getAttribute('aria-checked')).toBe('true')
    expect(screen.getByRole('radio', {name: /supported/i}).getAttribute('aria-checked')).toBe('false')
    expect(screen.getByRole('radio', {name: /required/i}).getAttribute('aria-checked')).toBe('false')
  })

  it('reflects controlled value via aria-checked', () => {
    const {rerender} = render(<TriState value="supported" />)
    expect(screen.getByRole('radio', {name: /supported/i}).getAttribute('aria-checked')).toBe('true')
    expect(screen.getByRole('radio', {name: /disabled/i}).getAttribute('aria-checked')).toBe('false')
    rerender(<TriState value="required" />)
    expect(screen.getByRole('radio', {name: /required/i}).getAttribute('aria-checked')).toBe('true')
    expect(screen.getByRole('radio', {name: /supported/i}).getAttribute('aria-checked')).toBe('false')
  })

  it('onChange returns the correct TriStateValue', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<TriState onChange={onChange} />)
    await user.click(screen.getByRole('radio', {name: /supported/i}))
    expect(onChange).toHaveBeenCalledWith('supported')
    await user.click(screen.getByRole('radio', {name: /required/i}))
    expect(onChange).toHaveBeenCalledWith('required')
    await user.click(screen.getByRole('radio', {name: /disabled/i}))
    expect(onChange).toHaveBeenCalledWith('disabled')
  })

  it('active supported segment gets the supported (tonal) fill class', () => {
    render(<TriState value="supported" />)
    const supportedSegment = screen.getByRole('radio', {name: /supported/i})
    expect(supportedSegment.className).toContain('supported')
  })

  it('active required segment gets the required (solid) fill class', () => {
    render(<TriState value="required" />)
    const requiredSegment = screen.getByRole('radio', {name: /required/i})
    expect(requiredSegment.className).toContain('required')
  })

  it('active disabled segment does NOT get a brand fill class', () => {
    render(<TriState value="disabled" />)
    const disabledSegment = screen.getByRole('radio', {name: /disabled/i})
    expect(disabledSegment.className).not.toContain('supported')
    expect(disabledSegment.className).not.toContain('required')
  })

  it('renders the Field label', () => {
    render(<TriState label="Feature Access" />)
    expect(screen.getByText('Feature Access')).toBeDefined()
  })

  it('renders the Field description', () => {
    render(<TriState label="Access" description="Controls who can use this feature" />)
    expect(screen.getByText('Controls who can use this feature')).toBeDefined()
  })

  it('renders the Field error message with role="alert"', () => {
    render(<TriState label="Access" errorMessage="Selection required" />)
    const alert = screen.getByRole('alert')
    expect(alert.textContent).toBe('Selection required')
  })

  it('Field label is associated with the radiogroup via aria-labelledby', () => {
    render(<TriState label="Feature Access" />)
    const group = screen.getByRole('radiogroup')
    const labelledBy = group.getAttribute('aria-labelledby')
    expect(labelledBy).toBeTruthy()
    const labelEl = document.getElementById(labelledBy!)
    expect(labelEl?.textContent).toContain('Feature Access')
  })

  it('renders role="radiogroup" wrapper', () => {
    render(<TriState label="Access" />)
    expect(screen.getByRole('radiogroup')).toBeDefined()
  })

  it('isDisabled disables all segments', () => {
    render(<TriState isDisabled />)
    const radios = screen.getAllByRole('radio')
    radios.forEach((r) => expect(r).toBeDisabled())
  })

  it('size="sm" forwards the compact modifier class to the radiogroup', () => {
    render(<TriState aria-label="PKCE" size="sm" />)
    const group = screen.getByRole('radiogroup', {name: 'PKCE'})
    expect(group.className.trim().split(/\s+/)).toHaveLength(2)
  })

  it('aria-label names the radiogroup when no visible label is rendered', () => {
    render(<TriState aria-label="PKCE policy" />)
    expect(screen.getByRole('radiogroup', {name: 'PKCE policy'})).toBeDefined()
  })

  it('aria-labelledby points the radiogroup at an external label and wins over the Field label', () => {
    render(
      <>
        <span id="ext-label">Device flow</span>
        <TriState label="Ignored" aria-labelledby="ext-label" />
      </>,
    )
    const group = screen.getByRole('radiogroup', {name: 'Device flow'})
    expect(group.getAttribute('aria-labelledby')).toBe('ext-label')
  })
})
