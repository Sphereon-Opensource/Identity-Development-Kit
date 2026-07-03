/**
 * Tier 3 component token defaults.
 *
 * GENERATED from tokens.json (comp.*) by build/generate-theme-react.mjs.
 * Do NOT hand-edit; change the DTCG source and regenerate.
 */

import type { ThemeTokenMap } from './types'

export const buttonTokenDefaults: ThemeTokenMap = {
  'comp.button.primary.background': '{color.primary}',
  'comp.button.primary.backgroundHover': '{palette.brand.600}',
  'comp.button.primary.backgroundActive': '{palette.brand.800}',
  'comp.button.primary.foreground': '{color.onPrimary}',
  'comp.button.primary.border': '{palette.brand.700}',
  'comp.button.primary.borderWidth': '{borderWidth.none}',
  'comp.button.primary.radius': '{shape.radius.md}',
  'comp.button.primary.shadow': '{shadow.subtle}',
  'comp.button.primary.paddingX': '{spacing.inline.md}',
  'comp.button.primary.paddingY': '{spacing.stack.sm}',
  'comp.button.primary.height': '42px',
  'comp.button.secondary.background': 'transparent',
  'comp.button.secondary.backgroundHover': '{color.interactive.hover}',
  'comp.button.secondary.backgroundActive': '{color.interactive.pressed}',
  'comp.button.secondary.foreground': '{color.text.primary}',
  'comp.button.secondary.border': '{color.secondary}',
  'comp.button.secondary.borderWidth': '{borderWidth.thin}',
  'comp.button.secondary.radius': '{shape.radius.3}',
  'comp.button.secondary.height': '42px',
  'comp.button.ghost.background': 'transparent',
  'comp.button.ghost.backgroundHover': '{color.interactive.hover}',
  'comp.button.ghost.foreground': '{color.primary}',
  'comp.button.ghost.radius': '{shape.radius.3}',
  'comp.button.ghost.height': '42px',
}

export const tabTokenDefaults: ThemeTokenMap = {
  'comp.tab.background': '{color.surface}',
  'comp.tab.foreground': '{color.text.secondary}',
  'comp.tab.active.foreground': '{color.primary}',
  'comp.tab.active.indicator': '{color.primary}',
  'comp.tab.border': '{color.border.subtle}',
}

export const modalTokenDefaults: ThemeTokenMap = {
  'comp.modal.background': '{color.surface}',
  'comp.modal.foreground': '{color.onSurface}',
  'comp.modal.border': '{color.border.subtle}',
  'comp.modal.shadow': '{shadow.overlay}',
  'comp.modal.overlay': '{color.scrim}',
  'comp.modal.radius': '{shape.radius.xl}',
}

export const inputTokenDefaults: ThemeTokenMap = {
  'comp.input.background': '{color.surface}',
  'comp.input.foreground': '{color.onSurface}',
  'comp.input.border': '{color.border.default}',
  'comp.input.border.focus': '{color.primary}',
  'comp.input.borderError': '{color.error}',
  'comp.input.placeholder': '{color.text.disabled}',
  'comp.input.radius': '{shape.radius.2}',
  'comp.input.minHeight': '40px',
  'comp.input.paddingX': '{spacing.3_5}',
  'comp.input.paddingY': '{spacing.stack.xs}',
}

export const cardTokenDefaults: ThemeTokenMap = {
  'comp.card.background': '{color.surfaceContainerLow}',
  'comp.card.foreground': '{color.onSurface}',
  'comp.card.border': '{color.border.subtle}',
  'comp.card.borderHover': '{palette.brand.600}',
  'comp.card.borderWidth': '{borderWidth.thin}',
  'comp.card.radius': '{shape.radius.lg}',
  'comp.card.shadow': '{shadow.raised}',
  'comp.card.shadowHover': '{shadow.subtle}, 0 0 0 1px {palette.brand.600}, 0 0 0 3px color-mix(in srgb, {palette.brand.600} 16%, transparent), 0 0 18px 4px color-mix(in srgb, {palette.brand.600} 30%, transparent), 0 0 48px 10px color-mix(in srgb, {palette.brand.500} 18%, transparent)',
  'comp.card.ringHover': '0 0 0 3px color-mix(in srgb, {palette.brand.600} 52%, transparent)',
  'comp.card.padding': '{spacing.inset.md}',
}

