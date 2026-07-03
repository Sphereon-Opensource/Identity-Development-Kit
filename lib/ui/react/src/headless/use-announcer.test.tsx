import {render, screen, act} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {AnnouncerProvider, useAnnouncer} from './use-announcer'

function Trigger() {
  const {announce} = useAnnouncer()
  return <button onClick={() => announce('Saved', 'polite')}>go</button>
}

function TriggerAssertive() {
  const {announce} = useAnnouncer()
  return <button onClick={() => announce('Error occurred', 'assertive')}>go-assertive</button>
}

describe('announcer', () => {
  it('writes the message into the polite live region', async () => {
    render(
      <AnnouncerProvider>
        <Trigger />
      </AnnouncerProvider>,
    )
    const region = screen.getByTestId('live-polite')
    expect(region).toHaveAttribute('aria-live', 'polite')
    await act(async () => {
      screen.getByRole('button', {name: 'go'}).click()
    })
    expect(region).toHaveTextContent('Saved')
  })

  it('writes the message into the assertive live region', async () => {
    render(
      <AnnouncerProvider>
        <TriggerAssertive />
      </AnnouncerProvider>,
    )
    const region = screen.getByTestId('live-assertive')
    expect(region).toHaveAttribute('aria-live', 'assertive')
    await act(async () => {
      screen.getByRole('button', {name: 'go-assertive'}).click()
    })
    expect(region).toHaveTextContent('Error occurred')
  })

  it('returns a no-op announce when used outside a provider', () => {
    function NoProviderConsumer() {
      const {announce} = useAnnouncer()
      // Should not throw when called outside provider
      announce('test', 'polite')
      return <span>ok</span>
    }
    expect(() => render(<NoProviderConsumer />)).not.toThrow()
  })
})
