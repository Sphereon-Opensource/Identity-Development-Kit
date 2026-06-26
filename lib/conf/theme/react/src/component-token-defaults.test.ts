import { describe, expect, it } from 'vitest'
import { tokenKeyToCssVar, resolveTokenReferences } from './css-injector'
import {appShellTokenDefaults, buttonTokenDefaults, cardTokenDefaults, navTokenDefaults, sidebarNavTokenDefaults, statusPillTokenDefaults} from './component-token-defaults'
import { getSystemDefaults } from './defaults'

describe('card component tokens', () => {
  it('keeps admin shell primary actions on the deep brand stop', () => {
    expect(buttonTokenDefaults['comp.button.primary.background']).toBe('{palette.brand.700}')
    expect(buttonTokenDefaults['comp.button.primary.backgroundHover']).toBe('{palette.brand.800}')
    expect(buttonTokenDefaults['comp.button.primary.foreground']).toBe('{color.onPrimary}')
  })

  it('defines shell and sidebar tokens in the React component defaults', () => {
    expect(appShellTokenDefaults['comp.appshell.topBackground']).toBe('{color.surface}')
    expect(appShellTokenDefaults['comp.appshell.topBorder']).toBe('{color.border.subtle}')
    expect(sidebarNavTokenDefaults['comp.snav.bg']).toBe('{color.surface}')
    expect(sidebarNavTokenDefaults['comp.snav.itemFgActive']).toBe('{color.navigation.activeForeground}')
    expect(sidebarNavTokenDefaults['comp.snav.itemBgActive']).toBe('{color.primaryContainer}')
    expect(sidebarNavTokenDefaults['comp.snav.itemBackgroundActive']).toContain('linear-gradient')
    expect(sidebarNavTokenDefaults['comp.snav.itemBorderActive']).toBe('color-mix(in srgb, {color.primary} 34%, transparent)')
    expect(navTokenDefaults['comp.nav.side.active.foreground']).toBe('{color.navigation.activeForeground}')
  })

  it('defines status-pill tokens in the React component defaults', () => {
    expect(statusPillTokenDefaults['comp.statuspill.success.background']).toBe('{color.feedback.successContainer}')
    expect(statusPillTokenDefaults['comp.statuspill.success.border']).toBe('{color.feedback.successBorder}')
    expect(statusPillTokenDefaults['comp.statuspill.warning.background']).toBe('{color.feedback.warningContainer}')
    expect(statusPillTokenDefaults['comp.statuspill.warning.border']).toBe('{color.feedback.warningBorder}')
  })

  it('defines card interaction tokens in the React component defaults', () => {
    expect(cardTokenDefaults['comp.card.borderHover']).toBe('{palette.brand.600}')
    expect(cardTokenDefaults['comp.card.shadowHover']).toContain('{palette.brand.600}')
    expect(cardTokenDefaults['comp.card.shadowHover']).toContain('{palette.brand.500}')
    expect(cardTokenDefaults['comp.card.ringHover']).toContain('{palette.brand.600}')
  })

  it('emits canonical CSS variable names for card interaction tokens', () => {
    expect(tokenKeyToCssVar('comp.card.borderHover')).toBe('--comp-card-border-hover')
    expect(tokenKeyToCssVar('comp.card.shadowHover')).toBe('--comp-card-shadow-hover')
    expect(tokenKeyToCssVar('comp.card.ringHover')).toBe('--comp-card-ring-hover')
    expect(tokenKeyToCssVar('comp.card.borderWidth')).toBe('--comp-card-border-width')
  })

  it('resolves card interaction tokens from system defaults', () => {
    const tokens = resolveTokenReferences(getSystemDefaults('light'))

    expect(tokens['comp.card.borderHover']).toBe(tokens['palette.brand.600'])
    expect(tokens['comp.card.shadowHover']).toContain(tokens['palette.brand.600'])
    expect(tokens['comp.card.shadowHover']).toContain(tokens['palette.brand.500'])
    expect(tokens['comp.card.ringHover']).toContain(tokens['palette.brand.600'])
  })

  it('keeps dark shell surfaces aligned with the design-system shell reference', () => {
    const tokens = resolveTokenReferences(getSystemDefaults('dark'))

    expect(tokens['color.background']).toBe('#0D0F14')
    expect(tokens['color.surface']).toBe('#161922')
    expect(tokens['color.surfaceContainerLow']).toBe('#161922')
    expect(tokens['color.border.subtle']).toBe('#22262F')
    expect(tokens['color.text.primary']).toBe('#ECECF1')
    expect(tokens['color.primary']).toBe('#7C40E8')
    expect(tokens['color.onPrimary']).toBe('#FFFFFF')
    expect(tokens['color.primaryContainer']).toBe('color-mix(in srgb, #7C40E8 18%, transparent)')
    expect(tokens['color.feedback.successContainer']).toBe('color-mix(in srgb, #00E963 12%, transparent)')
    expect(tokens['color.feedback.successBorder']).toBe('color-mix(in srgb, #66BFA0 28%, transparent)')
  })

  it('resolves nested semantic references used by shell component tokens', () => {
    const tokens = resolveTokenReferences({...getSystemDefaults('dark'), ...appShellTokenDefaults, ...sidebarNavTokenDefaults, ...statusPillTokenDefaults})

    expect(tokens['color.navigation.activeForeground']).toBe(tokens['color.onPrimary'])
    expect(tokens['comp.snav.itemFgActive']).toBe(tokens['color.onPrimary'])
    expect(tokens['comp.snav.itemBgActive']).toBe('color-mix(in srgb, #7C40E8 18%, transparent)')
    expect(tokens['comp.snav.itemBackgroundActive']).toBe(
      'linear-gradient(90deg, color-mix(in srgb, #7C40E8 18%, transparent) 0%, color-mix(in srgb, #7C40E8 10%, transparent) 58%, transparent 100%)',
    )
    expect(tokens['comp.snav.itemBorderActive']).toBe('color-mix(in srgb, #7C40E8 34%, transparent)')
    expect(tokens['comp.statuspill.success.background']).toBe('color-mix(in srgb, #00E963 12%, transparent)')
    expect(tokens['comp.appshell.controlBackgroundHover']).toBe('color-mix(in srgb, #FFFFFF 6%, transparent)')
    expect(tokens['comp.snav.itemBgActive']).not.toContain('{')
    expect(tokens['comp.snav.itemBackgroundActive']).not.toContain('{')
    expect(tokens['comp.statuspill.success.background']).not.toContain('{')
  })

  it('uses the design-system light foreground for active sidebar items', () => {
    const defaults = getSystemDefaults('light')
    const tokens = resolveTokenReferences({...getSystemDefaults('light'), ...sidebarNavTokenDefaults, ...navTokenDefaults})

    expect(defaults['color.navigation.activeForeground']).toBe('{palette.brand.700}')
    expect(tokens['color.navigation.activeForeground']).toBe(tokens['palette.brand.700'])
    expect(tokens['comp.snav.itemFgActive']).toBe(tokens['palette.brand.700'])
    expect(tokens['comp.nav.side.active.foreground']).toBe(tokens['palette.brand.700'])
  })
})
