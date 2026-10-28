/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.crypto.key.persistence

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.conf.DEFAULT_APPLICATION_TENANT_ID
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Immutable publication of an existing tenant key-reference under a stable logical KMS resource ID.
 * The physical key-reference UUID and certificate linkage are never rewritten by this mapping.
 * `origin` is deliberately absent: stores derive and require [Origin.MANAGED] from the target row.
 */
data class ManagedKeyReferencePublication(
    val tenantId: String,
    val logicalResourceId: String,
    val resourceHandle: String,
    val logicalAlias: String,
    val providerId: String,
    val keyReferenceId: String,
    /** RFC 7638 SHA-256 JWK thumbprint, base64url without padding. */
    val publicKeyThumbprint: String,
) {
    init {
        require(tenantId.isNotBlank() && tenantId != DEFAULT_APPLICATION_TENANT_ID && tenantId != "__platform__")
        require(logicalResourceId.isNotBlank() && logicalResourceId.length <= 255)
        require(resourceHandle.isNotBlank() && resourceHandle.length <= 255)
        require(logicalAlias.isNotBlank() && logicalAlias.length <= 255)
        require(providerId.isNotBlank() && providerId.length <= 255)
        require(keyReferenceId.isNotBlank() && keyReferenceId.length <= 36)
        require(publicKeyThumbprint.matches(Regex("^[A-Za-z0-9_-]{43}$")))
    }
}

/** Outcome of an immutable publication bind. A conflict never changes the stored publication. */
sealed interface ManagedKeyReferencePublicationBindResult {
    data class Created(val publication: ManagedKeyReferencePublication) : ManagedKeyReferencePublicationBindResult

    data class IdenticalExisting(val publication: ManagedKeyReferencePublication) : ManagedKeyReferencePublicationBindResult

    data object Conflict : ManagedKeyReferencePublicationBindResult
}

object ManagedKeyReferencePublicationErrorCodes {
    const val UNSUPPORTED = "KMS_MANAGED_KEY_PUBLICATION_UNSUPPORTED"
    const val INVALID_TARGET = "KMS_MANAGED_KEY_PUBLICATION_INVALID_TARGET"
    const val INTEGRITY_FAILURE = "KMS_MANAGED_KEY_PUBLICATION_INTEGRITY_FAILURE"
}

internal fun <T> unsupportedManagedKeyPublicationResult(): IdkResult<T, IdkError> =
    Err(
        IdkError.fromString(
            code = ManagedKeyReferencePublicationErrorCodes.UNSUPPORTED,
            message = "This key-reference store does not support managed publication mappings",
        ),
    )

/** Returns the RFC 7638 thumbprint of a stored public JWK, refusing private JWK members. */
fun managedKeyReferencePublicJwkThumbprint(serializedJwk: String?): String? =
    serializedJwk?.let { serialized ->
        runCatching {
            val jwkObject = Json.parseToJsonElement(serialized).jsonObject
            require(jwkObject.keys.none { it in PRIVATE_JWK_MEMBERS })
            generateJwkThumbprint(Jwk.fromJsonObject(jwkObject).toPublicKey())
        }.getOrNull()
    }

/** Check that this active managed row is exactly the physical identity recorded by [publication]. */
fun KeyReferenceRecord.matchesManagedPublicationTarget(
    publication: ManagedKeyReferencePublication,
): Boolean =
    tenantId == publication.tenantId &&
        id == publication.keyReferenceId &&
        alias == publication.logicalAlias &&
        providerId == publication.providerId &&
        origin == Origin.MANAGED &&
        deletedAt == null &&
        managedKeyReferencePublicJwkThumbprint(publicKeyJwk) == publication.publicKeyThumbprint

private val PRIVATE_JWK_MEMBERS = setOf("d", "p", "q", "dp", "dq", "qi", "k", "oth")
