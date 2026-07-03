// WCAG 2.1 relative luminance + contrast ratio. Hex only (#rgb / #rrggbb).
function parseHex(hex: string): [number, number, number] {
  let h = hex.trim().replace(/^#/, '')
  if (h.length === 3) h = h.split('').map((c) => c + c).join('')
  if (h.length !== 6) throw new Error(`contrast: expected hex color, got "${hex}"`)
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as [number, number, number]
}
function channelLuminance(c8: number): number {
  const c = c8 / 255
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}
export function relativeLuminance(hex: string): number {
  const [r, g, b] = parseHex(hex)
  return 0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b)
}
export function contrastRatio(fg: string, bg: string): number {
  const l1 = relativeLuminance(fg)
  const l2 = relativeLuminance(bg)
  const [hi, lo] = l1 >= l2 ? [l1, l2] : [l2, l1]
  return (hi + 0.05) / (lo + 0.05)
}
export function meetsAA(fg: string, bg: string, large = false): boolean {
  return contrastRatio(fg, bg) >= (large ? 3 : 4.5)
}
