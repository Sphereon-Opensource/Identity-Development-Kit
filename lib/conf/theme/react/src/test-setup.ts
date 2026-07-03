import {vi} from 'vitest'

// jsdom has no matchMedia; provide a controllable mock. Tests set globalThis.__mqWidth
// before render, then this evaluates simple (min-width)/(max-width) queries against it.
;(globalThis as Record<string, unknown>).__mqWidth ??= 1280
function evaluate(query: string): boolean {
  const w = (globalThis as unknown as Record<string, number>).__mqWidth
  const min = /\(min-width:\s*([\d.]+)px\)/.exec(query)
  const max = /\(max-width:\s*([\d.]+)px\)/.exec(query)
  if (min && w < parseFloat(min[1])) return false
  if (max && w > parseFloat(max[1])) return false
  return Boolean(min || max)
}
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: evaluate(query),
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }) as unknown as MediaQueryList
}
