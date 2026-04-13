/**
 * Merges multiple props objects, composing event handlers.
 * When the same event handler exists in multiple objects,
 * they are called in order.
 */
export function mergeProps<T extends Record<string, unknown>>(
  ...allProps: (T | undefined)[]
): T {
  const result: Record<string, unknown> = {}

  for (const props of allProps) {
    if (!props) continue
    for (const [key, value] of Object.entries(props)) {
      const existing = result[key]
      if (
        typeof existing === 'function' &&
        typeof value === 'function' &&
        key.startsWith('on')
      ) {
        // Compose event handlers
        result[key] = (...args: unknown[]) => {
          existing(...args)
          ;(value as (...a: unknown[]) => unknown)(...args)
        }
      } else {
        result[key] = value
      }
    }
  }

  return result as T
}
