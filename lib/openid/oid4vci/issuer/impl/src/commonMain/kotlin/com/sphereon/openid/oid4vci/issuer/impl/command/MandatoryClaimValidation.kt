/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Final, format-independent mandatory-claim gate. The lifecycle pipeline normally evaluates
 * completeness earlier, but issuance must still fail closed when that optional integration is
 * absent or unavailable. Both dotted claim paths and their JSON-pointer-like `/` aliases are
 * accepted because the issuer's mdoc mapper supports both forms.
 */
internal fun missingMandatoryClaimPaths(
    mandatoryClaims: Set<String>,
    attributes: Map<String, JsonElement>,
): List<String> = mandatoryClaims
    .asSequence()
    .filter { path ->
        val value = attributes[path] ?: attributes["/$path"]
        value == null || value.isMissingMandatoryValue()
    }
    .sorted()
    .toList()

private fun JsonElement.isMissingMandatoryValue(): Boolean = when (this) {
    JsonNull -> true
    is JsonArray -> isEmpty()
    is JsonObject -> isEmpty()
    is JsonPrimitive -> isString && content.trim().let { it.isEmpty() || it == "null" || it == "[]" || it == "{}" }
}
