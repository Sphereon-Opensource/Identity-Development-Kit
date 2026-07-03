import {useEffect, useState} from 'react'

/** SSR-safe media-query matcher. Returns false on the server / first render, then the
 *  real match after mount (avoids hydration mismatch). */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false)
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mql = window.matchMedia(query)
    const update = () => setMatches(mql.matches)
    update()
    mql.addEventListener('change', update)
    return () => mql.removeEventListener('change', update)
  }, [query])
  return matches
}
