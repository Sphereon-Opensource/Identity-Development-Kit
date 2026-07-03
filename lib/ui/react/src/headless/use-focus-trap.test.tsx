import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {useState} from 'react'
import {describe, expect, it, vi} from 'vitest'
import {useFocusTrap} from './use-focus-trap'

function Dialog({onEscape}: {onEscape: () => void}) {
  const ref = useFocusTrap<HTMLDivElement>(true, onEscape)
  return (
    <div ref={ref} role="dialog" aria-label="d">
      <button>first</button>
      <button>last</button>
    </div>
  )
}

function Harness() {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <button onClick={() => setOpen(true)}>open</button>
      {open ? <Trapped onClose={() => setOpen(false)} /> : null}
    </div>
  )
}
function Trapped({onClose}: {onClose: () => void}) {
  const ref = useFocusTrap<HTMLDivElement>(true, onClose)
  return (
    <div ref={ref} role="dialog" aria-label="d">
      <button onClick={onClose}>close</button>
    </div>
  )
}

describe('useFocusTrap', () => {
  it('focuses the first focusable element when activated', () => {
    render(<Dialog onEscape={vi.fn()} />)
    expect(screen.getByRole('button', {name: 'first'})).toHaveFocus()
  })

  it('calls onEscape when Escape is pressed', async () => {
    const onEscape = vi.fn()
    render(<Dialog onEscape={onEscape} />)
    await userEvent.keyboard('{Escape}')
    expect(onEscape).toHaveBeenCalledTimes(1)
  })

  it('wraps focus from last to first on Tab', async () => {
    render(<Dialog onEscape={vi.fn()} />)
    screen.getByRole('button', {name: 'last'}).focus()
    await userEvent.tab()
    expect(screen.getByRole('button', {name: 'first'})).toHaveFocus()
  })

  it('restores focus to the opener when the trap unmounts', async () => {
    render(<Harness />)
    const opener = screen.getByRole('button', {name: 'open'})
    opener.focus()
    await userEvent.click(opener)
    expect(screen.getByRole('button', {name: 'close'})).toHaveFocus()
    await userEvent.click(screen.getByRole('button', {name: 'close'}))
    expect(opener).toHaveFocus()
  })
})
