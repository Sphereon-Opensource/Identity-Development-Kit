/*
 * © 2025 Sphereon International B.V.
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

@file:OptIn(ExperimentalJsExport::class)
@file:JsExportCompat

package com.sphereon.did.persistence

import com.sphereon.did.manager.DidRole
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.ExperimentalJsExport
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Persistence model for DID records.
 *
 * This is the database entity representation. Use [toManagedDid] to convert
 * to the domain model for API responses.
 *
 * Plain data class for Obj-C/JS compatibility.
 *
 * @property id Unique record identifier (UUID)
 * @property did The DID string (e.g., "did:key:z6Mk...")
 * @property method The DID method (e.g., "key", "web", "jwk")
 * @property alias Optional human-readable alias
 * @property documentJson Serialized DID Document JSON (for did:web)
 * @property role Role of the DID (MANAGED or EXTERNAL)
 * @property deactivated Whether the DID has been deactivated
 * @property createdAt ISO 8601 creation timestamp
 * @property updatedAt ISO 8601 last update timestamp
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidRecord", exact = true)
@JsExportCompat
@Serializable
data class DidRecord(
    val id: String,
    val did: String,
    val method: String,
    val alias: String? = null,
    val documentJson: String? = null,
    val role: DidRole,
    val deactivated: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Persistence model for DID key mappings.
 *
 * Maps verification methods in a DID document to KMS keys.
 *
 * @property id Unique mapping identifier (UUID)
 * @property didRecordId Foreign key to DidRecord
 * @property verificationMethodId The verification method ID (fragment, e.g., "key-1")
 * @property kmsKeyAlias The key alias in KMS
 * @property kmsProviderId The KMS provider identifier
 * @property purposesJson JSON array of verification purposes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidKeyMappingRecord", exact = true)
@JsExportCompat
@Serializable
data class DidKeyMappingRecord(
    val id: String,
    val didRecordId: String,
    val verificationMethodId: String,
    val kmsKeyAlias: String,
    val kmsProviderId: String,
    val purposesJson: String
)

/**
 * Filter criteria for querying DID records.
 *
 * @property method Filter by DID method
 * @property alias Filter by alias (exact match)
 * @property role Filter by role
 * @property includeDeactivated Whether to include deactivated DIDs (default: false)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidRecordFilter", exact = true)
@JsExportCompat
@Serializable
data class DidRecordFilter(
    val method: String? = null,
    val alias: String? = null,
    val role: DidRole? = null,
    val includeDeactivated: Boolean = false
)
