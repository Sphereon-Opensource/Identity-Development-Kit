import {render, screen} from '@testing-library/react'
import {userEvent} from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {InlineAlert} from './InlineAlert'

describe('InlineAlert', () => {
  it('renders the title', () => {
    render(<InlineAlert status="info" title="Heads up">Something happened</InlineAlert>)
    expect(screen.getByText('Heads up')).toBeDefined()
  })

  it('renders the message body (children)', () => {
    render(<InlineAlert status="success" title="Done">Your credential is ready.</InlineAlert>)
    expect(screen.getByText('Your credential is ready.')).toBeDefined()
  })

  it('renders the default status icon', () => {
    const {container} = render(<InlineAlert status="warning" title="Warning" />)
    const svg = container.querySelector('svg')
    expect(svg).not.toBeNull()
    expect(svg?.getAttribute('aria-hidden')).toBe('true')
  })

  it('uses role="alert" for error status', () => {
    render(<InlineAlert status="error" title="Something went wrong" />)
    expect(screen.getByRole('alert')).toBeDefined()
  })

  it('uses role="status" for non-error statuses', () => {
    const nonErrors = ['success', 'warning', 'info', 'neutral'] as const
    for (const status of nonErrors) {
      const {unmount} = render(<InlineAlert status={status} title={status} />)
      expect(screen.getByRole('status')).toBeDefined()
      unmount()
    }
  })

  it('does not render dismiss button when onDismiss is absent', () => {
    render(<InlineAlert status="info" title="Info" />)
    expect(screen.queryByRole('button', {name: 'Dismiss'})).toBeNull()
  })

  it('renders dismiss button when onDismiss is provided', () => {
    const onDismiss = vi.fn()
    render(<InlineAlert status="warning" title="Warn" onDismiss={onDismiss} />)
    expect(screen.getByRole('button', {name: 'Dismiss'})).toBeDefined()
  })

  it('calls onDismiss when dismiss button is clicked', async () => {
    const user = userEvent.setup()
    const onDismiss = vi.fn()
    render(<InlineAlert status="error" title="Error" onDismiss={onDismiss} />)
    await user.click(screen.getByRole('button', {name: 'Dismiss'}))
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('icon prop overrides the default status icon', () => {
    const customIcon = <span data-testid="custom-icon">!</span>
    render(<InlineAlert status="info" title="Custom" icon={customIcon} />)
    expect(screen.getByTestId('custom-icon')).toBeDefined()
    const {container} = render(<InlineAlert status="info" title="Custom" icon={customIcon} />)
    expect(container.querySelector('svg')).toBeNull()
  })

  it('sets --badge-bg CSS var from the status token', () => {
    render(<InlineAlert status="success" title="OK" />)
    const el = screen.getByRole('status') as HTMLElement
    const bg = el.style.getPropertyValue('--badge-bg')
    expect(bg).toContain('var(--color-feedback-success-container)')
    expect(bg).not.toMatch(/#[0-9a-fA-F]{3,6}/)
  })

  it('sets --badge-text CSS var from the status token', () => {
    render(<InlineAlert status="error" title="Err" />)
    const el = screen.getByRole('alert') as HTMLElement
    const text = el.style.getPropertyValue('--badge-text')
    expect(text).toContain('var(--color-on-error-container)')
    expect(text).not.toMatch(/#[0-9a-fA-F]{3,6}/)
  })

  it('forwards className to root element', () => {
    render(<InlineAlert status="neutral" className="extra" title="Neutral" />)
    const el = screen.getByRole('status')
    expect(el.classList.contains('extra')).toBe(true)
  })

  it('renders without title or children without error', () => {
    const {container} = render(<InlineAlert status="info" />)
    expect(container.querySelector('[role="status"]')).not.toBeNull()
  })
})
