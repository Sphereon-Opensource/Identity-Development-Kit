/** Single source of truth for responsive breakpoints (px). Desktop-first priority per the spec:
 *  lg (1024) is the first-class target, md (768) the tablet fallback, < md the mobile band. */
export const BREAKPOINTS = {sm: 640, md: 768, lg: 1024, xl: 1280, xxl: 1536} as const

export type BreakpointName = 'xs' | keyof typeof BREAKPOINTS

const ORDER: BreakpointName[] = ['xs', 'sm', 'md', 'lg', 'xl', 'xxl']

/** The largest breakpoint whose min-width is <= width. Widths below `sm` resolve to 'xs'. */
export function resolveBreakpoint(width: number): BreakpointName {
  let current: BreakpointName = 'xs'
  for (const name of ORDER) {
    if (name === 'xs') continue
    if (width >= BREAKPOINTS[name]) current = name
  }
  return current
}

/** `(min-width: <bp>px)` — true at and above the named breakpoint. */
export function mediaUp(name: keyof typeof BREAKPOINTS): string {
  return `(min-width: ${BREAKPOINTS[name]}px)`
}

/** `(max-width: <bp - 0.02>px)` — true strictly below the named breakpoint (avoids 1px overlap). */
export function mediaDown(name: keyof typeof BREAKPOINTS): string {
  return `(max-width: ${BREAKPOINTS[name] - 0.02}px)`
}
