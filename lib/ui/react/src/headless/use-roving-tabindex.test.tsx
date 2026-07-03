import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it} from 'vitest'
import {useRovingTabindex} from './use-roving-tabindex'

function Menu() {
  const roving = useRovingTabindex(3)
  const labels = ['one', 'two', 'three']
  return (
    <div {...roving.containerProps}>
      {labels.map((label, i) => {
        const {ref, ...props} = roving.getItemProps(i)
        return (
          <button key={label} ref={ref} {...props}>
            {label}
          </button>
        )
      })}
    </div>
  )
}

/** Harness with a variable item count so we can simulate NavRail capability-driven shrink. */
function ResizableMenu({count}: {count: number}) {
  const allLabels = ['one', 'two', 'three', 'four', 'five']
  const labels = allLabels.slice(0, count)
  const roving = useRovingTabindex(count)
  return (
    <div {...roving.containerProps}>
      {labels.map((label, i) => {
        const {ref, ...props} = roving.getItemProps(i)
        return (
          <button key={label} ref={ref} {...props}>
            {label}
          </button>
        )
      })}
    </div>
  )
}

describe('useRovingTabindex', () => {
  it('makes only the first item tabbable initially', () => {
    render(<Menu />)
    expect(screen.getByRole('button', {name: 'one'})).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('button', {name: 'two'})).toHaveAttribute('tabindex', '-1')
  })

  it('moves focus and tabbability with ArrowDown', async () => {
    render(<Menu />)
    screen.getByRole('button', {name: 'one'}).focus()
    await userEvent.keyboard('{ArrowDown}')
    expect(screen.getByRole('button', {name: 'two'})).toHaveFocus()
    expect(screen.getByRole('button', {name: 'two'})).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('button', {name: 'one'})).toHaveAttribute('tabindex', '-1')
  })

  it('jumps to the last item with End and first with Home', async () => {
    render(<Menu />)
    screen.getByRole('button', {name: 'one'}).focus()
    await userEvent.keyboard('{End}')
    expect(screen.getByRole('button', {name: 'three'})).toHaveFocus()
    await userEvent.keyboard('{Home}')
    expect(screen.getByRole('button', {name: 'one'})).toHaveFocus()
  })

  it('clamps activeIndex to 0 when itemCount shrinks below activeIndex', async () => {
    // Start with 3 items and move focus to the last (index 2).
    const {rerender} = render(<ResizableMenu count={3} />)
    screen.getByRole('button', {name: 'one'}).focus()
    await userEvent.keyboard('{End}')
    expect(screen.getByRole('button', {name: 'three'})).toHaveFocus()
    expect(screen.getByRole('button', {name: 'three'})).toHaveAttribute('tabindex', '0')

    // Shrink to 2 items — activeIndex=2 is now out of range.
    // The render clamp must fall back to index 0 so the rail stays keyboard-reachable.
    rerender(<ResizableMenu count={2} />)
    expect(screen.getByRole('button', {name: 'one'})).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('button', {name: 'two'})).toHaveAttribute('tabindex', '-1')
  })
})
