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
     * DID is managed by this system (created and controlled locally).
     * Keys are stored in the local KMS with signing capability.
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
 * @property controller Controller DID (defaults to the DID being created)
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
 * @property currentDocument The current DID document (required for did:web)
 * @property controller New controller DID
 * @property addVerificationMethods Verification methods to add (full objects)
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
        val controller: String? = null,
        val addVerificationMethods: List<com.sphereon.did.models.VerificationMethod>? = null,
        val removeVerificationMethodIds: List<String>? = null,
        val addServices: List<DidService>? = null,
        val removeServiceIds: List<String>? = null,
    )

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
 * Filter for listing DIDs.
 *
 * @property method Filter by DID method
 * @property alias Filter by alias (exact match)
 * @property role Filter by role (CREATED or RECEIVED)
 * @property includeDeactivated Whether to include deactivated DIDs
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
        val includeDeactivated: Boolean = false,
    )

/**
 * Represents a managed DID with its associated data.
 *
 * @property id Record identifier
 * @property did The DID
 * @property method The DID method
 * @property alias Human-readable alias
 * @property document The DID Document (may be null for external DIDs)
 * @property role Role of the DID (CREATED or RECEIVED)
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
