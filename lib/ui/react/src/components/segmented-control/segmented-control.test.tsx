import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, it, vi} from 'vitest'
import {SegmentedControl} from './SegmentedControl'

const OPTIONS = [
  {value: 'a', label: 'Alpha'},
  {value: 'b', label: 'Beta'},
  {value: 'c', label: 'Gamma'},
]

const OPTIONS_WITH_DISABLED = [
  {value: 'a', label: 'Alpha'},
  {value: 'b', label: 'Beta', isDisabled: true},
  {value: 'c', label: 'Gamma'},
]

describe('SegmentedControl', () => {
  it('renders role="radiogroup" with role="radio" segments', () => {
    render(<SegmentedControl options={OPTIONS} aria-label="Choose" />)
    expect(screen.getByRole('radiogroup', {name: 'Choose'})).toBeDefined()
    expect(screen.getAllByRole('radio')).toHaveLength(3)
  })

  it('sets aria-checked on the active segment', () => {
    render(<SegmentedControl options={OPTIONS} defaultValue="b" aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    expect(radios[0].getAttribute('aria-checked')).toBe('false')
    expect(radios[1].getAttribute('aria-checked')).toBe('true')
    expect(radios[2].getAttribute('aria-checked')).toBe('false')
  })

  it('clicking a segment fires onChange and marks it active', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS} defaultValue="a" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    await user.click(radios[2])
    expect(onChange).toHaveBeenCalledWith('c')
    expect(radios[2].getAttribute('aria-checked')).toBe('true')
  })

  it('only the active segment has tabIndex=0 (roving tabindex)', () => {
    render(<SegmentedControl options={OPTIONS} defaultValue="b" aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    expect(radios[0].getAttribute('tabindex')).toBe('-1')
    expect(radios[1].getAttribute('tabindex')).toBe('0')
    expect(radios[2].getAttribute('tabindex')).toBe('-1')
  })

  it('first enabled segment is tabbable when no value selected', () => {
    render(<SegmentedControl options={OPTIONS} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    // defaultValue = first enabled option, so first should be active and tabIndex=0
    expect(radios[0].getAttribute('tabindex')).toBe('0')
  })

  it('ArrowRight moves selection to next segment', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS} defaultValue="a" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios[0].focus()
    await user.keyboard('{ArrowRight}')
    expect(onChange).toHaveBeenCalledWith('b')
  })

  it('ArrowLeft moves selection to previous segment', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS} defaultValue="b" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios[1].focus()
    await user.keyboard('{ArrowLeft}')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  it('ArrowRight wraps from last to first', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS} defaultValue="c" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios[2].focus()
    await user.keyboard('{ArrowRight}')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  it('Home moves to first segment', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS} defaultValue="c" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios[2].focus()
    await user.keyboard('{Home}')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  it('End moves to last segment', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS} defaultValue="a" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios[0].focus()
    await user.keyboard('{End}')
    expect(onChange).toHaveBeenCalledWith('c')
  })

  it('skips disabled segments in arrow key navigation', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<SegmentedControl options={OPTIONS_WITH_DISABLED} defaultValue="a" onChange={onChange} aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios[0].focus()
    await user.keyboard('{ArrowRight}')
    // Should skip 'b' (disabled) and go to 'c'
    expect(onChange).toHaveBeenCalledWith('c')
  })

  it('disabled segment is not tabbable', () => {
    render(<SegmentedControl options={OPTIONS_WITH_DISABLED} defaultValue="a" aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    expect(radios[1].getAttribute('tabindex')).toBe('-1')
    expect(radios[1]).toBeDisabled()
  })

  it('whole control disabled makes all segments disabled', () => {
    render(<SegmentedControl options={OPTIONS} defaultValue="a" isDisabled aria-label="Choose" />)
    const radios = screen.getAllByRole('radio')
    radios.forEach((r) => expect(r).toBeDisabled())
  })

  it('controlled value is reflected as aria-checked', () => {
    const {rerender} = render(<SegmentedControl options={OPTIONS} value="a" aria-label="Choose" />)
    expect(screen.getAllByRole('radio')[0].getAttribute('aria-checked')).toBe('true')
    rerender(<SegmentedControl options={OPTIONS} value="c" aria-label="Choose" />)
    expect(screen.getAllByRole('radio')[0].getAttribute('aria-checked')).toBe('false')
    expect(screen.getAllByRole('radio')[2].getAttribute('aria-checked')).toBe('true')
  })

  it('size="sm" adds a compact modifier class to the track', () => {
    render(<SegmentedControl options={OPTIONS} size="sm" aria-label="Choose" />)
    const track = screen.getByRole('radiogroup')
    // The compact variant layers a second class (track + sm) on the track element.
    expect(track.className.trim().split(/\s+/)).toHaveLength(2)
  })

  it('default size renders only the base track class (no compact modifier)', () => {
    render(<SegmentedControl options={OPTIONS} aria-label="Choose" />)
    const track = screen.getByRole('radiogroup')
    expect(track.className.trim().split(/\s+/)).toHaveLength(1)
  })

  it('applies getSegmentClassName to the active segment', () => {
    render(
      <SegmentedControl
        options={OPTIONS}
        defaultValue="b"
        aria-label="Choose"
        getSegmentClassName={(v, active) => (active ? `active-${v}` : undefined)}
      />,
    )
    const radios = screen.getAllByRole('radio')
    expect(radios[1].className).toContain('active-b')
    expect(radios[0].className).not.toContain('active-')
  })
})
