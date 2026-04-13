package com.sphereon.conf.theme.ui.compose.blobexplorer

/**
 * Sidebar display mode.
 */
enum class SidebarMode {
    /** Expanded by default, user can collapse */
    Visible,
    /** Collapsed by default, user can expand */
    Collapsed,
    /** Sidebar is not shown at all */
    Hidden,
}

/**
 * Sidebar configuration.
 */
data class BlobExplorerSidebarConfig(
    val defaultMode: SidebarMode = SidebarMode.Visible,
    /** When true, sidebar auto-collapses when detail pane opens on medium screens */
    val autoCollapse: Boolean = true,
    /** If set, only these folder prefixes are shown in the tree */
    val visibleFolders: List<String>? = null,
)

/**
 * Configuration for the BlobExplorer component.
 */
data class BlobExplorerConfig(
    val readOnly: Boolean = false,
    val onViewBlob: ((BlobItem) -> Unit)? = null,
    val onEditBlob: ((BlobItem) -> Unit)? = null,
    val onDownloadBlob: ((BlobItem) -> Unit)? = null,
    val sidebar: BlobExplorerSidebarConfig = BlobExplorerSidebarConfig(),
    /**
     * Custom date formatter. Receives an ISO-8601 date string (e.g. "2026-03-17T10:30:00Z")
     * and should return a display string. Defaults to [formatIsoDate] which produces
     * English month abbreviations like "Mar 17, 2026".
     *
     * Override for locale-aware formatting, e.g. on JVM:
     * ```kotlin
     * formatDate = { iso ->
     *     LocalDate.parse(iso.take(10))
     *         .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
     * }
     * ```
     */
    val formatDate: (String?) -> String = ::formatIsoDate,
)
