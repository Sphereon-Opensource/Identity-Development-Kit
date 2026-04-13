import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { useTabs } from './use-tabs'
import { renderHook } from '@testing-library/react'

// Minimal tabs test component using the headless hook
function TestTabs({ onChange, defaultIndex }: { onChange?: (i: number) => void; defaultIndex?: number }) {
  const { tabListProps, getTabProps, getPanelProps, selectedIndex } = useTabs({ onChange, defaultIndex, tabCount: 3 })
  return (
    <div>
      <div {...tabListProps}>
        <button {...getTabProps(0)}>Tab 0</button>
        <button {...getTabProps(1)}>Tab 1</button>
        <button {...getTabProps(2)}>Tab 2</button>
      </div>
      {/* Only render panel content when selected, using hidden attribute */}
      <div {...getPanelProps(0)}>Panel 0</div>
      <div {...getPanelProps(1)}>Panel 1</div>
      <div {...getPanelProps(2)}>Panel 2</div>
    </div>
  )
}

describe('useTabs', () => {
  it('returns tablist role', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    expect(result.current.tabListProps.role).toBe('tablist')
  })

  it('starts at defaultIndex 0', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    expect(result.current.selectedIndex).toBe(0)
  })

  it('starts at custom defaultIndex', () => {
    const { result } = renderHook(() => useTabs({ defaultIndex: 1, tabCount: 3 }))
    expect(result.current.selectedIndex).toBe(1)
  })

  it('tab has correct aria-selected', () => {
    const { result } = renderHook(() => useTabs({ defaultIndex: 0, tabCount: 3 }))
    expect(result.current.getTabProps(0)['aria-selected']).toBe(true)
    expect(result.current.getTabProps(1)['aria-selected']).toBe(false)
  })

  it('tab has role tab', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    expect(result.current.getTabProps(0).role).toBe('tab')
  })

  it('selected tab has tabIndex 0, others have -1', () => {
    const { result } = renderHook(() => useTabs({ defaultIndex: 0, tabCount: 3 }))
    expect(result.current.getTabProps(0).tabIndex).toBe(0)
    expect(result.current.getTabProps(1).tabIndex).toBe(-1)
  })

  it('panel is hidden when not selected', () => {
    const { result } = renderHook(() => useTabs({ defaultIndex: 0, tabCount: 3 }))
    expect(result.current.getPanelProps(1).hidden).toBe(true)
    expect(result.current.getPanelProps(0).hidden).toBeUndefined()
  })

  it('panel has role tabpanel', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    expect(result.current.getPanelProps(0).role).toBe('tabpanel')
  })

  it('tab aria-controls matches panel id', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    const tabProps = result.current.getTabProps(0)
    const panelProps = result.current.getPanelProps(0)
    expect(tabProps['aria-controls']).toBe(panelProps.id)
  })

  it('panel aria-labelledby matches tab id', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    const tabProps = result.current.getTabProps(0)
    const panelProps = result.current.getPanelProps(0)
    expect(panelProps['aria-labelledby']).toBe(tabProps.id)
  })

  it('default orientation is horizontal', () => {
    const { result } = renderHook(() => useTabs({ tabCount: 3 }))
    expect(result.current.tabListProps['aria-orientation']).toBe('horizontal')
  })

  it('accepts vertical orientation', () => {
    const { result } = renderHook(() => useTabs({ orientation: 'vertical', tabCount: 3 }))
    expect(result.current.tabListProps['aria-orientation']).toBe('vertical')
  })
})

describe('TestTabs', () => {
  it('renders tablist', () => {
    render(<TestTabs />)
    expect(screen.getByRole('tablist')).toBeDefined()
  })

  it('renders all tabs', () => {
    render(<TestTabs />)
    expect(screen.getAllByRole('tab')).toHaveLength(3)
  })

  it('selects tab on click', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<TestTabs onChange={onChange} />)
    await user.click(screen.getByText('Tab 1'))
    expect(onChange).toHaveBeenCalledWith(1)
  })

  it('first panel is visible by default', () => {
    render(<TestTabs />)
    // Panel 0 should be visible (not hidden)
    const panels = screen.getAllByRole('tabpanel')
    expect(panels).toHaveLength(1)
    expect(panels[0]).toHaveTextContent('Panel 0')
  })

  it('second tab is selected when defaultIndex=1', () => {
    render(<TestTabs defaultIndex={1} />)
    const panels = screen.getAllByRole('tabpanel')
    expect(panels).toHaveLength(1)
    expect(panels[0]).toHaveTextContent('Panel 1')
  })

  it('clicking tab shows corresponding panel', async () => {
    const user = userEvent.setup()
    render(<TestTabs />)
    await user.click(screen.getByText('Tab 2'))
    const panels = screen.getAllByRole('tabpanel')
    expect(panels).toHaveLength(1)
    expect(panels[0]).toHaveTextContent('Panel 2')
  })
})
