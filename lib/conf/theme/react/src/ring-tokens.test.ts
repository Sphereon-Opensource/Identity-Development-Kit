/**
 * Regression test for Task 1.4 — unified per-mode focus/error ring tokens.
 *
 * comp.focus.ring / comp.error.ring live in allComponentTokenDefaults (Tier 3),
 * so we merge them with getSystemDefaults before resolving references.
 */

import { getSystemDefaults } from './defaults'
import { allComponentTokenDefaults } from './component-token-defaults'
import { resolveTokenReferences } from './css-injector'

describe('ring tokens', () => {
  it('comp focus/error ring resolve to non-empty shadows', () => {
    const combined = { ...getSystemDefaults('light'), ...allComponentTokenDefaults }
    const t = resolveTokenReferences(combined)
    expect(t['comp.focus.ring']).toMatch(/color-mix/)
    expect(t['comp.error.ring']).toMatch(/color-mix/)
  })

  it('shadow focusRing is present in light defaults', () => {
    const t = getSystemDefaults('light')
    expect(t['shadow.focusRing']).toMatch(/color-mix/)
    expect(t['shadow.errorRing']).toMatch(/color-mix/)
  })

  it('error ring resolves to different values in light vs dark (color.error differs per mode)', () => {
    const lightResolved = resolveTokenReferences(getSystemDefaults('light'))['shadow.errorRing']
    const darkResolved = resolveTokenReferences(getSystemDefaults('dark'))['shadow.errorRing']
    // Template strings are identical (70% both modes), but {color.error} resolves to a
    // different hex per mode, so the fully-resolved strings must differ.
    expect(lightResolved).not.toBe(darkResolved)
  })

  it('focus ring alpha differs between light and dark', () => {
    const light = getSystemDefaults('light')['shadow.focusRing']
    const dark = getSystemDefaults('dark')['shadow.focusRing']
    expect(light).not.toBe(dark)
  })

  it('dark focus ring has higher alpha than light (solid edge + halo)', () => {
    const light = getSystemDefaults('light')['shadow.focusRing'] ?? ''
    const dark = getSystemDefaults('dark')['shadow.focusRing'] ?? ''
    // solid edge: 1.5px; halo: light = 30%, dark = 40%
    expect(light).toContain('1.5px')
    expect(dark).toContain('1.5px')
    expect(light).toContain('30%')
    expect(dark).toContain('40%')
  })
})
