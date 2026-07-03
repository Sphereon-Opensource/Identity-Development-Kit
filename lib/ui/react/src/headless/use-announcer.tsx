import {createContext, useCallback, useContext, useMemo, useState} from 'react'
import type {CSSProperties, ReactElement, ReactNode} from 'react'

// ---------------------------------------------------------------------------
// LiveRegion — visually-hidden aria-live regions.
// Mount once near the app root via AnnouncerProvider.
// ---------------------------------------------------------------------------

const srOnly: CSSProperties = {
  position: 'absolute',
  width: 1,
  height: 1,
  margin: -1,
  padding: 0,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
  border: 0,
}

/** Visually-hidden aria-live regions. Mount once near the app root via AnnouncerProvider. */
export function LiveRegion({polite, assertive}: {polite: string; assertive: string}): ReactElement {
  return (
    <>
      <div data-testid="live-polite" role="status" aria-live="polite" aria-atomic="true" style={srOnly}>
        {polite}
      </div>
      <div data-testid="live-assertive" role="alert" aria-live="assertive" aria-atomic="true" style={srOnly}>
        {assertive}
      </div>
    </>
  )
}

// ---------------------------------------------------------------------------
// AnnouncerProvider + useAnnouncer
// ---------------------------------------------------------------------------

type Assertiveness = 'polite' | 'assertive'

export interface AnnouncerApi {
  announce: (message: string, assertiveness?: Assertiveness) => void
}

const AnnouncerContext = createContext<AnnouncerApi | null>(null)

/** Mount once near the app root (inside ThemeProvider) to give all descendants access to
 *  screen-reader announcements via {@link useAnnouncer}. Renders the visually-hidden
 *  {@link LiveRegion} as a sibling of children so portals stay inside the provider. */
export function AnnouncerProvider({children}: {children: ReactNode}): ReactElement {
  const [polite, setPolite] = useState('')
  const [assertive, setAssertive] = useState('')

  const announce = useCallback((message: string, assertiveness: Assertiveness = 'polite') => {
    // Clear then set so identical consecutive messages re-trigger the live region.
    // A microtask boundary (Promise.resolve().then) lets React commit the empty render
    // first, then sets the message so the DOM sees both transitions.
    if (assertiveness === 'assertive') {
      setAssertive('')
      // Use a microtask boundary so the empty render commits before the message render.
      Promise.resolve().then(() => setAssertive(message))
    } else {
      setPolite('')
      Promise.resolve().then(() => setPolite(message))
    }
  }, [])

  const api = useMemo(() => ({announce}), [announce])

  return (
    <AnnouncerContext.Provider value={api}>
      {children}
      <LiveRegion polite={polite} assertive={assertive} />
    </AnnouncerContext.Provider>
  )
}

/**
 * Returns the announcer API from the nearest {@link AnnouncerProvider}.
 * Returns a no-op `{announce(){}}` when called outside a provider so callers never crash —
 * this is intentional: `useAsyncAction` consumers rendered in unit tests without a provider
 * remain unaffected.
 */
export function useAnnouncer(): AnnouncerApi {
  const ctx = useContext(AnnouncerContext)
  return ctx ?? {announce: () => {}}
}
