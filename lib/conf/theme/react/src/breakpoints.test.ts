import {describe, expect, it} from 'vitest'
import {BREAKPOINTS, mediaDown, mediaUp, resolveBreakpoint} from './breakpoints'

describe('breakpoints', () => {
  it('exposes the canonical px scale', () => {
    expect(BREAKPOINTS).toEqual({sm: 640, md: 768, lg: 1024, xl: 1280, xxl: 1536})
  })

  it('resolves a width to its breakpoint band', () => {
    expect(resolveBreakpoint(375)).toBe('xs')
    expect(resolveBreakpoint(640)).toBe('sm')
    expect(resolveBreakpoint(800)).toBe('md')
    expect(resolveBreakpoint(1024)).toBe('lg')
    expect(resolveBreakpoint(1400)).toBe('xl')
    expect(resolveBreakpoint(1920)).toBe('xxl')
  })

  it('builds min-width media query strings', () => {
    expect(mediaUp('md')).toBe('(min-width: 768px)')
    expect(mediaUp('lg')).toBe('(min-width: 1024px)')
  })

  it('builds max-width media query strings just below the breakpoint', () => {
    expect(mediaDown('md')).toBe('(max-width: 767.98px)')
    expect(mediaDown('lg')).toBe('(max-width: 1023.98px)')
  })
})