export const appShellTokenDefaults: ThemeTokenMap = {
  'comp.appshell.topHeight': '{spacing.14}',
  'comp.appshell.topBackground': '{color.surface}',
  'comp.appshell.topForeground': '{color.text.primary}',
  'comp.appshell.topBorder': '{color.border.subtle}',
  'comp.appshell.topPadding': '0 {spacing.5}',
  'comp.appshell.topGap': '{spacing.4}',
  'comp.appshell.brandForeground': '{color.text.primary}',
  'comp.appshell.mainBackground': '{color.background}',
  'comp.appshell.controlForeground': '{color.text.secondary}',
  'comp.appshell.controlForegroundHover': '{color.text.primary}',
  'comp.appshell.controlBackgroundHover': '{color.interactive.hover}',
  'comp.appshell.controlRadius': '{shape.radius.md}',
}

export const sidebarNavTokenDefaults: ThemeTokenMap = {
  'comp.snav.width': '248px',
  'comp.snav.widthRail': '68px',
  'comp.snav.pad': '{spacing.3}',
  'comp.snav.gap': '{spacing.0_5}',
  'comp.snav.bg': '{color.surface}',
  'comp.snav.border': '{color.border.subtle}',
  'comp.snav.itemHeight': '40px',
  'comp.snav.itemRadius': '{shape.radius.md}',
  'comp.snav.itemFg': '{color.text.secondary}',
  'comp.snav.itemFgHover': '{color.text.primary}',
  'comp.snav.itemBgHover': '{color.interactive.hover}',
  'comp.snav.itemFgActive': '{color.navigation.activeForeground}',
  'comp.snav.itemBgActive': '{color.primaryContainer}',
  'comp.snav.itemBackgroundActive': 'linear-gradient(90deg, color-mix(in srgb, {color.primary} 18%, transparent) 0%, color-mix(in srgb, {color.primary} 10%, transparent) 58%, transparent 100%)',
  'comp.snav.itemBorderActive': 'color-mix(in srgb, {color.primary} 34%, transparent)',
  'comp.snav.itemIcon': '{color.text.disabled}',
  'comp.snav.itemIconActive': '{color.primary}',
  'comp.snav.groupFg': '{color.text.secondary}',
  'comp.snav.divider': '{color.border.subtle}',
  'comp.snav.badgeBg': '{color.primary}',
  'comp.snav.badgeFg': '{color.onPrimary}',
  'comp.snav.tipBg': '{color.inverseSurface}',
  'comp.snav.tipFg': '{color.inverseOnSurface}',
  'comp.snav.ease': '{motion.easing.emphasized}',
  'comp.snav.dur': '{motion.duration.medium2}',
}

export const tableTokenDefaults: ThemeTokenMap = {
  'comp.table.header.background': '{color.surfaceContainer}',
  'comp.table.header.foreground': '{color.text.secondary}',
  'comp.table.header.padding': '{spacing.2_5} {spacing.5}',
  'comp.table.header.fontSize': '{text.style.micro1.desktop.fontSize}',
  'comp.table.header.fontWeight': '600',
  'comp.table.header.height': '44px',
  'comp.table.header.tracking': '0.05em',
  'comp.table.row.background': '{color.surface}',
  'comp.table.row.backgroundHover': '{color.interactive.hover}',
  'comp.table.row.backgroundSelected': '{color.primaryContainer}',
  'comp.table.row.foreground': '{color.text.primary}',
  'comp.table.row.fontSize': '{text.style.micro1.desktop.fontSize}',
  'comp.table.row.padding': '{spacing.2_5} {spacing.5}',
  'comp.table.row.divider': '{color.border.subtle}',
  'comp.table.row.border': '{color.border.default}',
  'comp.table.row.minHeight': '48px',
  'comp.table.row.paddingX': '{spacing.5}',
  'comp.table.row.paddingY': '{spacing.2_5}',
  'comp.table.row.backgroundActive': '{color.primaryContainer}',
  'comp.table.row.ruleActive': '{color.primary}',
}

