import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {Badge} from './Badge'

describe('Badge', () => {
  it('renders children', () => {
    render(<Badge>New</Badge>)
    expect(screen.getByText('New')).toBeDefined()
  })

  it('applies default variant', () => {
    render(<Badge>Tag</Badge>)
    expect(screen.getByText('Tag').dataset.variant).toBe('default')
  })

  it('applies error variant', () => {
    render(<Badge variant="error">Error</Badge>)
    expect(screen.getByText('Error').dataset.variant).toBe('error')
  })

  it('renders as a span', () => {
    render(<Badge>Tag</Badge>)
    expect(screen.getByText('Tag').tagName).toBe('SPAN')
  })

  it('applies className', () => {
    render(<Badge className="custom">Tag</Badge>)
    expect(screen.getByText('Tag').classList.contains('custom')).toBe(true)
  })

  it('passes through HTML attributes', () => {
    render(<Badge data-testid="my-badge">Tag</Badge>)
    expect(screen.getByTestId('my-badge')).toBeDefined()
  })

  it('forwards ref', () => {
    const ref = { current: null as HTMLSpanElement | null }
    render(<Badge ref={ref}>Tag</Badge>)
    expect(ref.current).not.toBeNull()
    expect(ref.current?.tagName).toBe('SPAN')
  })
})
