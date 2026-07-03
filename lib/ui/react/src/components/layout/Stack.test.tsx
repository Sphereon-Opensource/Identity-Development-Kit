import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {Stack} from './Stack'
import {Cluster} from './Cluster'
import {spaceVar} from './space'

describe('layout primitives', () => {
  it('spaceVar maps a key to a token var', () => {
    expect(spaceVar('4')).toBe('var(--space-4)')
  })
  it('Stack renders children with a column gap from the token scale', () => {
    render(<Stack gap="4"><span>a</span><span>b</span></Stack>)
    const el = screen.getByText('a').parentElement as HTMLElement
    expect(el.style.getPropertyValue('--stack-gap')).toBe('var(--space-4)')
  })
  it('Cluster renders with the given alignment', () => {
    render(<Cluster gap="2" align="center"><span>x</span></Cluster>)
    const el = screen.getByText('x').parentElement as HTMLElement
    expect(el.style.getPropertyValue('--cluster-gap')).toBe('var(--space-2)')
  })
  it('Stack honours the `as` prop', () => {
    render(<Stack as="section" gap="2"><span>s</span></Stack>)
    expect(screen.getByText('s').parentElement?.tagName).toBe('SECTION')
  })
})
