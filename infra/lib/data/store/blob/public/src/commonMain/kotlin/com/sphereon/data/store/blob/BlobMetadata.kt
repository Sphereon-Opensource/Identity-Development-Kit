/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Hint for retention policy enforcement. IDK defines the model; EDK provides jurisdiction-aware enforcement.
 */
@Serializable
@JsExportCompat
data class RetentionHint(
    val retainUntil: Instant? = null,
    val legalHold: Boolean = false,
    val policy: String? = null,
)

/**
 * Metadata associated with a blob.
 *
 * Tier 1 (storage-native): contentType, contentEncoding, contentDisposition — stored by the backend.
 * Tier 2 (application): custom map, contentHash, retentionHint — indexed in BlobMetadataIndex.
 * Tier 3 (classification hints): consumer-supplied classification metadata carried as strings
 * so IDK stays independent of EDK classification enums. Authoritative values live on the
 * consumer domain row (for example, vault_document); these fields are hints surfaced to
 * audit, retention, and tenant-isolation policy at the storage layer.
 */
@Serializable
@JsExportCompat
data class BlobMetadata(
    val contentType: String? = null,
    val contentEncoding: String? = null,
    val contentDisposition: String? = null,
    @JsExportIgnoreCompat
    val custom: Map<String, String> = emptyMap(),
    val contentHash: String? = null,
    val retentionHint: RetentionHint? = null,
    /** Consumer-supplied sensitivity classification hint (e.g., "CONFIDENTIAL"). */
    val classification: String? = null,
    /** Consumer-supplied legal basis hint (e.g., "gdpr.art6.1a.consent"). */
    val legalBasis: String? = null,
    /** Consumer-supplied retention-days hint; authoritative value lives on the consumer row. */
    val retentionDays: Int? = null,
    /** Consumer-supplied processing-purpose hint (GDPR Art. 5(1)(b)). */
    val processingPurpose: String? = null,
    /** Consumer-supplied jurisdiction hint (e.g., "EU-NL"). */
    val jurisdiction: String? = null,
) {
    companion object {
        val EMPTY = BlobMetadata()

        fun ofContentType(contentType: String) = BlobMetadata(contentType = contentType)
    }
}
