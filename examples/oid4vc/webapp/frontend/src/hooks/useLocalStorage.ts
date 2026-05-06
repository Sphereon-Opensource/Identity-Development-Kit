import { useCallback, useState } from 'react'

export function useLocalStorage<T extends string>(
  key: string,
  defaultValue: T,
): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => {
    try {
      const stored = localStorage.getItem(key)
      return (stored as T | null) ?? defaultValue
    } catch {
      return defaultValue
    }
  })

  const update = useCallback(
    (next: T) => {
      setValue(next)
      try {
        localStorage.setItem(key, next)
      } catch {
        // localStorage may be unavailable (private mode, quota); silently fall back to in-memory state
      }
    },
    [key],
  )

  return [value, update]
}
