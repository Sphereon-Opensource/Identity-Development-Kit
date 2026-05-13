import {render, screen} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {CredentialMiniCard} from './CredentialMiniCard'

describe('CredentialMiniCard', () => {
  it('renders display name', () => {
    render(
      <CredentialMiniCard credential={{id: 'a', displayName: 'Driver License'}} />,
    )
    expect(screen.getByText('Driver License')).toBeDefined()
  })

  it('renders type when provided', () => {
    render(
      <CredentialMiniCard credential={{id: 'a', displayName: 'Driver License', type: 'mDL'}} />,
    )
    expect(screen.getByText('mDL')).toBeDefined()
  })

  it('renders mandatory badge when mandatory + label provided', () => {
    render(
      <CredentialMiniCard
        credential={{id: 'a', displayName: 'X', mandatory: true}}
        mandatoryLabel="Required"
      />,
    )
    expect(screen.getByText('Required')).toBeDefined()
  })

  it('does not render badge when mandatory is false', () => {
    render(
      <CredentialMiniCard
        credential={{id: 'a', displayName: 'X', mandatory: false}}
        mandatoryLabel="Required"
      />,
    )
    expect(screen.queryByText('Required')).toBeNull()
  })

  it('does not render badge when mandatoryLabel missing', () => {
    render(<CredentialMiniCard credential={{id: 'a', displayName: 'X', mandatory: true}} />)
    expect(screen.queryByText(/required/i)).toBeNull()
  })

  it('renders logo when logoUrl provided', () => {
    const {container} = render(
      <CredentialMiniCard credential={{id: 'a', displayName: 'X', logoUrl: 'https://example/x.png'}} />,
    )
    const img = container.querySelector('img')
    expect(img).not.toBeNull()
    expect(img?.getAttribute('src')).toBe('https://example/x.png')
  })

  it('renders fallback initial when no logoUrl', () => {
    render(<CredentialMiniCard credential={{id: 'a', displayName: 'driver license'}} />)
    expect(screen.getByText('D')).toBeDefined()
  })
})
