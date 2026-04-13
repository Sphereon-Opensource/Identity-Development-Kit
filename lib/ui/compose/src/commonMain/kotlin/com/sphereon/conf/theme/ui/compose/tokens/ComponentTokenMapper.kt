package com.sphereon.conf.theme.ui.compose.tokens

import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Maps raw resolved token map to typed component token data classes.
 * Falls back to defaults when tokens are not present (e.g., IDK-only usage without EDK presets).
 */
object ComponentTokenMapper {

    fun toButtonTokens(tokens: Map<String, String>): ButtonTokens = ButtonTokens(
        primaryBackground = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_BACKGROUND] ?: ButtonTokens().primaryBackground,
        primaryForeground = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_FOREGROUND] ?: ButtonTokens().primaryForeground,
        primaryBorder = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_BORDER] ?: ButtonTokens().primaryBorder,
        primaryBorderWidth = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_BORDER_WIDTH] ?: ButtonTokens().primaryBorderWidth,
        primaryRadius = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_RADIUS] ?: ButtonTokens().primaryRadius,
        primaryShadow = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_SHADOW] ?: ButtonTokens().primaryShadow,
        primaryPaddingX = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_PADDING_X] ?: ButtonTokens().primaryPaddingX,
        primaryPaddingY = tokens[TokenKeyConstants.COMP_BUTTON_PRIMARY_PADDING_Y] ?: ButtonTokens().primaryPaddingY,
        secondaryBackground = tokens[TokenKeyConstants.COMP_BUTTON_SECONDARY_BACKGROUND] ?: ButtonTokens().secondaryBackground,
        secondaryForeground = tokens[TokenKeyConstants.COMP_BUTTON_SECONDARY_FOREGROUND] ?: ButtonTokens().secondaryForeground,
        secondaryBorder = tokens[TokenKeyConstants.COMP_BUTTON_SECONDARY_BORDER] ?: ButtonTokens().secondaryBorder,
        secondaryBorderWidth = tokens[TokenKeyConstants.COMP_BUTTON_SECONDARY_BORDER_WIDTH] ?: ButtonTokens().secondaryBorderWidth,
        secondaryRadius = tokens[TokenKeyConstants.COMP_BUTTON_SECONDARY_RADIUS] ?: ButtonTokens().secondaryRadius,
        ghostBackground = tokens[TokenKeyConstants.COMP_BUTTON_GHOST_BACKGROUND] ?: ButtonTokens().ghostBackground,
        ghostForeground = tokens[TokenKeyConstants.COMP_BUTTON_GHOST_FOREGROUND] ?: ButtonTokens().ghostForeground,
    )

    fun toInputTokens(tokens: Map<String, String>): InputTokens = InputTokens(
        background = tokens[TokenKeyConstants.COMP_INPUT_BACKGROUND] ?: InputTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_INPUT_FOREGROUND] ?: InputTokens().foreground,
        border = tokens[TokenKeyConstants.COMP_INPUT_BORDER] ?: InputTokens().border,
        borderFocus = tokens[TokenKeyConstants.COMP_INPUT_BORDER_FOCUS] ?: InputTokens().borderFocus,
        placeholder = tokens[TokenKeyConstants.COMP_INPUT_PLACEHOLDER] ?: InputTokens().placeholder,
        radius = tokens[TokenKeyConstants.COMP_INPUT_RADIUS] ?: InputTokens().radius,
        paddingX = tokens[TokenKeyConstants.COMP_INPUT_PADDING_X] ?: InputTokens().paddingX,
        paddingY = tokens[TokenKeyConstants.COMP_INPUT_PADDING_Y] ?: InputTokens().paddingY,
    )

    fun toCheckboxTokens(tokens: Map<String, String>): CheckboxTokens = CheckboxTokens(
        border = tokens[TokenKeyConstants.COMP_CHECKBOX_BORDER] ?: CheckboxTokens().border,
        borderWidth = tokens[TokenKeyConstants.COMP_CHECKBOX_BORDER_WIDTH] ?: CheckboxTokens().borderWidth,
        radius = tokens[TokenKeyConstants.COMP_CHECKBOX_RADIUS] ?: CheckboxTokens().radius,
        checkedBackground = tokens[TokenKeyConstants.COMP_CHECKBOX_CHECKED_BACKGROUND] ?: CheckboxTokens().checkedBackground,
        checkedForeground = tokens[TokenKeyConstants.COMP_CHECKBOX_CHECKED_FOREGROUND] ?: CheckboxTokens().checkedForeground,
        size = tokens[TokenKeyConstants.COMP_CHECKBOX_SIZE] ?: CheckboxTokens().size,
        labelForeground = tokens[TokenKeyConstants.COMP_CHECKBOX_LABEL_FOREGROUND] ?: CheckboxTokens().labelForeground,
        disabledBackground = tokens[TokenKeyConstants.COMP_CHECKBOX_DISABLED_BACKGROUND] ?: CheckboxTokens().disabledBackground,
    )

    fun toRadioTokens(tokens: Map<String, String>): RadioTokens = RadioTokens(
        border = tokens[TokenKeyConstants.COMP_RADIO_BORDER] ?: RadioTokens().border,
        borderWidth = tokens[TokenKeyConstants.COMP_RADIO_BORDER_WIDTH] ?: RadioTokens().borderWidth,
        selectedBorder = tokens[TokenKeyConstants.COMP_RADIO_SELECTED_BORDER] ?: RadioTokens().selectedBorder,
        selectedIndicator = tokens[TokenKeyConstants.COMP_RADIO_SELECTED_INDICATOR] ?: RadioTokens().selectedIndicator,
        size = tokens[TokenKeyConstants.COMP_RADIO_SIZE] ?: RadioTokens().size,
        labelForeground = tokens[TokenKeyConstants.COMP_RADIO_LABEL_FOREGROUND] ?: RadioTokens().labelForeground,
        disabledBorder = tokens[TokenKeyConstants.COMP_RADIO_DISABLED_BORDER] ?: RadioTokens().disabledBorder,
    )

    fun toCardTokens(tokens: Map<String, String>): CardTokens = CardTokens(
        background = tokens[TokenKeyConstants.COMP_CARD_BACKGROUND] ?: CardTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_CARD_FOREGROUND] ?: CardTokens().foreground,
        border = tokens[TokenKeyConstants.COMP_CARD_BORDER] ?: CardTokens().border,
        borderWidth = tokens[TokenKeyConstants.COMP_CARD_BORDER_WIDTH] ?: CardTokens().borderWidth,
        radius = tokens[TokenKeyConstants.COMP_CARD_RADIUS] ?: CardTokens().radius,
        shadow = tokens[TokenKeyConstants.COMP_CARD_SHADOW] ?: CardTokens().shadow,
        padding = tokens[TokenKeyConstants.COMP_CARD_PADDING] ?: CardTokens().padding,
    )

    fun toBadgeTokens(tokens: Map<String, String>): BadgeTokens = BadgeTokens(
        background = tokens[TokenKeyConstants.COMP_BADGE_BACKGROUND] ?: BadgeTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_BADGE_FOREGROUND] ?: BadgeTokens().foreground,
        border = tokens[TokenKeyConstants.COMP_BADGE_BORDER] ?: BadgeTokens().border,
        borderWidth = tokens[TokenKeyConstants.COMP_BADGE_BORDER_WIDTH] ?: BadgeTokens().borderWidth,
        radius = tokens[TokenKeyConstants.COMP_BADGE_RADIUS] ?: BadgeTokens().radius,
        paddingX = tokens[TokenKeyConstants.COMP_BADGE_PADDING_X] ?: BadgeTokens().paddingX,
        paddingY = tokens[TokenKeyConstants.COMP_BADGE_PADDING_Y] ?: BadgeTokens().paddingY,
        errorBackground = tokens[TokenKeyConstants.COMP_BADGE_ERROR_BACKGROUND] ?: BadgeTokens().errorBackground,
        errorForeground = tokens[TokenKeyConstants.COMP_BADGE_ERROR_FOREGROUND] ?: BadgeTokens().errorForeground,
    )

    fun toModalTokens(tokens: Map<String, String>): ModalTokens = ModalTokens(
        background = tokens[TokenKeyConstants.COMP_MODAL_BACKGROUND] ?: ModalTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_MODAL_FOREGROUND] ?: ModalTokens().foreground,
        border = tokens[TokenKeyConstants.COMP_MODAL_BORDER] ?: ModalTokens().border,
        shadow = tokens[TokenKeyConstants.COMP_MODAL_SHADOW] ?: ModalTokens().shadow,
        overlay = tokens[TokenKeyConstants.COMP_MODAL_OVERLAY] ?: ModalTokens().overlay,
        radius = tokens[TokenKeyConstants.COMP_MODAL_RADIUS] ?: ModalTokens().radius,
    )

    fun toTabTokens(tokens: Map<String, String>): TabTokens = TabTokens(
        background = tokens[TokenKeyConstants.COMP_TAB_BACKGROUND] ?: TabTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_TAB_FOREGROUND] ?: TabTokens().foreground,
        activeForeground = tokens[TokenKeyConstants.COMP_TAB_ACTIVE_FOREGROUND] ?: TabTokens().activeForeground,
        activeIndicator = tokens[TokenKeyConstants.COMP_TAB_ACTIVE_INDICATOR] ?: TabTokens().activeIndicator,
        border = tokens[TokenKeyConstants.COMP_TAB_BORDER] ?: TabTokens().border,
    )

    fun toSelectTokens(tokens: Map<String, String>): SelectTokens = SelectTokens(
        background = tokens[TokenKeyConstants.COMP_SELECT_BACKGROUND] ?: SelectTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_SELECT_FOREGROUND] ?: SelectTokens().foreground,
        border = tokens[TokenKeyConstants.COMP_SELECT_BORDER] ?: SelectTokens().border,
        borderFocus = tokens[TokenKeyConstants.COMP_SELECT_BORDER_FOCUS] ?: SelectTokens().borderFocus,
        radius = tokens[TokenKeyConstants.COMP_SELECT_RADIUS] ?: SelectTokens().radius,
        paddingX = tokens[TokenKeyConstants.COMP_SELECT_PADDING_X] ?: SelectTokens().paddingX,
        paddingY = tokens[TokenKeyConstants.COMP_SELECT_PADDING_Y] ?: SelectTokens().paddingY,
        menuBackground = tokens[TokenKeyConstants.COMP_SELECT_MENU_BACKGROUND] ?: SelectTokens().menuBackground,
        menuShadow = tokens[TokenKeyConstants.COMP_SELECT_MENU_SHADOW] ?: SelectTokens().menuShadow,
        menuRadius = tokens[TokenKeyConstants.COMP_SELECT_MENU_RADIUS] ?: SelectTokens().menuRadius,
        optionHover = tokens[TokenKeyConstants.COMP_SELECT_OPTION_HOVER] ?: SelectTokens().optionHover,
        placeholder = tokens[TokenKeyConstants.COMP_SELECT_PLACEHOLDER] ?: SelectTokens().placeholder,
    )

    fun toToastTokens(tokens: Map<String, String>): ToastTokens = ToastTokens(
        background = tokens[TokenKeyConstants.COMP_TOAST_BACKGROUND] ?: ToastTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_TOAST_FOREGROUND] ?: ToastTokens().foreground,
        radius = tokens[TokenKeyConstants.COMP_TOAST_RADIUS] ?: ToastTokens().radius,
        shadow = tokens[TokenKeyConstants.COMP_TOAST_SHADOW] ?: ToastTokens().shadow,
        padding = tokens[TokenKeyConstants.COMP_TOAST_PADDING] ?: ToastTokens().padding,
        successBackground = tokens[TokenKeyConstants.COMP_TOAST_SUCCESS_BACKGROUND] ?: ToastTokens().successBackground,
        successForeground = tokens[TokenKeyConstants.COMP_TOAST_SUCCESS_FOREGROUND] ?: ToastTokens().successForeground,
        errorBackground = tokens[TokenKeyConstants.COMP_TOAST_ERROR_BACKGROUND] ?: ToastTokens().errorBackground,
        errorForeground = tokens[TokenKeyConstants.COMP_TOAST_ERROR_FOREGROUND] ?: ToastTokens().errorForeground,
        warningBackground = tokens[TokenKeyConstants.COMP_TOAST_WARNING_BACKGROUND] ?: ToastTokens().warningBackground,
        warningForeground = tokens[TokenKeyConstants.COMP_TOAST_WARNING_FOREGROUND] ?: ToastTokens().warningForeground,
        infoBackground = tokens[TokenKeyConstants.COMP_TOAST_INFO_BACKGROUND] ?: ToastTokens().infoBackground,
        infoForeground = tokens[TokenKeyConstants.COMP_TOAST_INFO_FOREGROUND] ?: ToastTokens().infoForeground,
        dismissible = tokens[TokenKeyConstants.COMP_TOAST_DISMISSIBLE]?.toBooleanStrictOrNull() ?: ToastTokens().dismissible,
    )

    fun toBlobExplorerTokens(tokens: Map<String, String>): BlobExplorerTokens = BlobExplorerTokens(
        background = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_BACKGROUND] ?: BlobExplorerTokens().background,
        foreground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_FOREGROUND] ?: BlobExplorerTokens().foreground,
        border = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_BORDER] ?: BlobExplorerTokens().border,
        radius = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_RADIUS] ?: BlobExplorerTokens().radius,
        padding = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_PADDING] ?: BlobExplorerTokens().padding,
        toolbarBackground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_TOOLBAR_BACKGROUND] ?: BlobExplorerTokens().toolbarBackground,
        toolbarBorder = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_TOOLBAR_BORDER] ?: BlobExplorerTokens().toolbarBorder,
        sidebarBackground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_SIDEBAR_BACKGROUND] ?: BlobExplorerTokens().sidebarBackground,
        sidebarWidth = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_SIDEBAR_WIDTH] ?: BlobExplorerTokens().sidebarWidth,
        sidebarBorder = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_SIDEBAR_BORDER] ?: BlobExplorerTokens().sidebarBorder,
        itemBackground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_BACKGROUND] ?: BlobExplorerTokens().itemBackground,
        itemBackgroundHover = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_BACKGROUND_HOVER] ?: BlobExplorerTokens().itemBackgroundHover,
        itemBackgroundSelected = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_BACKGROUND_SELECTED] ?: BlobExplorerTokens().itemBackgroundSelected,
        itemForeground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_FOREGROUND] ?: BlobExplorerTokens().itemForeground,
        itemForegroundSecondary = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_FOREGROUND_SECONDARY] ?: BlobExplorerTokens().itemForegroundSecondary,
        itemPadding = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_PADDING] ?: BlobExplorerTokens().itemPadding,
        itemRadius = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_ITEM_RADIUS] ?: BlobExplorerTokens().itemRadius,
        breadcrumbForeground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_BREADCRUMB_FOREGROUND] ?: BlobExplorerTokens().breadcrumbForeground,
        breadcrumbForegroundActive = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_BREADCRUMB_FOREGROUND_ACTIVE] ?: BlobExplorerTokens().breadcrumbForegroundActive,
        breadcrumbSeparator = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_BREADCRUMB_SEPARATOR] ?: BlobExplorerTokens().breadcrumbSeparator,
        detailBackground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_DETAIL_BACKGROUND] ?: BlobExplorerTokens().detailBackground,
        detailBorder = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_DETAIL_BORDER] ?: BlobExplorerTokens().detailBorder,
        detailWidth = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_DETAIL_WIDTH] ?: BlobExplorerTokens().detailWidth,
        emptyForeground = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_EMPTY_FOREGROUND] ?: BlobExplorerTokens().emptyForeground,
        emptyIconColor = tokens[TokenKeyConstants.COMP_BLOB_EXPLORER_EMPTY_ICON_COLOR] ?: BlobExplorerTokens().emptyIconColor,
    )
}
