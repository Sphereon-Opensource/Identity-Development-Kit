/**
 * Regression test for Task 1.5 — brand gradient tokens.
 *
 * color.gradient.brand (light + dark) live in the color.* section of defaults,
 * so getSystemDefaults already carries them without any comp-token merge.
 */

import { getSystemDefaults } from './defaults'
import { resolveTokenReferences } from './css-injector'

describe('brand gradient tokens', () => {
  it('color.gradient.brand resolves to a linear-gradient', () => {
    const t = resolveTokenReferences(getSystemDefaults('light'))
    expect(t['color.gradient.brand']).toMatch(/^linear-gradient\(/)
  })

  it('brand gradient differs light vs dark', () => {
    const light = resolveTokenReferences(getSystemDefaults('light'))['color.gradient.brand']
    const dark = resolveTokenReferences(getSystemDefaults('dark'))['color.gradient.brand']
    expect(light).not.toBe(dark)
  })

  it('color.gradient.brandSubtle resolves to a linear-gradient containing color-mix', () => {
    const t = resolveTokenReferences(getSystemDefaults('light'))
    expect(t['color.gradient.brandSubtle']).toMatch(/^linear-gradient\(/)
    expect(t['color.gradient.brandSubtle']).toMatch(/color-mix/)
  })
})
