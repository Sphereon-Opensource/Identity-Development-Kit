/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.catalog.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@JsExportCompat
@Serializable
enum class AttestationCatalogStatus {
    DRAFT,
    PUBLISHED,
    DISABLED,
}

@JsExportCompat
@Serializable
enum class CatalogSchemaProvenance {
    AUTHORED,
    LINKED_DESIGN,
    IMPORTED,
}

@JsExportCompat
@Serializable
enum class CatalogVerificationMode {
    DISCOVERY,
    TYPE_MUST_EXIST,
    TYPE_AND_TRUSTED_AUTHORITIES,
}

@JsExportCompat
@Serializable
enum class CatalogVerificationOutcome {
    ALLOW,
    DENY,
}

@JsExportCompat
@Serializable
data class PaginatedSchemaList
    @JvmOverloads
    constructor(
        val total: Int,
        val limit: Int,
        val offset: Int,
        val data: List<SchemaMeta>,
    )

@JsExportCompat
@Serializable
data class SignedSchemaListPayload
    @JvmOverloads
    constructor(
        val iss: String,
        val iat: Long,
        val data: PaginatedSchemaList,
    )

@JsExportCompat
@Serializable
data class SignedSchemaPayload
    @JvmOverloads
    constructor(
        val iss: String,
        val iat: Long,
        val data: SchemaMeta,
    )

@JsExportCompat
@Serializable
data class AttestationCatalog
    @JvmOverloads
    constructor(
        val id: String,
        val slug: String,
        val displayName: String,
        val description: String? = null,
        val status: AttestationCatalogStatus = AttestationCatalogStatus.DRAFT,
        val verificationEnabled: Boolean = false,
        val version: Long = 1,
        val createdAt: Instant? = null,
        val updatedAt: Instant? = null,
    )

@JsExportCompat
@Serializable
data class AttestationCatalogList
    @JvmOverloads
    constructor(
        val items: List<AttestationCatalog>,
    )

@JsExportCompat
@Serializable
data class CatalogDocument
    @JvmOverloads
    constructor(
        val mediaType: String,
        val bytes: ByteArray,
        val integrity: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CatalogDocument) return false
            return mediaType == other.mediaType &&
                bytes.contentEquals(other.bytes) &&
                integrity == other.integrity
        }

        override fun hashCode(): Int {
            var result = mediaType.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + (integrity?.hashCode() ?: 0)
            return result
        }
    }

@JsExportCompat
@Serializable
data class AttestationSchemaRecord
    @JvmOverloads
    constructor(
        val catalogId: String,
        val schema: SchemaMeta,
        val provenance: CatalogSchemaProvenance,
        val listing: CatalogListingWindow = CatalogListingWindow.ALWAYS,
        val linkedDesignId: String? = null,
        val linkedVctId: String? = null,
        val documents: List<AttestationSchemaDocument> = emptyList(),
        val createdAt: Instant? = null,
        val updatedAt: Instant? = null,
    )

@JsExportCompat
@Serializable
data class AttestationSchemaDocument
    @JvmOverloads
    constructor(
        val kind: CatalogDocumentKind,
        val formatIdentifier: String? = null,
        val mediaType: String,
        val bytes: ByteArray,
        val integrity: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AttestationSchemaDocument) return false
            return kind == other.kind &&
                formatIdentifier == other.formatIdentifier &&
                mediaType == other.mediaType &&
                bytes.contentEquals(other.bytes) &&
                integrity == other.integrity
        }

        override fun hashCode(): Int {
            var result = kind.hashCode()
            result = 31 * result + (formatIdentifier?.hashCode() ?: 0)
            result = 31 * result + mediaType.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + (integrity?.hashCode() ?: 0)
            return result
        }
    }

@JsExportCompat
@Serializable
enum class CatalogDocumentKind {
    RULEBOOK,
    FORMAT,
}

@JsExportCompat
@Serializable
data class CatalogVerificationDecision
    @JvmOverloads
    constructor(
        val outcome: CatalogVerificationOutcome,
        val mode: CatalogVerificationMode,
        val reasons: List<String> = emptyList(),
        val schema: SchemaMeta? = null,
        val selectedAuthorities: List<TrustAuthority> = emptyList(),
    )

@JsExportCompat
@Serializable
data class CatalogImportReport
    @JvmOverloads
    constructor(
        val catalogId: String,
        val imported: Int,
        val skipped: Int,
        val errors: List<String> = emptyList(),
        val schemaIds: List<String> = emptyList(),
    )
