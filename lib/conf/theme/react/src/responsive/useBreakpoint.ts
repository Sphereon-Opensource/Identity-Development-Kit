import {BREAKPOINTS, mediaUp} from '../breakpoints'
import type {BreakpointName} from '../breakpoints'
import {useMediaQuery} from './useMediaQuery'

export interface BreakpointApi {
  /** current band (desktop-first default 'lg' until mounted). */
  name: BreakpointName
  /** true at or above the named breakpoint. */
  isUp: (n: keyof typeof BREAKPOINTS) => boolean
  /** true strictly below the named breakpoint. */
  isDown: (n: keyof typeof BREAKPOINTS) => boolean
}

/** Reports the current responsive band. Built on the Phase-1 BREAKPOINTS. */
export function useBreakpoint(): BreakpointApi {
  // Evaluate each breakpoint's min-width; the highest matched is the current band.
  const sm = useMediaQuery(mediaUp('sm'))
  const md = useMediaQuery(mediaUp('md'))
  const lg = useMediaQuery(mediaUp('lg'))
  const xl = useMediaQuery(mediaUp('xl'))
  const xxl = useMediaQuery(mediaUp('xxl'))

  let name: BreakpointName = 'xs'
  if (xxl) name = 'xxl'
  else if (xl) name = 'xl'
  else if (lg) name = 'lg'
  else if (md) name = 'md'
  else if (sm) name = 'sm'

  const matched: Record<keyof typeof BREAKPOINTS, boolean> = {sm, md, lg, xl, xxl}
  return {
    name,
    isUp: (n) => matched[n],
    isDown: (n) => !matched[n],
  }
}