export const statusPillTokenDefaults: ThemeTokenMap = {
  'comp.statuspill.radius': '{shape.radius.full}',
  'comp.statuspill.borderWidth': '{borderWidth.thin}',
  'comp.statuspill.padding': '{spacing.0_5} {spacing.2}',
  'comp.statuspill.fontSize': '{text.style.micro2.desktop.fontSize}',
  'comp.statuspill.fontWeight': '600',
  'comp.statuspill.success.background': '{color.feedback.successContainer}',
  'comp.statuspill.success.foreground': '{color.feedback.onSuccessContainer}',
  'comp.statuspill.success.border': '{color.feedback.successBorder}',
  'comp.statuspill.warning.background': '{color.feedback.warningContainer}',
  'comp.statuspill.warning.foreground': '{color.feedback.onWarningContainer}',
  'comp.statuspill.warning.border': '{color.feedback.warningBorder}',
  'comp.statuspill.error.background': '{color.feedback.errorContainer}',
  'comp.statuspill.error.foreground': '{color.feedback.onErrorContainer}',
  'comp.statuspill.error.border': '{color.feedback.errorBorder}',
  'comp.statuspill.info.background': '{color.feedback.infoContainer}',
  'comp.statuspill.info.foreground': '{color.feedback.onInfoContainer}',
  'comp.statuspill.info.border': '{color.feedback.infoBorder}',
  'comp.statuspill.neutral.background': '{color.surfaceVariant}',
  'comp.statuspill.neutral.foreground': '{color.text.secondary}',
  'comp.statuspill.neutral.border': '{color.border.default}',
}

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

export const chipTokenDefaults: ThemeTokenMap = {
  'comp.chip.background': '{color.surface}',
  'comp.chip.backgroundActive': '{color.text.primary}',
  'comp.chip.foreground': '{color.text.primary}',
  'comp.chip.foregroundActive': '{color.onPrimary}',
  'comp.chip.border': '{color.border.default}',
  'comp.chip.radius': '{shape.radius.full}',
}

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

export const progressTokenDefaults: ThemeTokenMap = {
  'comp.progress.track': '{palette.gray.200}',
  'comp.progress.indicator': '{color.primary}',
  'comp.progress.success': '{color.feedback.success}',
  'comp.progress.error': '{color.error}',
  'comp.progress.trackHeight': '4px',
  'comp.progress.radius': '{shape.radius.full}',
}

export const navTokenDefaults: ThemeTokenMap = {
  'comp.nav.side.background': '{color.surface}',
  'comp.nav.side.foreground': '{color.text.primary}',
  'comp.nav.side.active.background': '{color.primaryContainer}',
  'comp.nav.side.active.foreground': '{color.navigation.activeForeground}',
  'comp.nav.side.sectionLabel': '{color.text.secondary}',
  'comp.nav.side.divider': '{color.border.subtle}',
  'comp.nav.side.width': '248px',
  'comp.nav.side.itemHeight': '40px',
  'comp.nav.top.background': '{color.surface}',
  'comp.nav.top.foreground': '{color.text.primary}',
  'comp.nav.top.height': '56px',
  'comp.nav.top.divider': '{color.border.subtle}',
  'comp.nav.bottom.background': '{color.surface}',
  'comp.nav.bottom.foreground': '{color.text.secondary}',
  'comp.nav.bottom.active': '{color.primary}',
  'comp.nav.bottom.divider': '{color.border.subtle}',
  'comp.nav.bottom.height': '52px',
  'comp.nav.bottom.target': '42px',
}

export const snackbarTokenDefaults: ThemeTokenMap = {
  'comp.snackbar.background': '{color.inverseSurface}',
  'comp.snackbar.foreground': '{color.inverseOnSurface}',
  'comp.snackbar.radius': '{shape.radius.md}',
  'comp.snackbar.shadow': '{shadow.floating}',
  'comp.snackbar.padding': '{spacing.inset.md}',
}

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

export const livePreviewTokenDefaults: ThemeTokenMap = {
  'comp.livePreview.background': '{color.surface}',
  'comp.livePreview.border': '#8A38F5',
  'comp.livePreview.borderWidth': '{borderWidth.thin}',
  'comp.livePreview.radius': '{shape.radius.xl}',
  'comp.livePreview.padding': '{spacing.inset.md}',
}

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

