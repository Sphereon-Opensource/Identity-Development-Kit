import {render} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {Skeleton} from './Skeleton'

describe('Skeleton', () => {
  it('renders a decorative (aria-hidden) block with the given size', () => {
    const {container} = render(<Skeleton width="120px" height="14px" />)
    const el = container.firstElementChild as HTMLElement
    expect(el).toHaveAttribute('aria-hidden', 'true')
    expect(el.style.width).toBe('120px')
    expect(el.style.height).toBe('14px')
  })
})
