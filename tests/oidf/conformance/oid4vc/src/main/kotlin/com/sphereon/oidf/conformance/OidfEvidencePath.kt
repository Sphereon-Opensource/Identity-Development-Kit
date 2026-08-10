/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Produces a bounded, deterministic directory name for one discovered module variant.
 * The complete identity remains in result.json and the row coverage manifest.
 */
fun OidfPlanModule.evidenceDirectoryName(): String {
    val canonicalIdentity =
        buildString {
            append(testModule)
            variant.toSortedMap().forEach { (key, value) ->
                append('\n')
                append(key)
                append('=')
                append(value)
            }
        }
    val digest =
        MessageDigest
            .getInstance("SHA-256")
            .digest(canonicalIdentity.toByteArray(StandardCharsets.UTF_8))
            .take(HASH_BYTES)
            .joinToString("") { byte -> "%02x".format(byte) }
    val readablePrefix = testModule.evidenceFilePart().take(MAX_READABLE_PREFIX_LENGTH).ifBlank { "module" }
    return "${readablePrefix}__$digest"
}

private fun String.evidenceFilePart(): String =
    lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')

private const val MAX_READABLE_PREFIX_LENGTH = 72
private const val HASH_BYTES = 8