export const radioTokenDefaults: ThemeTokenMap = {
  'comp.radio.border': '{color.border.default}',
  'comp.radio.borderWidth': '{borderWidth.medium}',
  'comp.radio.selected.border': '{color.primary}',
  'comp.radio.selected.indicator': '{color.primary}',
  'comp.radio.size': '20px',
  'comp.radio.label.foreground': '{color.text.primary}',
  'comp.radio.disabled.border': '{color.border.disabled}',
}

export const selectTokenDefaults: ThemeTokenMap = {
  'comp.select.background': '{color.surface}',
  'comp.select.foreground': '{color.onSurface}',
  'comp.select.border': '{color.border.default}',
  'comp.select.border.focus': '{color.primary}',
  'comp.select.radius': '{shape.radius.sm}',
  'comp.select.minHeight': '40px',
  'comp.select.paddingX': '{spacing.3_5}',
  'comp.select.paddingY': '{spacing.stack.xs}',
  'comp.select.menu.background': '{color.surfaceContainerHigh}',
  'comp.select.menu.shadow': '{shadow.floating}',
  'comp.select.menu.radius': '{shape.radius.md}',
  'comp.select.option.hover': '{color.interactive.hover}',
  'comp.select.placeholder': '{color.text.disabled}',
}

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

export const menuTokenDefaults: ThemeTokenMap = {
  'comp.menu.background': '{color.surface}',
  'comp.menu.border': '{color.border.default}',
  'comp.menu.minWidth': '180px',
  'comp.menu.padding': '{spacing.1_5}',
  'comp.menu.radius': '{shape.radius.md}',
  'comp.menu.shadow': '{shadow.overlay}',
  'comp.menu.item.padding': '{spacing.2_5} {spacing.3}',
  'comp.menu.item.radius': '{shape.radius.sm}',
  'comp.menu.item.backgroundHover': '{color.interactive.hover}',
  'comp.menu.item.fontSize': '{text.style.subtitle1.desktop.fontSize}',
  'comp.menu.item.foreground': '{color.text.primary}',
  'comp.menu.item.danger.foreground': '{color.error}',
  'comp.menu.item.danger.backgroundHover': '{color.errorContainer}',
}

export const panelTokenDefaults: ThemeTokenMap = {
  'comp.panel.background': '{color.surface}',
  'comp.panel.border': '{color.border.default}',
  'comp.panel.header.background': '{color.surfaceVariant}',
  'comp.panel.header.foreground': '{color.text.primary}',
  'comp.panel.header.height': '{spacing.14}',
  'comp.panel.width.sm': '100%',
  'comp.panel.width.md': '380px',
  'comp.panel.width.lg': '420px',
  'comp.panel.width.xl': '480px',
  'comp.panel.shadow': '{shadow.overlay}',
}

export const statustabTokenDefaults: ThemeTokenMap = {
  'comp.statustab.padding': '{spacing.2} {spacing.3_5}',
  'comp.statustab.radius': '{shape.radius.md}',
  'comp.statustab.fontSize': '{text.style.subtitle1.desktop.fontSize}',
  'comp.statustab.fontWeight': '500',
  'comp.statustab.background': 'transparent',
  'comp.statustab.backgroundActive': '{palette.brand.50}',
  'comp.statustab.foreground': '{color.text.secondary}',
  'comp.statustab.foregroundActive': '{color.primary}',
}

export const formTokenDefaults: ThemeTokenMap = {
  'comp.form.background': '{color.surfaceContainerLow}',
  'comp.form.border': '{color.border.default}',
  'comp.form.radius': '{shape.radius.xxxl}',
  'comp.form.padding': '{spacing.12} {spacing.6}',
}

export const formsectionTokenDefaults: ThemeTokenMap = {
  'comp.formsection.border': '{color.border.default}',
  'comp.formsection.radius': '{shape.radius.md}',
  'comp.formsection.padding': '{spacing.4}',
  'comp.formsection.gap': '{spacing.3}',
  'comp.formsection.legend.background': '{color.surfaceContainerLow}',
  'comp.formsection.legend.foreground': '{color.text.secondary}',
  'comp.formsection.legend.paddingX': '{spacing.2}',
  'comp.formsection.legend.fontSize': '{text.style.micro1.desktop.fontSize}',
  'comp.formsection.legend.fontWeight': '600',
}

