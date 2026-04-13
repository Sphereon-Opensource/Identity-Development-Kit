/**
 * Default Tier 3 component token values for EDK components.
 * Values reference Tier 2 semantic tokens via `{references}`, enabling
 * brand-adaptive component styling through the token resolution chain.
 */

import type { ThemeTokenMap } from './types'

/** Button component tokens — all variants */
export const buttonTokenDefaults: ThemeTokenMap = {
  'comp.button.primary.background': '{color.primary}',
  'comp.button.primary.foreground': '{color.onPrimary}',
  'comp.button.primary.border': '{color.primary}',
  'comp.button.primary.borderWidth': '{borderWidth.none}',
  'comp.button.primary.radius': '{shape.radius.md}',
  'comp.button.primary.shadow': '{shadow.subtle}',
  'comp.button.primary.paddingX': '{spacing.inline.md}',
  'comp.button.primary.paddingY': '{spacing.stack.sm}',

  'comp.button.secondary.background': '{color.secondaryContainer}',
  'comp.button.secondary.foreground': '{color.onSecondaryContainer}',
  'comp.button.secondary.border': '{color.border.default}',
  'comp.button.secondary.borderWidth': '{borderWidth.thin}',
  'comp.button.secondary.radius': '{shape.radius.md}',

  'comp.button.ghost.background': 'transparent',
  'comp.button.ghost.foreground': '{color.primary}',
}

/** Tab component tokens */
export const tabTokenDefaults: ThemeTokenMap = {
  'comp.tab.background': '{color.surface}',
  'comp.tab.foreground': '{color.text.secondary}',
  'comp.tab.active.foreground': '{color.primary}',
  'comp.tab.active.indicator': '{color.primary}',
  'comp.tab.border': '{color.border.subtle}',
}

/** Modal component tokens */
export const modalTokenDefaults: ThemeTokenMap = {
  'comp.modal.background': '{color.surface}',
  'comp.modal.foreground': '{color.onSurface}',
  'comp.modal.border': '{color.border.subtle}',
  'comp.modal.shadow': '{shadow.overlay}',
  'comp.modal.overlay': '{color.scrim}',
  'comp.modal.radius': '{shape.radius.xl}',
}

/** Input component tokens */
export const inputTokenDefaults: ThemeTokenMap = {
  'comp.input.background': '{color.surface}',
  'comp.input.foreground': '{color.onSurface}',
  'comp.input.border': '{color.border.default}',
  'comp.input.border.focus': '{color.primary}',
  'comp.input.placeholder': '{color.text.disabled}',
  'comp.input.radius': '{shape.radius.sm}',
  'comp.input.paddingX': '{spacing.inline.sm}',
  'comp.input.paddingY': '{spacing.stack.xs}',
}

/** Card component tokens */
export const cardTokenDefaults: ThemeTokenMap = {
  'comp.card.background': '{color.surfaceContainerLow}',
  'comp.card.foreground': '{color.onSurface}',
  'comp.card.border': '{color.border.subtle}',
  'comp.card.borderWidth': '{borderWidth.thin}',
  'comp.card.radius': '{shape.radius.lg}',
  'comp.card.shadow': '{shadow.raised}',
  'comp.card.padding': '{spacing.inset.md}',
}

/** Badge / Chip component tokens */
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

/** Checkbox component tokens */
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

/** Radio component tokens */
export const radioTokenDefaults: ThemeTokenMap = {
  'comp.radio.border': '{color.border.default}',
  'comp.radio.borderWidth': '{borderWidth.medium}',
  'comp.radio.selected.border': '{color.primary}',
  'comp.radio.selected.indicator': '{color.primary}',
  'comp.radio.size': '20px',
  'comp.radio.label.foreground': '{color.text.primary}',
  'comp.radio.disabled.border': '{color.border.disabled}',
}

/** Select / Dropdown component tokens */
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

/** Toast / Notification component tokens */
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

/** Blob Explorer component tokens */
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

/** All component tokens combined */
export const allComponentTokenDefaults: ThemeTokenMap = {
  ...buttonTokenDefaults,
  ...tabTokenDefaults,
  ...modalTokenDefaults,
  ...inputTokenDefaults,
  ...cardTokenDefaults,
  ...badgeTokenDefaults,
  ...checkboxTokenDefaults,
  ...radioTokenDefaults,
  ...selectTokenDefaults,
  ...toastTokenDefaults,
  ...blobExplorerTokenDefaults,
}
