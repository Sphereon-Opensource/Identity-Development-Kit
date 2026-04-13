import type {MutableRefObject, Ref, RefCallback} from 'react'

/**
 * Merges multiple refs into a single callback ref.
 * Supports callback refs, object refs, and null.
 */
export function mergeRefs<T>(...refs: (Ref<T> | undefined)[]): RefCallback<T> {
  return (value: T | null) => {
    for (const ref of refs) {
      if (typeof ref === 'function') {
        ref(value)
      } else if (ref != null) {
        ;(ref as MutableRefObject<T | null>).current = value
      }
    }
  }
}
