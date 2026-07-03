import {render, screen} from '@testing-library/react'
import {describe, expect, it, beforeEach} from 'vitest'
import {useBreakpoint} from './useBreakpoint'

function Probe() {
  const bp = useBreakpoint()
  return (
    <div>
      <span data-testid="name">{bp.name}</span>
      <span data-testid="up-lg">{String(bp.isUp('lg'))}</span>
      <span data-testid="down-md">{String(bp.isDown('md'))}</span>
    </div>
  )
}

function setWidth(px: number) {
  ;(globalThis as Record<string, number>).__mqWidth = px
}

describe('useBreakpoint', () => {
  beforeEach(() => setWidth(1280))

  it('reports a desktop band at wide widths', () => {
    setWidth(1280)
    render(<Probe />)
    expect(screen.getByTestId('name').textContent).toBe('xl')
    expect(screen.getByTestId('up-lg').textContent).toBe('true')
    expect(screen.getByTestId('down-md').textContent).toBe('false')
  })

  it('reports a mobile band below md', () => {
    setWidth(500)
    render(<Probe />)
    expect(screen.getByTestId('name').textContent).toBe('xs')
    expect(screen.getByTestId('up-lg').textContent).toBe('false')
    expect(screen.getByTestId('down-md').textContent).toBe('true')
  })
})
