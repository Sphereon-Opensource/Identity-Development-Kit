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

@file:OptIn(ExperimentalJsExport::class)
@file:JsExportCompat

package com.sphereon.did.manager

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.models.VerificationMethodType
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.ExperimentalJsExport
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * Role of a DID in relation to this system.
 *
 * Aligns with the identifier resolution naming convention (ManagedIdentifier vs ExternalIdentifier).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidRole", exact = true)
@JsExportCompat
@Serializable
enum class DidRole {
    /**
     * The DID and document lifecycle are managed by this system. Individual verification
     * methods may be KMS-bound or may carry public-only material; signing requires a KMS
     * binding for the selected verification method.
     */
    MANAGED,

    /**
     * DID is external (received from another party).
     * Only the DID document is stored for resolution purposes.
     * No local key access - cannot sign with these DIDs.
     */
    EXTERNAL,
}

/**
 * Options for creating a new DID.
 *
 * @property method The DID method to use (e.g., "key", "web", "jwk")
 * @property alias Optional human-readable alias for the DID
 * @property domain Domain for did:web (e.g., "example.com")
 * @property path Path segments for did:web (e.g., ["user", "alice"])
 * @property publicKeyJwk Public key JWK for did:key, did:jwk, or initial did:web key
 * @property verificationMethodId Custom ID for the verification method (e.g., "key-1")
 * @property verificationMethodType Type of verification method (defaults to JsonWebKey2020)
 * @property controller Legacy single controller DID (defaults to the DID being created)
 * @property controllers Optional document controller list for DID Core multi-controller documents
 * @property purposes Verification purposes for the key
 * @property verificationMethods Configuration for additional verification methods
 * @property services Services to include in the DID document
 * @property methodOptions Method-specific options as key-value pairs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidCreateOptions", exact = true)
@JsExportCompat
@Serializable
data class DidCreateOptions
    @JvmOverloads
    constructor(
        val method: String,
        val alias: String? = null,
        // did:web specific
        val domain: String? = null,
        val path: List<String>? = null,
        // Key material
        val publicKeyJwk: com.sphereon.crypto.core.jose.Jwk? = null,
        val verificationMethodId: String? = null,
        val verificationMethodType: VerificationMethodType? = null,
        val controller: String? = null,
        val controllers: List<String> = emptyList(),
        val purposes: List<com.sphereon.did.models.VerificationPurpose>? = null,
        // Additional configuration
        val verificationMethods: List<VerificationMethodConfig> = emptyList(),
        val services: List<DidService>? = null,
        @JsExportIgnoreCompat
        val methodOptions: Map<String, String> = emptyMap(),
    )

/**
 * Options for updating an existing DID.
 *
 * Adding verification methods is intentionally *not* expressible here: keys must be added
 * via [DidManager.addVerificationMethod] so callers supply the KMS provider/alias triple
 * required for persistence.
 *
 * @property currentDocument The current DID document (required for did:web)
 * @property removeVerificationMethodIds IDs of verification methods to remove
 * @property addServices Services to add
 * @property removeServiceIds IDs of services to remove
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidUpdateOptions", exact = true)
@JsExportCompat
@Serializable
data class DidUpdateOptions
    @JvmOverloads
    constructor(
        val currentDocument: DidDocument? = null,
        /**
         * Updated DID alias.
         *
         * Internal convention: `""` clears the alias, `null` means "leave unchanged".
         *
         * The wire OpenAPI `DidUpdateRequest` uses JSON Merge Patch semantics where an
         * explicit `null` clears and an omitted field is unchanged. The REST adapter
         * translates wire `null` → internal `""` and wire-absent → internal `null` before
         * reaching the manager.
         */
        val alias: String? = null,
        /**
         * Updated canonical ID. Same internal / wire-translation semantics as [alias].
         */
        val canonicalId: String? = null,
        val removeVerificationMethodIds: List<String>? = null,
        val addServices: List<DidService>? = null,
        val removeServiceIds: List<String>? = null,
    )
// NOTE: a `controller: String?` field used to live here. It was removed in IDK-21 review
// (VDX-infra-agt) because its semantics conflicted with AddControllerServiceCommand:
// update() replaced the entire controllers list with a single-element list, while
// AddController/RemoveController treat controllers as an additive set. Manage the
// controllers list exclusively through Add/Remove controller endpoints; for one-shot
// replacement use ReplaceDid.