export const forminputTokenDefaults: ThemeTokenMap = {
  'comp.forminput.background': '{color.surface}',
  'comp.forminput.backgroundReadonly': 'transparent',
  'comp.forminput.border': '{color.border.default}',
  'comp.forminput.borderHover': '{color.text.disabled}',
  'comp.forminput.borderFocus': '{color.primary}',
  'comp.forminput.borderReadonly': 'transparent',
  'comp.forminput.radius': '{shape.radius.md}',
  'comp.forminput.padding': '{spacing.3} {spacing.3_5}',
  'comp.forminput.fontSize': '{text.style.subtitle1.desktop.fontSize}',
}

export const emptystateTokenDefaults: ThemeTokenMap = {
  'comp.emptystate.iconSize': '{spacing.16}',
  'comp.emptystate.maxWidth': '400px',
  'comp.emptystate.foreground': '{color.text.secondary}',
  'comp.emptystate.title.fontSize': '{text.style.h2.desktop.fontSize}',
  'comp.emptystate.title.fontWeight': '600',
  'comp.emptystate.body.fontSize': '{text.style.subtitle1.desktop.fontSize}',
  'comp.emptystate.gap': '{spacing.3}',
}

export const selectionTokenDefaults: ThemeTokenMap = {
  'comp.selection.background': '{color.primaryContainer}',
  'comp.selection.foreground': '{color.onPrimaryContainer}',
  'comp.selection.deselect.border': '{color.primary}',
  'comp.selection.deselect.backgroundHover': '{color.primary}',
  'comp.selection.deselect.foregroundHover': '{color.onPrimary}',
  'comp.selection.delete.background': '{color.error}',
  'comp.selection.delete.backgroundHover': '{palette.error.600}',
  'comp.selection.delete.foreground': '{color.onError}',
}

export const confirmTokenDefaults: ThemeTokenMap = {
  'comp.confirm.overlay.background': 'color-mix(in oklab, black 50%, transparent)',
  'comp.confirm.modal.background': '{color.surface}',
  'comp.confirm.modal.radius': '{shape.radius.lg}',
  'comp.confirm.modal.shadow': '0 20px 40px color-mix(in oklab, black 20%, transparent)',
  'comp.confirm.modal.width': '400px',
  'comp.confirm.icon.size': '56px',
  'comp.confirm.icon.background': '{color.errorContainer}',
  'comp.confirm.icon.foreground': '{color.error}',
  'comp.confirm.title.fontSize': '18px',
  'comp.confirm.message.fontSize': '14px',
  'comp.confirm.message.foreground': '{color.text.secondary}',
}

export const meatballTokenDefaults: ThemeTokenMap = {
  'comp.meatball.size': '32px',
  'comp.meatball.background': 'transparent',
  'comp.meatball.backgroundHover': '{palette.gray.100}',
  'comp.meatball.foreground': '{color.text.disabled}',
  'comp.meatball.foregroundHover': '{color.text.primary}',
}

export const ringTokenDefaults: ThemeTokenMap = {
  'comp.focus.ring': '{shadow.focusRing}',
  'comp.error.ring': '{shadow.errorRing}',
}

export const allComponentTokenDefaults: ThemeTokenMap = {
  ...buttonTokenDefaults,
  ...tabTokenDefaults,
  ...modalTokenDefaults,
  ...inputTokenDefaults,
  ...cardTokenDefaults,
  ...appShellTokenDefaults,
  ...sidebarNavTokenDefaults,
  ...tableTokenDefaults,
  ...statusPillTokenDefaults,
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
  ...menuTokenDefaults,
  ...panelTokenDefaults,
  ...statustabTokenDefaults,
  ...formTokenDefaults,
  ...formsectionTokenDefaults,
  ...forminputTokenDefaults,
  ...emptystateTokenDefaults,
  ...selectionTokenDefaults,
  ...confirmTokenDefaults,
  ...meatballTokenDefaults,
  ...ringTokenDefaults,
}
