import { describe, expect, it } from 'vitest'
import { contrastRatio, meetsAA } from './contrast'

describe('contrast', () => {
  it('computes a known ratio (black on white = 21)', () => {
    expect(Math.round(contrastRatio('#000000', '#FFFFFF'))).toBe(21)
  })
  it('white on the new error red passes AA', () => {
    expect(meetsAA('#FFFFFF', '#BE123C')).toBe(true)
  })
  it('flags a failing pair', () => {
    expect(meetsAA('#9A9A9A', '#FFFFFF')).toBe(false)
  })
})