/**
 * Options for deactivating a DID.
 *
 * @property reason Optional reason for deactivation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDeactivateOptions", exact = true)
@JsExportCompat
@Serializable
data class DidDeactivateOptions
    @JvmOverloads
    constructor(
        val reason: String? = null,
    )

/**
 * Options for adding a key to a DID.
 *
 * @property currentDocument The current DID document (required for did:web)
 * @property publicKeyJwk The public key JWK to add
 * @property verificationMethodId Custom ID for the verification method
 * @property verificationMethodType Type of verification method
 * @property controller Controller DID for the key
 * @property purposes Verification purposes for the key
 * @property config Alternative: use VerificationMethodConfig
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AddKeyOptions", exact = true)
@JsExportCompat
@Serializable
data class AddKeyOptions
    @JvmOverloads
    constructor(
        val currentDocument: DidDocument? = null,
        val publicKeyJwk: com.sphereon.crypto.core.jose.Jwk? = null,
        val verificationMethodId: String? = null,
        val verificationMethodType: VerificationMethodType? = null,
        val controller: String? = null,
        val purposes: List<com.sphereon.did.models.VerificationPurpose>? = null,
        val config: VerificationMethodConfig? = null,
    )

/**
 * Result of creating a DID.
 *
 * @property did The created DID
 * @property didDocument The DID Document
 * @property verificationMethodsByPurpose Verification methods indexed by purpose
 * @property alias The alias if set
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidCreateResult", exact = true)
@JsExportCompat
@Serializable
data class DidCreateResult
    @JvmOverloads
    constructor(
        val did: String,
        val didDocument: DidDocument,
        @JsExportIgnoreCompat
        val verificationMethodsByPurpose: Map<com.sphereon.did.models.VerificationPurpose, List<com.sphereon.did.models.VerificationMethod>> = emptyMap(),
        val alias: String? = null,
    )

/**
 * Result of updating a DID.
 *
 * @property did The DID that was updated
 * @property didDocument The updated DID Document
 * @property verificationMethodsByPurpose Verification methods indexed by purpose
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidUpdateResult", exact = true)
@JsExportCompat
@Serializable
data class DidUpdateResult
    @JvmOverloads
    constructor(
        val did: String,
        val didDocument: DidDocument,
        @JsExportIgnoreCompat
        val verificationMethodsByPurpose: Map<com.sphereon.did.models.VerificationPurpose, List<com.sphereon.did.models.VerificationMethod>> = emptyMap(),
    )

/**
 * Result of deactivating a DID.
 *
 * @property did The DID that was deactivated
 * @property deactivated Whether deactivation was successful
 * @property deactivatedDocument The deactivated DID document (for did:web publishing)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDeactivateResult", exact = true)
@JsExportCompat
@Serializable
data class DidDeactivateResult
    @JvmOverloads
    constructor(
        val did: String,
        val deactivated: Boolean = true,
        val deactivatedDocument: DidDocument? = null,
    )

/**
 * Sort field for [DidFilter]. Wire form is camelCase (`createdAt`, `updatedAt`, `did`,
 * `method`, `alias`) per the OpenAPI `listDids.sort` parameter.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidSortField", exact = true)
@JsExportCompat
@Serializable
enum class DidSortField {
    @kotlinx.serialization.SerialName("createdAt")
    CREATED_AT,

    @kotlinx.serialization.SerialName("updatedAt")
    UPDATED_AT,

    @kotlinx.serialization.SerialName("did")
    DID,

    @kotlinx.serialization.SerialName("method")
    METHOD,

    @kotlinx.serialization.SerialName("alias")
    ALIAS,
}

/**
 * Sort direction for [DidFilter]. ASC = ascending, DESC = descending. Matches the OpenAPI
 * `listDids.sortDirection` parameter.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SortDirection", exact = true)
@JsExportCompat
@Serializable
enum class SortDirection {
    ASC,
    DESC,
}

/**
 * Filter for listing DIDs.
 *
 * @property method Filter by DID method.
 * @property alias Filter by alias (exact match).
 * @property role Filter by role (MANAGED or EXTERNAL).
 * @property search Case-insensitive substring match against the DID string and alias. `null`
 *                  disables the search filter.
 * @property includeDeactivated Include DIDs whose `deactivated` flag is set.
 * @property includeDeleted Include soft-deleted DIDs (rows with a non-null `deletedAt`).
 * @property page Zero-based page index; combined with [size] to slice the result set. Ignored
 *                when [size] is null.
 * @property size Page size cap. `null` means "no pagination" — return every matching
 *                aggregate. When set, the manager pushes LIMIT/OFFSET down to the repository.
 *                Defaults to [DEFAULT_PAGE_SIZE].
 * @property sort Column to order results by. Defaults to [DidSortField.CREATED_AT].
 * @property sortDirection Ascending or descending. Defaults to [SortDirection.DESC] (newest
 *                         first).
 * @property expand Comma-separated `?expand=` query value (e.g. `document,keys` or `all`).
 *                  Parsed by the service-command impl into a typed [DidExpand] set; null/blank
 *                  yields the default lightweight projection. Kept as a raw `String?` so the
 *                  binary-transport codec can decode it directly from a query parameter.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidFilter", exact = true)
@JsExportCompat
@Serializable
data class DidFilter
    @JvmOverloads
    constructor(
        val method: String? = null,
        val alias: String? = null,
        val role: DidRole? = null,
        val search: String? = null,
        val includeDeactivated: Boolean = false,
        val includeDeleted: Boolean = false,
        /** Zero-based page index. Combined with [size] to slice the result set; ignored when [size] is null. */
        val page: Int = 0,
        /**
         * Page size cap. `null` means "no pagination" — return every matching aggregate. When set, the
         * manager pushes LIMIT/OFFSET down to the repository. Defaults to [DEFAULT_PAGE_SIZE].
         */
        val size: Int? = DEFAULT_PAGE_SIZE,
        val sort: DidSortField = DidSortField.CREATED_AT,
        val sortDirection: SortDirection = SortDirection.DESC,
        val expand: String? = null,
    ) {
        companion object {
            const val DEFAULT_PAGE_SIZE: Int = 100
        }
    }

