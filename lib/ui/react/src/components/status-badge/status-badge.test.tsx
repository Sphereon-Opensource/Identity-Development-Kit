import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {StatusBadge} from './StatusBadge'

describe('StatusBadge', () => {
  it('renders the label text', () => {
    render(<StatusBadge status="success">Verified</StatusBadge>)
    expect(screen.getByText('Verified')).toBeDefined()
  })

  it('renders the default status icon (aria-hidden icon cell)', () => {
    const {container} = render(<StatusBadge status="success">Verified</StatusBadge>)
    // icon cell is aria-hidden; locate the SVG inside it
    const svg = container.querySelector('svg')
    expect(svg).not.toBeNull()
    expect(svg?.getAttribute('aria-hidden')).toBe('true')
  })

  it('applies data-status and data-variant attributes', () => {
    render(<StatusBadge status="warning">Expired</StatusBadge>)
    const badge = screen.getByText('Expired').closest('[data-status]')
    expect(badge?.getAttribute('data-status')).toBe('warning')
    expect(badge?.getAttribute('data-variant')).toBe('tonal')
  })

  it('defaults to tonal variant', () => {
    render(<StatusBadge status="info">Info</StatusBadge>)
    const badge = screen.getByText('Info').closest('[data-variant]')
    expect(badge?.getAttribute('data-variant')).toBe('tonal')
  })

  it('applies solid variant', () => {
    render(<StatusBadge status="error" variant="solid">Error</StatusBadge>)
    const badge = screen.getByText('Error').closest('[data-variant]')
    expect(badge?.getAttribute('data-variant')).toBe('solid')
  })

  it('applies outline variant', () => {
    render(<StatusBadge status="neutral" variant="outline">Neutral</StatusBadge>)
    const badge = screen.getByText('Neutral').closest('[data-variant]')
    expect(badge?.getAttribute('data-variant')).toBe('outline')
  })

  it('sets --badge-bg CSS var from the status token (no hardcoded hex)', () => {
    render(<StatusBadge status="success">OK</StatusBadge>)
    const badge = screen.getByText('OK').closest('[data-status]') as HTMLElement
    const bg = badge.style.getPropertyValue('--badge-bg')
    expect(bg).toContain('var(--color-feedback-success-container)')
    expect(bg).not.toMatch(/#[0-9a-fA-F]{3,6}/)
  })

  it('sets --badge-text CSS var from the status token (no hardcoded hex)', () => {
    render(<StatusBadge status="error">Err</StatusBadge>)
    const badge = screen.getByText('Err').closest('[data-status]') as HTMLElement
    const text = badge.style.getPropertyValue('--badge-text')
    expect(text).toContain('var(--color-on-error-container)')
    expect(text).not.toMatch(/#[0-9a-fA-F]{3,6}/)
  })

  it('sets --badge-strong CSS var from the status token (no hardcoded hex)', () => {
    render(<StatusBadge status="warning">Warn</StatusBadge>)
    const badge = screen.getByText('Warn').closest('[data-status]') as HTMLElement
    const strong = badge.style.getPropertyValue('--badge-strong')
    expect(strong).toContain('var(--color-feedback-warning)')
    expect(strong).not.toMatch(/#[0-9a-fA-F]{3,6}/)
  })

  it('neutral status uses surface-variant and text-secondary tokens', () => {
    render(<StatusBadge status="neutral">Draft</StatusBadge>)
    const badge = screen.getByText('Draft').closest('[data-status]') as HTMLElement
    expect(badge.style.getPropertyValue('--badge-bg')).toContain('var(--color-surface-variant)')
    expect(badge.style.getPropertyValue('--badge-text')).toContain('var(--color-text-secondary)')
    expect(badge.style.getPropertyValue('--badge-strong')).toContain('var(--color-text-secondary)')
  })

  it('icon prop overrides the default status icon', () => {
    const customIcon = <span data-testid="custom-icon">★</span>
    render(<StatusBadge status="success" icon={customIcon}>Label</StatusBadge>)
    expect(screen.getByTestId('custom-icon')).toBeDefined()
    // default SVG should not be present
    const {container} = render(<StatusBadge status="success" icon={customIcon}>Label</StatusBadge>)
    expect(container.querySelector('svg')).toBeNull()
  })

  it('forwards className to the root element', () => {
    render(<StatusBadge status="info" className="extra">Badge</StatusBadge>)
    const badge = screen.getByText('Badge').closest('[data-status]')
    expect(badge?.classList.contains('extra')).toBe(true)
  })

  it('renders all status kinds without error', () => {
    const statuses = ['success', 'warning', 'info', 'error', 'neutral'] as const
    for (const status of statuses) {
      const {unmount} = render(<StatusBadge status={status}>{status}</StatusBadge>)
      expect(screen.getByText(status)).toBeDefined()
      unmount()
    }
  })
})
