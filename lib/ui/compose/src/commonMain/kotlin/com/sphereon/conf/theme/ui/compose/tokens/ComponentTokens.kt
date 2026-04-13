package com.sphereon.conf.theme.ui.compose.tokens

import androidx.compose.runtime.Immutable

/**
 * Resolved component token values ready for Compose consumption.
 * String values match CSS-compatible formats (colors as hex, dimensions as "Npx").
 */

@Immutable
data class ButtonTokens(
    val primaryBackground: String = "#6750A4",
    val primaryForeground: String = "#FFFFFF",
    val primaryBorder: String = "#6750A4",
    val primaryBorderWidth: String = "0px",
    val primaryRadius: String = "12px",
    val primaryShadow: String = "none",
    val primaryPaddingX: String = "24px",
    val primaryPaddingY: String = "12px",
    val secondaryBackground: String = "#E8DEF8",
    val secondaryForeground: String = "#1D192B",
    val secondaryBorder: String = "#79747E",
    val secondaryBorderWidth: String = "1px",
    val secondaryRadius: String = "12px",
    val ghostBackground: String = "transparent",
    val ghostForeground: String = "#6750A4",
)

@Immutable
data class InputTokens(
    val background: String = "#FFFBFE",
    val foreground: String = "#1C1B1F",
    val border: String = "#79747E",
    val borderFocus: String = "#6750A4",
    val placeholder: String = "#49454F",
    val radius: String = "4px",
    val paddingX: String = "16px",
    val paddingY: String = "12px",
)

@Immutable
data class CheckboxTokens(
    val border: String = "#79747E",
    val borderWidth: String = "2px",
    val radius: String = "2px",
    val checkedBackground: String = "#6750A4",
    val checkedForeground: String = "#FFFFFF",
    val size: String = "20px",
    val labelForeground: String = "#1C1B1F",
    val disabledBackground: String = "#1C1B1F1F",
)

@Immutable
data class RadioTokens(
    val border: String = "#79747E",
    val borderWidth: String = "2px",
    val selectedBorder: String = "#6750A4",
    val selectedIndicator: String = "#6750A4",
    val size: String = "20px",
    val labelForeground: String = "#1C1B1F",
    val disabledBorder: String = "#1C1B1F1F",
)

@Immutable
data class CardTokens(
    val background: String = "#F7F2FA",
    val foreground: String = "#1C1B1F",
    val border: String = "#CAC4D0",
    val borderWidth: String = "1px",
    val radius: String = "16px",
    val shadow: String = "none",
    val padding: String = "16px",
)

@Immutable
data class BadgeTokens(
    val background: String = "#E8DEF8",
    val foreground: String = "#1D192B",
    val border: String = "#CAC4D0",
    val borderWidth: String = "0px",
    val radius: String = "9999px",
    val paddingX: String = "12px",
    val paddingY: String = "4px",
    val errorBackground: String = "#B3261E",
    val errorForeground: String = "#FFFFFF",
)

@Immutable
data class ModalTokens(
    val background: String = "#FFFBFE",
    val foreground: String = "#1C1B1F",
    val border: String = "#CAC4D0",
    val shadow: String = "none",
    val overlay: String = "#00000052",
    val radius: String = "28px",
)

@Immutable
data class TabTokens(
    val background: String = "#FFFBFE",
    val foreground: String = "#49454F",
    val activeForeground: String = "#6750A4",
    val activeIndicator: String = "#6750A4",
    val border: String = "#CAC4D0",
)

@Immutable
data class SelectTokens(
    val background: String = "#FFFBFE",
    val foreground: String = "#1C1B1F",
    val border: String = "#79747E",
    val borderFocus: String = "#6750A4",
    val radius: String = "4px",
    val paddingX: String = "16px",
    val paddingY: String = "12px",
    val menuBackground: String = "#ECE6F0",
    val menuShadow: String = "none",
    val menuRadius: String = "12px",
    val optionHover: String = "#6750A41F",
    val placeholder: String = "#49454F",
)

@Immutable
data class ToastTokens(
    val background: String = "#313033",
    val foreground: String = "#F4EFF4",
    val radius: String = "12px",
    val shadow: String = "none",
    val padding: String = "16px",
    val successBackground: String = "#1B6D2B",
    val successForeground: String = "#FFFFFF",
    val errorBackground: String = "#B3261E",
    val errorForeground: String = "#FFFFFF",
    val warningBackground: String = "#7D5800",
    val warningForeground: String = "#FFFFFF",
    val infoBackground: String = "#0061A4",
    val infoForeground: String = "#FFFFFF",
    val dismissible: Boolean = true,
)

@Immutable
data class BlobExplorerTokens(
    val background: String = "#FFFBFE",
    val foreground: String = "#1C1B1F",
    val border: String = "#CAC4D0",
    val radius: String = "12px",
    val padding: String = "16px",
    val toolbarBackground: String = "#F7F2FA",
    val toolbarBorder: String = "#CAC4D0",
    val sidebarBackground: String = "#F7F2FA",
    val sidebarWidth: String = "240px",
    val sidebarBorder: String = "#CAC4D0",
    val itemBackground: String = "transparent",
    val itemBackgroundHover: String = "#6750A41F",
    val itemBackgroundSelected: String = "#E8DEF8",
    val itemForeground: String = "#1C1B1F",
    val itemForegroundSecondary: String = "#49454F",
    val itemPadding: String = "12px",
    val itemRadius: String = "8px",
    val breadcrumbForeground: String = "#49454F",
    val breadcrumbForegroundActive: String = "#1C1B1F",
    val breadcrumbSeparator: String = "#79747E",
    val detailBackground: String = "#F7F2FA",
    val detailBorder: String = "#CAC4D0",
    val detailWidth: String = "320px",
    val emptyForeground: String = "#49454F",
    val emptyIconColor: String = "#CAC4D0",
)
