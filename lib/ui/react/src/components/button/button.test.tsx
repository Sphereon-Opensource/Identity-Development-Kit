import {render, renderHook, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {Button} from './Button'
import {useButton} from './use-button'

describe('Button', () => {
  it('renders children', () => {
    render(<Button>Click me</Button>)
    expect(screen.getByRole('button', { name: 'Click me' })).toBeDefined()
  })

  it('applies variant data attribute', () => {
    render(<Button variant="secondary">Test</Button>)
    expect(screen.getByRole('button').dataset.variant).toBe('secondary')
  })

  it('applies size data attribute', () => {
    render(<Button size="lg">Test</Button>)
    expect(screen.getByRole('button').dataset.size).toBe('lg')
  })

  it('defaults to primary variant and md size', () => {
    render(<Button>Test</Button>)
    const btn = screen.getByRole('button')
    expect(btn.dataset.variant).toBe('primary')
    expect(btn.dataset.size).toBe('md')
  })

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    render(<Button onClick={onClick}>Click</Button>)
    await user.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('does not call onClick when disabled', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    render(<Button onClick={onClick} isDisabled>Click</Button>)
    await user.click(screen.getByRole('button'))
    expect(onClick).not.toHaveBeenCalled()
  })

  it('does not call onClick when loading', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    render(<Button onClick={onClick} isLoading>Click</Button>)
    await user.click(screen.getByRole('button'))
    expect(onClick).not.toHaveBeenCalled()
  })

  it('sets disabled attribute when disabled', () => {
    render(<Button isDisabled>Click</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('sets aria-busy when loading', () => {
    render(<Button isLoading>Click</Button>)
    expect(screen.getByRole('button').getAttribute('aria-busy')).toBe('true')
  })

  it('renders spinner when loading', () => {
    const { container } = render(<Button isLoading>Click</Button>)
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeNull()
  })

  it('does not render spinner when not loading', () => {
    const { container } = render(<Button>Click</Button>)
    expect(container.querySelector('[aria-hidden="true"]')).toBeNull()
  })

  it('sets button type to button', () => {
    render(<Button>Click</Button>)
    expect(screen.getByRole('button').getAttribute('type')).toBe('button')
  })
})

describe('useButton', () => {
  it('returns button props with type', () => {
    const { result } = renderHook(() => useButton())
    expect(result.current.buttonProps.type).toBe('button')
  })

  it('sets disabled when isDisabled', () => {
    const { result } = renderHook(() => useButton({ isDisabled: true }))
    expect(result.current.buttonProps.disabled).toBe(true)
  })

  it('sets disabled when isLoading', () => {
    const { result } = renderHook(() => useButton({ isLoading: true }))
    expect(result.current.buttonProps.disabled).toBe(true)
  })

  it('returns isPressed false initially', () => {
    const { result } = renderHook(() => useButton())
    expect(result.current.isPressed).toBe(false)
  })
})
