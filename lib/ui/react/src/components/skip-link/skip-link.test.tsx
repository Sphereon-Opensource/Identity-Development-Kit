import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {SkipLink} from './SkipLink'

describe('SkipLink', () => {
  it('renders an anchor pointing at the main content id', () => {
    render(<SkipLink />)
    const link = screen.getByRole('link', {name: /skip to (main )?content/i})
    expect(link).toHaveAttribute('href', '#main-content')
  })

  it('honours a custom target and label', () => {
    render(<SkipLink targetId="work" label="Skip to work area" />)
    const link = screen.getByRole('link', {name: 'Skip to work area'})
    expect(link).toHaveAttribute('href', '#work')
  })
})
