/**
 * Default Tier 3 component token values for IDK / EDK / VDX components.
 * Values reference Tier 2 semantic tokens via `{references}`, enabling
 * brand-adaptive component styling through the token resolution chain.
 *
 * Aligned with the Sphereon Wallet design system.
 */

import type { ThemeTokenMap } from './types'

/** Button — primary (gradient), secondary (outlined), ghost. */
export const buttonTokenDefaults: ThemeTokenMap = {
  // Primary
  'comp.button.primary.background': '{color.primary}',
  'comp.button.primary.backgroundHover': '{palette.brand.600}',
  'comp.button.primary.backgroundActive': '{palette.brand.700}',
  'comp.button.primary.foreground': '{color.onPrimary}',
  'comp.button.primary.border': '{color.primary}',
  'comp.button.primary.borderWidth': '{borderWidth.none}',
  'comp.button.primary.radius': '{shape.radius.3}',
  'comp.button.primary.shadow': '{shadow.subtle}',
  'comp.button.primary.paddingX': '{spacing.inline.md}',
  'comp.button.primary.paddingY': '{spacing.stack.sm}',
  'comp.button.primary.height': '42px',

  // Secondary (outlined)
  'comp.button.secondary.background': 'transparent',
  'comp.button.secondary.backgroundHover': '{palette.brand.50}',
  'comp.button.secondary.backgroundActive': '{palette.brand.100}',
  'comp.button.secondary.foreground': '{color.text.primary}',
  'comp.button.secondary.border': '{color.secondary}',
  'comp.button.secondary.borderWidth': '{borderWidth.thin}',
  'comp.button.secondary.radius': '{shape.radius.3}',
  'comp.button.secondary.height': '42px',

  // Ghost
  'comp.button.ghost.background': 'transparent',
  'comp.button.ghost.backgroundHover': '{palette.brand.50}',
  'comp.button.ghost.foreground': '{color.primary}',
  'comp.button.ghost.radius': '{shape.radius.3}',
  'comp.button.ghost.height': '42px',
}

/** Tab. */
export const tabTokenDefaults: ThemeTokenMap = {
  'comp.tab.background': '{color.surface}',
  'comp.tab.foreground': '{color.text.secondary}',
  'comp.tab.active.foreground': '{color.primary}',
  'comp.tab.active.indicator': '{color.primary}',
  'comp.tab.border': '{color.border.subtle}',
}

/** Modal. */
export const modalTokenDefaults: ThemeTokenMap = {
  'comp.modal.background': '{color.surface}',
  'comp.modal.foreground': '{color.onSurface}',
  'comp.modal.border': '{color.border.subtle}',
  'comp.modal.shadow': '{shadow.overlay}',
  'comp.modal.overlay': '{color.scrim}',
  'comp.modal.radius': '{shape.radius.xl}',
}

/** Input — M3 outlined-notch style with error state and 56dp min height. */
export const inputTokenDefaults: ThemeTokenMap = {
  'comp.input.background': '{color.surface}',
  'comp.input.foreground': '{color.onSurface}',
  'comp.input.border': '{color.border.default}',
  'comp.input.border.focus': '{color.primary}',
  'comp.input.borderError': '{color.error}',
  'comp.input.placeholder': '{color.text.disabled}',
  'comp.input.radius': '{shape.radius.2}',
  'comp.input.minHeight': '56px',
  'comp.input.paddingX': '{spacing.inline.sm}',
  'comp.input.paddingY': '{spacing.stack.xs}',
}

/** Card — credentials and utility cards. */
export const cardTokenDefaults: ThemeTokenMap = {
  'comp.card.background': '{color.surfaceContainerLow}',
  'comp.card.foreground': '{color.onSurface}',
  'comp.card.border': '{color.border.subtle}',
  'comp.card.borderWidth': '{borderWidth.thin}',
  'comp.card.radius': '{shape.radius.xl}',
  'comp.card.shadow': '{shadow.raised}',
  'comp.card.padding': '{spacing.inset.md}',
}

/** Badge — status pills, count badges. */
export const badgeTokenDefaults: ThemeTokenMap = {
  'comp.badge.background': '{color.secondaryContainer}',
  'comp.badge.foreground': '{color.onSecondaryContainer}',
  'comp.badge.border': '{color.border.subtle}',
  'comp.badge.borderWidth': '{borderWidth.none}',
  'comp.badge.radius': '{shape.radius.full}',
  'comp.badge.paddingX': '{spacing.inline.sm}',
  'comp.badge.paddingY': '{spacing.stack.xs}',
  'comp.badge.error.background': '{color.error}',
  'comp.badge.error.foreground': '{color.onError}',
}

