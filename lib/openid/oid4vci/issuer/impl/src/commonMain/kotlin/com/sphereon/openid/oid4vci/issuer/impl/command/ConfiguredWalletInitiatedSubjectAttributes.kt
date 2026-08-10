/*
 * © 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.config.INSTANCES_NAMESPACE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the config-backed subject attribute source for a sessionless wallet-initiated flow.
 *
 * The registry is a single JSON value instead of dynamic property-path segments, so arbitrary
 * subject/configuration identifiers cannot escape into the configuration namespace. A malformed
 * configured registry fails issuance closed; an absent subject or configuration simply means
 * this source has no contribution.
 */
internal fun resolveConfiguredWalletInitiatedSubjectAttributes(
    propertyResolver: PropertyResolver?,
    subject: String,
    credentialConfigurationId: String,
    issuerInstanceId: String? = null,
): IdkResult<Map<String, JsonElement>, IdkError> {
    val property = walletInitiatedSubjectAttributesProperty(issuerInstanceId)
    val raw =
        propertyResolver
            ?.getPropertyAsString(property)
            ?.takeIf { it.isNotBlank() }
            ?: return Ok(emptyMap())
    val registry =
        runCatching { Json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: return Err(
                IdkError.fromString(
                    code = "invalid_credential_configuration",
                    message = "$property must be a JSON object",
                ),
            )
    val subjectEntry = registry[subject] ?: return Ok(emptyMap())
    val subjectObject =
        subjectEntry as? JsonObject
            ?: return Err(
                IdkError.fromString(
                    code = "invalid_credential_configuration",
                    message = "Configured wallet-initiated subject '$subject' must map to a JSON object",
                ),
            )
    val attributesEntry = subjectObject[credentialConfigurationId] ?: return Ok(emptyMap())
    val attributes =
        attributesEntry as? JsonObject
            ?: return Err(
                IdkError.fromString(
                    code = "invalid_credential_configuration",
                    message =
                        "Configured wallet-initiated subject '$subject' credential " +
                            "'$credentialConfigurationId' must map to a JSON object",
                ),
            )
    return Ok(attributes)
}

internal fun walletInitiatedSubjectAttributesProperty(issuerInstanceId: String?): String =
    issuerInstanceId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { "$INSTANCES_NAMESPACE.$it.walletInitiated.subjectAttributesJson" }
        ?: WALLET_INITIATED_SUBJECT_ATTRIBUTES_PROPERTY

internal const val WALLET_INITIATED_SUBJECT_ATTRIBUTES_PROPERTY =
    "oid4vci.issuer.walletInitiated.subjectAttributesJson"
