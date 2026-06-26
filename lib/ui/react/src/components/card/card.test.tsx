import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {Card} from './Card'

describe('Card', () => {
  it('renders children', () => {
    render(<Card>Card content</Card>)
    expect(screen.getByText('Card content')).toBeDefined()
  })

  it('applies className', () => {
    const { container } = render(<Card className="custom">Content</Card>)
    expect(container.firstElementChild?.classList.contains('custom')).toBe(true)
  })

  it('passes through HTML attributes', () => {
    render(<Card data-testid="my-card">Content</Card>)
    expect(screen.getByTestId('my-card')).toBeDefined()
  })

  it('can opt into design-system card interaction styling without requiring a click handler', () => {
    const {container} = render(<Card interactive>Content</Card>)

    expect(container.firstElementChild?.getAttribute('class')).toContain('interactive')
    expect(container.firstElementChild?.getAttribute('role')).toBeNull()
  })

  it('renders as a div', () => {
    const { container } = render(<Card>Content</Card>)
    expect(container.firstElementChild?.tagName).toBe('DIV')
  })

  it('forwards ref', () => {
    const ref = { current: null as HTMLDivElement | null }
    render(<Card ref={ref}>Content</Card>)
    expect(ref.current).not.toBeNull()
    expect(ref.current?.tagName).toBe('DIV')
  })

  it('renders with no extra className when not provided', () => {
    const { container } = render(<Card>Content</Card>)
    // Should not have trailing space or "undefined" in className
    const className = container.firstElementChild?.getAttribute('class') ?? ''
    expect(className).not.toContain('undefined')
  })
})