/**
 * Optional projections requested via the wire `?expand=` query parameter. Used by the
 * `ListDidsServiceCommand` and `GetDidServiceCommand` to decide whether the resolved
 * DID document and KMS key mappings should be inlined on the returned wire-shape Did.
 *
 * Wire-string keys are case-insensitive and exposed as `[document, keys, all]` in the
 * OpenAPI spec. `all` is shorthand parsed to the full member set.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidExpand", exact = true)
@JsExportCompat
@Serializable
enum class DidExpand {
    DOCUMENT,
    KEYS,
}

/**
 * Simplified capability summary for a DID method — boolean flags suitable for UI/decision logic.
 * Derived from [com.sphereon.did.capabilities.DidMethodCapabilities].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MethodCapabilitySummary", exact = true)
@JsExportCompat
@Serializable
data class MethodCapabilitySummary
    @JvmOverloads
    constructor(
        val method: String,
        val canCreate: Boolean,
        val canUpdate: Boolean,
        val canDeactivate: Boolean,
        val canDelete: Boolean,
        val isImmutable: Boolean,
        val isMutable: Boolean,
        val supportsKeyManagement: Boolean,
        val supportsServiceManagement: Boolean,
        val allowsCaching: Boolean,
    )

/**
 * Wire form of a verification relationship belonging to a DID document.
 *
 * Exactly one of [embeddedVerificationMethodId] (relationship contains an inline VM) or
 * [referencedVerificationMethodId] (relationship is a `did:…#fragment` reference to an
 * existing VM) is non-null.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerificationRelationship", exact = true)
@JsExportCompat
@Serializable
data class VerificationRelationship
    @JvmOverloads
    constructor(
        val id: String,
        val purpose: String,
        val embeddedVerificationMethodId: String? = null,
        val referencedVerificationMethodId: String? = null,
        val ordinal: Int = 0,
    )

/**
 * Represents a managed DID with its associated data.
 *
 * @property id Record identifier
 * @property did The DID
 * @property method The DID method
 * @property alias Human-readable alias
 * @property document The DID Document (may be null for external DIDs)
 * @property role Role of the DID (MANAGED or EXTERNAL)
 * @property deactivated Whether the DID has been deactivated
 * @property keys Key mappings to KMS
 * @property createdAt ISO 8601 creation timestamp
 * @property updatedAt ISO 8601 last update timestamp
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedDid", exact = true)
@JsExportCompat
@Serializable
data class ManagedDid
    @JvmOverloads
    constructor(
        val id: String,
        val did: String,
        val method: String,
        val alias: String? = null,
        val document: DidDocument? = null,
        val role: DidRole,
        val deactivated: Boolean = false,
        val keys: List<DidKeyMapping> = emptyList(),
        val createdAt: String,
        val updatedAt: String,
    )

/**
 * Maps a verification method to a KMS key.
 *
 * @property id Mapping identifier
 * @property verificationMethodId The verification method ID (fragment)
 * @property kmsKeyAlias The key alias in KMS
 * @property kmsProviderId The KMS provider ID
 * @property purposesJson JSON-encoded list of verification purposes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidKeyMapping", exact = true)
@JsExportCompat
@Serializable
data class DidKeyMapping(
    val id: String,
    val verificationMethodId: String,
    val kmsKeyAlias: String,
    val kmsProviderId: String,
    // JSON array for Obj-C/JS compat
    val purposesJson: String,
)
