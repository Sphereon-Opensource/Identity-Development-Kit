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
 *
 */

package com.sphereon.crypto.key.persistence

import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Persistence record for a key reference. Contains metadata only — no key material.
 *
 * @property id UUID primary key
 * @property tenantId Tenant this reference belongs to (tenant isolation)
 * @property alias Key alias within the KMS provider
 * @property kid Key identifier (provider-specific, e.g. AWS key UUID, Azure name/version)
 * @property providerId ID of the KMS provider that owns this key
 * @property origin Whether this key was managed natively or discovered from an external source
 */
@Serializable
data class KeyReferenceRecord(
    val id: String,
    val tenantId: String,
    val alias: String,
    val kid: String?,
    val providerId: String,
    val origin: Origin,
    val keyType: KeyTypeMapping? = null,
    val signatureAlgorithm: SignatureAlgorithm? = null,
    val keyVisibility: KeyVisibility? = null,
    val keyEncoding: KeyEncoding? = null,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
    val deletedAt: Instant? = null,
    val deletedById: String? = null,
) {
    companion object {
        /** Create a [KeyReferenceRecord] from a [ManagedKeyInfoType] after key generation or storage. */
        fun fromManagedKey(
            key: ManagedKeyInfoType<*>,
            tenantId: String,
            origin: Origin = Origin.MANAGED,
            principalId: String? = null,
        ): KeyReferenceRecord {
            val now = Clock.System.now()
            return KeyReferenceRecord(
                id =
                    kotlin.uuid.Uuid
                        .random()
                        .toString(),
                tenantId = tenantId,
                alias = key.alias,
                kid = key.kid,
                providerId = key.providerId,
                origin = origin,
                keyType = key.keyType,
                signatureAlgorithm = key.signatureAlgorithm,
                keyVisibility = key.keyVisibility,
                keyEncoding = key.keyEncoding,
                createdAt = now,
                createdById = principalId,
                updatedAt = now,
                updatedById = principalId,
            )
        }
    }
}

/**
 * Converts a [KeyReferenceRecord] to a [ManagedKeyReference].
 */
fun KeyReferenceRecord.toKeyReference(): ManagedKeyReference =
    ManagedKeyReference(
        alias = alias,
        kid = kid,
        providerId = providerId,
        origin = origin,
        signatureAlgorithm = signatureAlgorithm,
        keyType = keyType,
        keyVisibility = keyVisibility,
        keyEncoding = keyEncoding,
    )
