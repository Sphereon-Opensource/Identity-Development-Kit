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

package com.sphereon.did.persistence

import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidKeyMapping
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.models.DidDocument
import kotlinx.serialization.json.Json

private val json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/**
 * Converts a persistence record to the domain model.
 *
 * @param keyMappings The key mappings for this DID
 * @return The domain model
 */
fun DidRecord.toManagedDid(keyMappings: List<DidKeyMappingRecord> = emptyList()): ManagedDid {
    val document =
        documentJson?.let {
            try {
                json.decodeFromString<DidDocument>(it)
            } catch (_: Exception) {
                null
            }
        }

    return ManagedDid(
        id = id,
        did = did,
        method = method,
        alias = alias,
        document = document,
        role = role,
        deactivated = deactivated,
        keys = keyMappings.map { it.toDidKeyMapping() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

/**
 * Converts a domain model to a persistence record.
 */
fun ManagedDid.toDidRecord(): DidRecord {
    val documentJson =
        document?.let {
            json.encodeToString(DidDocument.serializer(), it)
        }

    return DidRecord(
        id = id,
        did = did,
        method = method,
        alias = alias,
        documentJson = documentJson,
        role = role,
        deactivated = deactivated,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

/**
 * Converts a persistence key mapping to the domain model.
 */
fun DidKeyMappingRecord.toDidKeyMapping(): DidKeyMapping =
    DidKeyMapping(
        id = id,
        verificationMethodId = verificationMethodId,
        kmsKeyAlias = kmsKeyAlias,
        kmsProviderId = kmsProviderId,
        purposesJson = purposesJson,
    )

/**
 * Converts a domain key mapping to a persistence record.
 *
 * @param didRecordId The ID of the parent DID record
 */
fun DidKeyMapping.toDidKeyMappingRecord(didRecordId: String): DidKeyMappingRecord =
    DidKeyMappingRecord(
        id = id,
        didRecordId = didRecordId,
        verificationMethodId = verificationMethodId,
        kmsKeyAlias = kmsKeyAlias,
        kmsProviderId = kmsProviderId,
        purposesJson = purposesJson,
    )

/**
 * Converts a domain filter to a persistence filter.
 */
fun DidFilter.toDidRecordFilter(): DidRecordFilter =
    DidRecordFilter(
        method = method,
        alias = alias,
        role = role,
        includeDeactivated = includeDeactivated,
    )