/** Chip — toggleable filter chip distinct from badge. */
export const chipTokenDefaults: ThemeTokenMap = {
  'comp.chip.background': '{color.surface}',
  'comp.chip.backgroundActive': '{color.text.primary}',
  'comp.chip.foreground': '{color.text.primary}',
  'comp.chip.foregroundActive': '{color.onPrimary}',
  'comp.chip.border': '{color.border.default}',
  'comp.chip.radius': '{shape.radius.full}',
}

/** Avatar — five sizes plus ringed-on-photo variant. */
export const avatarTokenDefaults: ThemeTokenMap = {
  'comp.avatar.background': '{color.secondaryContainer}',
  'comp.avatar.foreground': '{color.onSecondaryContainer}',
  'comp.avatar.radius': '{shape.radius.full}',
  'comp.avatar.border': '{color.surface}',
  'comp.avatar.borderWidth': '2px',
  'comp.avatar.size.xs': '24px',
  'comp.avatar.size.sm': '32px',
  'comp.avatar.size.md': '40px',
  'comp.avatar.size.lg': '56px',
  'comp.avatar.size.xl': '72px',
}

/** Progress — linear and circular. */
export const progressTokenDefaults: ThemeTokenMap = {
  'comp.progress.track': '{palette.gray.200}',
  'comp.progress.indicator': '{color.primary}',
  'comp.progress.success': '{color.feedback.success}',
  'comp.progress.error': '{color.error}',
  'comp.progress.trackHeight': '4px',
  'comp.progress.radius': '{shape.radius.full}',
}

/** Navigation — side / top / bottom. */
export const navTokenDefaults: ThemeTokenMap = {
  // Side rail
  'comp.nav.side.background': '{palette.gray.100}',
  'comp.nav.side.foreground': '{color.text.primary}',
  'comp.nav.side.active.background': '{palette.brand.50}',
  'comp.nav.side.active.foreground': '{color.primary}',
  'comp.nav.side.sectionLabel': '{color.text.secondary}',
  'comp.nav.side.divider': '{color.border.default}',
  'comp.nav.side.width': '200px',
  'comp.nav.side.itemHeight': '40px',
  // Top app bar
  'comp.nav.top.background': '{palette.blue.900}',
  'comp.nav.top.foreground': '{color.onPrimary}',
  'comp.nav.top.height': '70px',
  'comp.nav.top.divider': '{palette.blue.600}',
  // Bottom tab bar
  'comp.nav.bottom.background': '{color.surface}',
  'comp.nav.bottom.foreground': '{color.text.secondary}',
  'comp.nav.bottom.active': '{color.primary}',
  'comp.nav.bottom.divider': '{palette.blue.600}',
  'comp.nav.bottom.height': '52px',
  'comp.nav.bottom.target': '42px',
}

/** Snackbar — sits at the bottom edge of the surface, distinct from toast. */
export const snackbarTokenDefaults: ThemeTokenMap = {
  'comp.snackbar.background': '{color.inverseSurface}',
  'comp.snackbar.foreground': '{color.inverseOnSurface}',
  'comp.snackbar.radius': '{shape.radius.md}',
  'comp.snackbar.shadow': '{shadow.floating}',
  'comp.snackbar.padding': '{spacing.inset.md}',
}

/** List item. */
export const listTokenDefaults: ThemeTokenMap = {
  'comp.list.item.background': 'transparent',
  'comp.list.item.backgroundHover': '{color.interactive.hover}',
  'comp.list.item.foreground': '{color.onSurface}',
  'comp.list.item.foregroundSecondary': '{color.text.secondary}',
  'comp.list.item.divider': '{color.border.subtle}',
  'comp.list.item.paddingX': '{spacing.inline.md}',
  'comp.list.item.paddingY': '{spacing.stack.sm}',
  'comp.list.item.radius': '{shape.radius.md}',
}

/** Live preview — issuer-side credential preview frame with dashed border. */
export const livePreviewTokenDefaults: ThemeTokenMap = {
  'comp.livePreview.background': '{color.surface}',
  'comp.livePreview.border': '#8A38F5',
  'comp.livePreview.borderWidth': '{borderWidth.thin}',
  'comp.livePreview.radius': '{shape.radius.xl}',
  'comp.livePreview.padding': '{spacing.inset.md}',
}

/** Toast / Notification. */
export const toastTokenDefaults: ThemeTokenMap = {
  'comp.toast.background': '{color.inverseSurface}',
  'comp.toast.foreground': '{color.inverseOnSurface}',
  'comp.toast.radius': '{shape.radius.md}',
  'comp.toast.shadow': '{shadow.floating}',
  'comp.toast.padding': '{spacing.inset.md}',
  'comp.toast.success.background': '{color.feedback.success}',
  'comp.toast.success.foreground': '{color.feedback.onSuccess}',
  'comp.toast.error.background': '{color.error}',
  'comp.toast.error.foreground': '{color.onError}',
  'comp.toast.warning.background': '{color.feedback.warning}',
  'comp.toast.warning.foreground': '{color.feedback.onWarning}',
  'comp.toast.info.background': '{color.feedback.info}',
  'comp.toast.info.foreground': '{color.feedback.onInfo}',
  'comp.toast.dismissible': 'true',
}

