export type SpaceKey = '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8'

/** Map a spacing-scale key to its CSS var. '4' -> var(--space-4). */
export function spaceVar(key: SpaceKey): string {
  return `var(--space-${key})`
}
