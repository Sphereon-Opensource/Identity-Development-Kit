import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {Grid} from './Grid'
import {Sidebar} from './Sidebar'

describe('Grid/Sidebar', () => {
  it('Grid sets the min track size and gap from props', () => {
    render(<Grid min="20rem" gap="4"><span>g</span></Grid>)
    const el = screen.getByText('g').parentElement as HTMLElement
    expect(el.style.getPropertyValue('--grid-min')).toBe('20rem')
    expect(el.style.getPropertyValue('--grid-gap')).toBe('var(--space-4)')
  })
  it('Sidebar exposes side width + content min as custom props', () => {
    render(<Sidebar sideWidth="16rem" contentMin="50%"><span>a</span><span>b</span></Sidebar>)
    const el = screen.getByText('a').parentElement as HTMLElement
    expect(el.style.getPropertyValue('--sidebar-width')).toBe('16rem')
    expect(el.style.getPropertyValue('--sidebar-content-min')).toBe('50%')
  })
})
