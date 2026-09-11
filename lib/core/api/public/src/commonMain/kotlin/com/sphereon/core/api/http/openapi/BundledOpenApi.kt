/* Copyright 2026 Sphereon International B.V. */
package com.sphereon.core.api.http.openapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** The stable, build-time manifest entry for one bundled OpenAPI document. */
@Serializable
data class BundledOpenApiSpecEntry(
    val division: String,
    val domain: String,
    val file: String,
    val title: String = "",
    val version: String = "",
    /** Normalized full paths (server path plus each `paths` key). */
    val paths: List<String> = emptyList(),
) {
    init {
        // The release bundler emits an empty division when no manifest overrides
        // the source default; preserve that valid legacy index shape.
        require(domain.isNotBlank()) { "domain must not be blank" }
        require(file.matches(BUNDLED_OPENAPI_FILE)) { "file must be an allowlisted YAML filename" }
        require(paths.all { it.startsWith("/") && !it.startsWith("//") }) {
            "bundled OpenAPI paths must be absolute"
        }
    }

    /** Stable request identifier; callers never need to provide the bundled filename. */
    @Transient
    val specId: String = file.substringBeforeLast('.')

    companion object {
        val BUNDLED_OPENAPI_FILE: Regex = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*\\.ya?ml$")
        val SPEC_ID: Regex = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    }
}

/** Same normalization used by the service build's bundled-index generator. */
fun normalizeBundledOpenApiPath(path: String): String {
    var normalized = path.replace(Regex("\\{[^}]*}"), "{}")
    if (!normalized.startsWith("/")) normalized = "/$normalized"
    normalized = normalized.replace(Regex("/+"), "/")
    if (normalized.length > 1) normalized = normalized.trimEnd('/')
    return normalized
}

/** Shared path-overlap semantics for generic bundled index consumers. */
fun bundledOpenApiPathsOverlap(specPath: String, mountedPath: String): Boolean {
    val spec = normalizeBundledOpenApiPath(specPath)
    val mounted = normalizeBundledOpenApiPath(mountedPath)
    return spec == mounted ||
        mounted.startsWith("$spec/") ||
        spec.startsWith("$mounted/") ||
        mounted.endsWith(spec) ||
        spec.endsWith(mounted)
}

fun isUniversalBundledOpenApiPath(path: String): Boolean {
    val normalized = normalizeBundledOpenApiPath(path)
    return normalized == "/health" || normalized == "/ready" || normalized == "/metrics" ||
        normalized.startsWith("/.well-known")
}

/**
 * Selects the representative bundled specs for a concrete mounted route set. A majority of
 * distinctive paths must be mounted; richer/product specs suppress redundant base specs.
 */
fun selectBundledOpenApiSpecs(
    all: List<BundledOpenApiSpecEntry>,
    mounted: List<String>,
): List<BundledOpenApiSpecEntry> {
    val normalizedMounted = mounted.map(::normalizeBundledOpenApiPath).distinct()
    val candidates = all.mapNotNull { entry ->
        val distinctive = entry.paths.filterNot(::isUniversalBundledOpenApiPath)
        if (distinctive.isEmpty()) return@mapNotNull null
        val covered = normalizedMounted.filter { route -> distinctive.any { bundledOpenApiPathsOverlap(it, route) } }.toSet()
        val matched = distinctive.count { path -> covered.any { bundledOpenApiPathsOverlap(path, it) } }
        if (matched > 0 && matched * 2 >= distinctive.size) entry to covered else null
    }
    val divisionOrder = mapOf("idk" to 0, "edk" to 1, "vdx" to 2)
    fun outranks(top: Pair<BundledOpenApiSpecEntry, Set<String>>, low: Pair<BundledOpenApiSpecEntry, Set<String>>): Boolean {
        if (top.second.size != low.second.size) return top.second.size > low.second.size
        val topDivision = divisionOrder[top.first.division] ?: 9
        val lowDivision = divisionOrder[low.first.division] ?: 9
        if (topDivision != lowDivision) return topDivision > lowDivision
        return "${top.first.title}/${top.first.file}" > "${low.first.title}/${low.first.file}"
    }
    return candidates
        .filter { low -> candidates.none { top -> top !== low && top.second.containsAll(low.second) && outranks(top, low) } }
        .map { it.first }
        .sortedWith(compareBy({ divisionOrder[it.division] ?: 9 }, { it.title }, { it.file }))
}