/** Checkbox. */
export const checkboxTokenDefaults: ThemeTokenMap = {
  'comp.checkbox.border': '{color.border.default}',
  'comp.checkbox.borderWidth': '{borderWidth.medium}',
  'comp.checkbox.radius': '{shape.radius.xs}',
  'comp.checkbox.checked.background': '{color.primary}',
  'comp.checkbox.checked.foreground': '{color.onPrimary}',
  'comp.checkbox.size': '20px',
  'comp.checkbox.label.foreground': '{color.text.primary}',
  'comp.checkbox.disabled.background': '{color.interactive.disabled}',
}

/** Radio. */
export const radioTokenDefaults: ThemeTokenMap = {
  'comp.radio.border': '{color.border.default}',
  'comp.radio.borderWidth': '{borderWidth.medium}',
  'comp.radio.selected.border': '{color.primary}',
  'comp.radio.selected.indicator': '{color.primary}',
  'comp.radio.size': '20px',
  'comp.radio.label.foreground': '{color.text.primary}',
  'comp.radio.disabled.border': '{color.border.disabled}',
}

/** Select / Dropdown. */
export const selectTokenDefaults: ThemeTokenMap = {
  'comp.select.background': '{color.surface}',
  'comp.select.foreground': '{color.onSurface}',
  'comp.select.border': '{color.border.default}',
  'comp.select.border.focus': '{color.primary}',
  'comp.select.radius': '{shape.radius.sm}',
  'comp.select.paddingX': '{spacing.inline.sm}',
  'comp.select.paddingY': '{spacing.stack.xs}',
  'comp.select.menu.background': '{color.surfaceContainerHigh}',
  'comp.select.menu.shadow': '{shadow.floating}',
  'comp.select.menu.radius': '{shape.radius.md}',
  'comp.select.option.hover': '{color.interactive.hover}',
  'comp.select.placeholder': '{color.text.disabled}',
}

/** Blob explorer. */
export const blobExplorerTokenDefaults: ThemeTokenMap = {
  'comp.blobExplorer.background': '{color.surface}',
  'comp.blobExplorer.foreground': '{color.onSurface}',
  'comp.blobExplorer.border': '{color.border.subtle}',
  'comp.blobExplorer.radius': '{shape.radius.md}',
  'comp.blobExplorer.padding': '{spacing.inset.md}',
  'comp.blobExplorer.toolbar.background': '{color.surfaceContainerLow}',
  'comp.blobExplorer.toolbar.border': '{color.border.subtle}',
  'comp.blobExplorer.sidebar.background': '{color.surfaceContainerLow}',
  'comp.blobExplorer.sidebar.width': '240px',
  'comp.blobExplorer.sidebar.border': '{color.border.subtle}',
  'comp.blobExplorer.item.background': 'transparent',
  'comp.blobExplorer.item.backgroundHover': '{color.interactive.hover}',
  'comp.blobExplorer.item.backgroundSelected': '{color.secondaryContainer}',
  'comp.blobExplorer.item.foreground': '{color.onSurface}',
  'comp.blobExplorer.item.foregroundSecondary': '{color.text.secondary}',
  'comp.blobExplorer.item.padding': '{spacing.inset.sm}',
  'comp.blobExplorer.item.radius': '{shape.radius.sm}',
  'comp.blobExplorer.breadcrumb.foreground': '{color.text.secondary}',
  'comp.blobExplorer.breadcrumb.foregroundActive': '{color.onSurface}',
  'comp.blobExplorer.breadcrumb.separator': '{color.border.default}',
  'comp.blobExplorer.detail.background': '{color.surfaceContainerLow}',
  'comp.blobExplorer.detail.border': '{color.border.subtle}',
  'comp.blobExplorer.detail.width': '320px',
  'comp.blobExplorer.empty.foreground': '{color.text.secondary}',
  'comp.blobExplorer.empty.iconColor': '{color.border.subtle}',
}

/** All component tokens combined. */
export const allComponentTokenDefaults: ThemeTokenMap = {
  ...buttonTokenDefaults,
  ...tabTokenDefaults,
  ...modalTokenDefaults,
  ...inputTokenDefaults,
  ...cardTokenDefaults,
  ...badgeTokenDefaults,
  ...chipTokenDefaults,
  ...avatarTokenDefaults,
  ...progressTokenDefaults,
  ...navTokenDefaults,
  ...snackbarTokenDefaults,
  ...listTokenDefaults,
  ...livePreviewTokenDefaults,
  ...toastTokenDefaults,
  ...checkboxTokenDefaults,
  ...radioTokenDefaults,
  ...selectTokenDefaults,
  ...blobExplorerTokenDefaults,
}
