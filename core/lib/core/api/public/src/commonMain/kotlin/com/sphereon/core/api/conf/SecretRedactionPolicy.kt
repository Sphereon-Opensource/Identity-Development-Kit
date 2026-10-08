/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/** Policy for redacting sensitive configuration values in logs and diagnostics. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretRedactionPolicy", exact = true)
interface SecretRedactionPolicy {
    fun shouldRedact(
        key: String,
        metadata: ResolutionMetadata,
    ): Boolean

    fun redact(value: String): String
}

/** Redacts explicit secrets and keys whose names have credential-like semantics. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultSecretRedactionPolicy", exact = true)
class DefaultSecretRedactionPolicy(
    private val redactedPlaceholder: String = "***REDACTED***",
    private val sensitiveKeyPatterns: List<Regex> =
        listOf(
            Regex(".*password.*", RegexOption.IGNORE_CASE),
            Regex(".*secret.*", RegexOption.IGNORE_CASE),
            Regex(".*token.*", RegexOption.IGNORE_CASE),
            Regex(".*key.*", RegexOption.IGNORE_CASE),
            Regex(".*credential.*", RegexOption.IGNORE_CASE),
            Regex(".*auth.*", RegexOption.IGNORE_CASE),
        ),
) : SecretRedactionPolicy {
    override fun shouldRedact(
        key: String,
        metadata: ResolutionMetadata,
    ): Boolean =
        metadata.isSecret ||
            metadata.provenance.hasTaint(ResolutionTaint.SENSITIVE) ||
            sensitiveKeyPatterns.any { it.matches(key) }

    override fun redact(value: String): String = redactedPlaceholder
}

internal fun SecretRedactionPolicy.isSensitiveKey(
    key: String,
    scope: ConfigLevel,
): Boolean =
    shouldRedact(
        key,
        ResolutionMetadata(
            source = "provenance-classification",
            scope = scope,
            originalKey = key,
            normalizedKey = key,
            order = 0,
            isSecret = false,
            isInterpolated = false,
            resolvedAt = Clock.System.now(),
            ttl = null,
            provenance = ResolutionProvenance.known(scope),
        ),
    )
