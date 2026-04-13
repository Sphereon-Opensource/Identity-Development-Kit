import {useId as reactUseId} from 'react'

/**
 * SSR-safe unique ID generator. Wraps React.useId().
 * Returns a stable, unique ID with optional prefix.
 */
export function useStableId(prefix?: string): string {
  const id = reactUseId()
  return prefix ? `${prefix}-${id}` : id
}
