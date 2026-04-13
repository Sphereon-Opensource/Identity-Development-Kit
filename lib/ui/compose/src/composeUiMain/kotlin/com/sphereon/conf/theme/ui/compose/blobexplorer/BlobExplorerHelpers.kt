package com.sphereon.conf.theme.ui.compose.blobexplorer

/**
 * Formats a byte size into a human-readable string (e.g. "1.5 MB").
 */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

/**
 * Returns a display icon character for a content type.
 */
fun contentTypeIcon(contentType: String?): String = when {
    contentType == null -> "\uD83D\uDCC4" // generic file
    contentType.startsWith("image/") -> "\uD83D\uDDBC" // image
    contentType.startsWith("video/") -> "\uD83C\uDFA5" // video
    contentType.startsWith("audio/") -> "\uD83C\uDFB5" // audio
    contentType == "application/pdf" -> "\uD83D\uDCC4" // PDF
    contentType.startsWith("text/") -> "\uD83D\uDCC4" // text
    contentType.contains("zip") || contentType.contains("compressed") -> "\uD83D\uDCE6" // archive
    else -> "\uD83D\uDCC4" // generic file
}

/**
 * Extracts the filename from a full blob path.
 */
fun extractFilename(path: String): String {
    val trimmed = path.trimEnd('/')
    return trimmed.substringAfterLast('/')
}

/**
 * Splits a prefix path into breadcrumb segments.
 * Each segment is a Pair of (label, fullPrefixUpToThisSegment).
 */
fun buildBreadcrumbs(prefix: String?): List<Pair<String, String>> {
    if (prefix.isNullOrBlank()) return emptyList()
    val parts = prefix.trimEnd('/').split('/')
    return parts.mapIndexed { index, part ->
        val fullPrefix = parts.take(index + 1).joinToString("/") + "/"
        part to fullPrefix
    }
}

private val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * Formats an ISO-8601 date string (e.g. "2026-03-17T10:30:00Z") into
 * a human-readable format like "Mar 17, 2026". Returns empty string if null.
 * Falls back to the first 10 characters (YYYY-MM-DD) if parsing fails.
 */
fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        // Parse YYYY-MM-DD from the beginning of the ISO string
        val datePart = iso.take(10)
        val parts = datePart.split('-')
        if (parts.size != 3) return datePart
        val year = parts[0]
        val monthIdx = parts[1].toIntOrNull()?.minus(1) ?: return datePart
        val day = parts[2].toIntOrNull() ?: return datePart
        if (monthIdx !in MONTH_NAMES.indices) return datePart
        "${MONTH_NAMES[monthIdx]} $day, $year"
    } catch (_: Exception) {
        iso.take(10)
    }
}
