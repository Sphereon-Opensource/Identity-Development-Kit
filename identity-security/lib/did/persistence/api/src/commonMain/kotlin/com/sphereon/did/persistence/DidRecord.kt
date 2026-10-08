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

package com.sphereon.did.persistence

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.DidSortField
import com.sphereon.did.manager.SortDirection
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.ExperimentalJsExport
import kotlin.native.ObjCName
import kotlin.time.Instant

/**
 * Aggregate root — one row per DID.
 *
 * The DID document itself is *not* stored as a blob on this row. For MANAGED DIDs the
 * document is recomposed on read from the normalized child tables; for EXTERNAL DIDs the
 * resolver-supplied document is cached out-of-band via the IDK CacheService (no persistence-
 * layer cache table). Unknown top-level JSON properties ride along in [extensionPropertiesJson]
 * so DID 1.1 documents round-trip losslessly.
 *
 * @property id UUID primary key.
 * @property tenantId Tenant this DID belongs to. Non-null; dev/test IDK callers may pass `""`.
 * @property did The DID string (e.g. `did:key:z6Mk…`). Unique per tenant.
 * @property method The DID method (e.g. `key`, `web`, `jwk`).
 * @property alias Optional human-readable alias.
 * @property role MANAGED (created locally, keys in KMS) or EXTERNAL (resolved from elsewhere).
 * @property canonicalId Canonical equivalent identifier per DID 1.1 §6.4; when set, must match
 *           one of the [DidEquivalentIdRecord.equivalentDid] values for this DID.
 * @property deactivated Whether the DID has been deactivated via the method's deactivate flow.
 * @property extensionPropertiesJson Top-level JSON properties not covered by the W3C schema,
 *           captured verbatim. `NULL` when the extension bag is empty — never `"{}"`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidRecord", exact = true)
@JsExportCompat
@Serializable
data class DidRecord(
    val id: String,
    val tenantId: String,
    val did: String,
    val method: String,
    val alias: String? = null,
    val role: DidRole,
    val canonicalId: String? = null,
    /**
     * For web-hosted methods (did:web / did:webvh), the shared web location — the did:web
     * method-specific id (`host[%3Aport][:segment...]`) this DID's document is served at. Both
     * methods that map to the same location share this value, so a `(tenant_id, web_location)`
     * unique index enforces that no two records (regardless of method) manage the same location.
     * `null` for non-web methods. Derived via [com.sphereon.did.utils.WebLocation.fromDid].
     */
    val webLocation: String? = null,
    val deactivated: Boolean = false,
    val extensionPropertiesJson: String? = null,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
    val deletedAt: Instant? = null,
    val deletedById: String? = null,
)

/**
 * Child row — a controller DID listed under [DidRecord.did].
 *
 * @property didRecordId FK → [DidRecord.id]; cascades on delete.
 * @property controllerDid A DID that controls this document. May appear more than once only
 *           with different ordinals.
 * @property ordinal Position in the source `controller` array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidControllerRecord", exact = true)
@JsExportCompat
@Serializable
data class DidControllerRecord(
    val id: String,
    val didRecordId: String,
    val controllerDid: String,
    val ordinal: Int,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
)

/**
 * Child row — an `alsoKnownAs` URI for the DID subject.
 *
 * @property akaUri The alias URI (matches the OpenAPI `AlsoKnownAsEntry.akaUri`).
 * @property ordinal Position in the source `alsoKnownAs` array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidAlsoKnownAsRecord", exact = true)
@JsExportCompat
@Serializable
data class DidAlsoKnownAsRecord(
    val id: String,
    val didRecordId: String,
    val akaUri: String,
    val ordinal: Int,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
)

/**
 * Child row — a DID 1.1 equivalent identifier. [DidRecord.canonicalId], when present,
 * must equal one of the [equivalentDid] values for the same parent (W3C DID 1.1 §6.4).
 *
 * @property equivalentDid The equivalent DID (matches OpenAPI `EquivalentIdEntry.equivalentDid`).
 * @property ordinal Position in the source `equivalentId` array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidEquivalentIdRecord", exact = true)
@JsExportCompat
@Serializable
data class DidEquivalentIdRecord(
    val id: String,
    val didRecordId: String,
    val equivalentDid: String,
    val ordinal: Int,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
)

/**
 * Child row — a JSON-LD `@context` URI.
 *
 * @property contextUri The context URI (e.g. `https://www.w3.org/ns/did/v1`).
 * @property ordinal Position in the source `@context` array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDocumentContextRecord", exact = true)
@JsExportCompat
@Serializable
data class DidDocumentContextRecord(
    val id: String,
    val didRecordId: String,
    val contextUri: String,
    val ordinal: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Child row — a DID verification method (key).
 *
 * Public key material is stored for lossless DID document round-tripping. Managed VMs may
 * additionally carry a [keyReferenceId] / KMS coordinate pair so signing flows can resolve
 * the corresponding private key through the key-reference store.
 *
 * Whether the VM is rendered in the document's top-level `verificationMethod[]` is inferred
 * at recompose time — see `DidRecordConverters`:
 *   - `inlineInJson` empty ⇒ VM appears in top-level array only.
 *   - Any relationship row references this VM by its [vmId] via
 *     [DidVerificationRelationshipRecord.entryRefDidUrl] ⇒ VM must appear in top-level.
 *   - Otherwise (inlineIn populated AND no string-ref rows) ⇒ VM is inline-only.
 *
 * @property vmId Absolute wire-form ID, e.g. `did:web:foo#key-1` (relative fragments are
 *           expanded on decompose per W3C rules).
 * @property vmIdAuthored Original authored form of [vmId] as it appeared in the source
 *           DID document, e.g. `#key-1` for a relative reference. `NULL` when the
 *           authored form is identical to the absolute [vmId]. Preserved so recompose
 *           can emit the document byte-identically. New in IDK-18 review (waf).
 * @property type Verification method type (`JsonWebKey2020`, `Ed25519VerificationKey2020`, …).
 * @property controller The DID that controls this VM.
 * @property kmsProviderId KMS provider identifier when this VM is backed by a managed key.
 * @property kmsKeyAlias Key alias inside the KMS when this VM is backed by a managed key.
 * @property kmsKid Optional KMS key identifier (nullable per IDK-19).
 * @property keyReferenceId Soft FK into the IDK-17 key-reference store when available.
 * @property publicKeyJwkJson JSON-encoded JWK key material from the DID document.
 * @property publicKeyMultibase Multibase key material from the DID document.
 * @property inlineInJson JSON array of `VerificationPurpose` wire strings whose relationship
 *           arrays embed this VM inline. `NULL` when empty (top-level rendering only).
 * @property expiresAt DID 1.1 lifecycle — when this VM ceases to be usable.
 * @property revokedAt DID 1.1 lifecycle — when this VM was revoked.
 * @property blockchainAccountId CAIP-10 blockchain account identifier.
 * @property extensionPropertiesJson Unknown JSON keys on the VM, verbatim. `NULL` when empty.
 * @property ordinal Position in the source `verificationMethod[]` array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationMethodRecord", exact = true)
@JsExportCompat
@Serializable
data class DidVerificationMethodRecord(
    val id: String,
    val didRecordId: String,
    val vmId: String,
    val vmIdAuthored: String? = null,
    val type: String,
    val controller: String,
    val kmsProviderId: String? = null,
    val kmsKeyAlias: String? = null,
    val kmsKid: String? = null,
    val keyReferenceId: String? = null,
    val publicKeyJwkJson: String? = null,
    val publicKeyMultibase: String? = null,
    val inlineInJson: String? = null,
    val expiresAt: Instant? = null,
    val revokedAt: Instant? = null,
    val blockchainAccountId: String? = null,
    val extensionPropertiesJson: String? = null,
    val ordinal: Int,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
)

/**
 * Child row — a single entry in one of the DID's relationship arrays (authentication,
 * assertionMethod, keyAgreement, capabilityInvocation, capabilityDelegation).
 *
 * IDK-19 collapsed the previous `verificationMethod`/`referenceDidUrl`/`isEmbedded` trio
 * into a polymorphic `entry` field (`oneOf(String | VerificationMethod)`). Persistence keeps
 * two mutually-exclusive columns — exactly one is populated:
 *
 * - String reference: [entryRefDidUrl] = original wire string (absolute DID URL or `#frag`),
 *   [entryEmbeddedVmId] = null.
 * - Inline VM object: [entryEmbeddedVmId] = UUID of the backing [DidVerificationMethodRecord]
 *   row, [entryRefDidUrl] = null. Embedded VMs are real VM records, not JSON blobs.
 *
 * Enforced by SQL `CHECK` constraint (where dialect supports it) and by converter assertions.
 *
 * @property purpose One of `authentication`, `assertionMethod`, `keyAgreement`,
 *           `capabilityInvocation`, `capabilityDelegation`.
 * @property entryEmbeddedVmId FK (UUID) to [DidVerificationMethodRecord.id] when the entry is
 *           an inline VM object. Null for string references.
 * @property entryRefDidUrl Wire-string form of the entry (absolute DID URL or `#fragment`) when
 *           the entry was a string. Null for inline VMs.
 * @property ordinal Position in the source relationship array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVerificationRelationshipRecord", exact = true)
@JsExportCompat
@Serializable
data class DidVerificationRelationshipRecord(
    val id: String,
    val didRecordId: String,
    val purpose: String,
    val entryEmbeddedVmId: String? = null,
    val entryRefDidUrl: String? = null,
    val ordinal: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        // Both columns may legitimately be set for "string-ref pointing at a VM held in the
        // same document": [entryRefDidUrl] preserves the originally authored wire form, while
        // [entryEmbeddedVmId] retains the local FK to the [DidVerificationMethodRecord].
        // Only the totally empty case (or empty-string ref) is rejected — a relationship must
        // point somewhere.
        require(!entryEmbeddedVmId.isNullOrBlank() || !entryRefDidUrl.isNullOrBlank()) {
            "Relationship entry must set entryEmbeddedVmId, entryRefDidUrl, or both (non-blank)"
        }
    }
}

/**
 * Child row — a service endpoint.
 *
 * @property serviceId Wire-form service ID (e.g. `did:web:foo#svc-1`).
 * @property typeJson JSON — either a bare string or an array of strings, matching the DID 1.1
 *           polymorphism of the service `type` field.
 * @property serviceEndpointJson JSON — string, object, or array of strings/objects; stored
 *           verbatim so the three shapes round-trip exactly.
 * @property extensionPropertiesJson Unknown JSON keys on the service. `NULL` when empty.
 * @property ordinal Position in the source `service[]` array; preserved on round-trip.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidServiceRecord", exact = true)
@JsExportCompat
@Serializable
data class DidServiceRecord(
    val id: String,
    val didRecordId: String,
    val serviceId: String,
    val typeJson: String,
    val serviceEndpointJson: String,
    val extensionPropertiesJson: String? = null,
    val ordinal: Int,
    val createdAt: Instant,
    val createdById: String? = null,
    val updatedAt: Instant,
    val updatedById: String? = null,
)

/**
 * KMS coordinates for a verification method, supplied to the decomposer by callers that know
 * which KMS-registered key backs a given wire-format VM. Wraps the crypto-core [KeyInfo]
 * triple (`providerId` + `alias` + optional `kid`) — same shape the IDK-19 `KeyInfo` wire
 * schema exposes — plus the resolved IDK-17 [keyReferenceId] so the converter can write
 * both the KMS triple and the soft FK onto [DidVerificationMethodRecord] in one pass.
 *
 * Note: [KeyInfo.providerId] and [KeyInfo.alias] are nullable on the wire type. The
 * decomposer treats them as nullable too (a missing binding for a managed VM is already
 * an `Err`; a binding whose providerId/alias happens to be null degrades the same way as
 * the original flat-field shape would have when those fields were absent). Callers
 * targeting managed VMs are expected to supply both.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidVmKmsBinding", exact = true)
@JsExportCompat
@Serializable
data class VmKmsBinding(
    val keyInfo: KeyInfo<*>,
    val keyReferenceId: String? = null,
)

/**
 * Persistence model for DID key mappings.
 *
 * Binds a verification method (by its UUID FK) to a KMS key, scoped to a set of purposes.
 * Matches IDK-19 `KeyMapping`: the KMS triple ([kmsProviderId] + [kmsKeyAlias] + optional
 * [kmsKid]) locates the key; [purposesJson] restricts which verification purposes may use it.
 *
 * @property verificationMethodId UUID FK to [DidVerificationMethodRecord.id]. Strong
 *           referential integrity: deletion of the VM cascades to the mapping at the DB
 *           layer (`ON DELETE CASCADE`). IDK-18 review §4 / VDX-infra-o77.
 * @property verificationMethodDidUrl Wire-form VM id the mapping points at (e.g. `#key-1`
 *           or an absolute DID URL). Retained for serialization/API surfaces that report
 *           the document-relative reference; `null` when the wire form is unavailable.
 * @property kmsProviderId KMS provider identifier (required).
 * @property kmsKeyAlias Key alias inside the KMS (required per IDK-19 — NOT NULL).
 * @property kmsKid Optional KMS key identifier (nullable per IDK-19).
 * @property keyReferenceId Soft FK into the IDK-17 key-reference store when available.
 * @property purposesJson JSON array of purpose strings
 *           (`authentication`, `assertionMethod`, …).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidKeyMappingRecord", exact = true)
@JsExportCompat
@Serializable
data class DidKeyMappingRecord(
    val id: String,
    val didRecordId: String,
    val verificationMethodId: String,
    val verificationMethodDidUrl: String? = null,
    val kmsProviderId: String,
    val kmsKeyAlias: String,
    val kmsKid: String? = null,
    val keyReferenceId: String? = null,
    val purposesJson: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Full persistence view of a DID — the aggregate root [record] and every child-row collection
 * that makes up its document. Matches the IDK-19 REST `DidDetail` schema shape (hence the name)
 * so persistence and wire speak the same vocabulary.
 *
 * Always loaded and saved as a unit. External-DID document caching is handled out-of-band
 * by the IDK CacheService and is not persisted on the aggregate.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDetail", exact = true)
@JsExportCompat
@Serializable
data class DidDetail(
    val record: DidRecord,
    val controller: List<DidControllerRecord> = emptyList(),
    val alsoKnownAs: List<DidAlsoKnownAsRecord> = emptyList(),
    val equivalentId: List<DidEquivalentIdRecord> = emptyList(),
    val context: List<DidDocumentContextRecord> = emptyList(),
    val verificationMethod: List<DidVerificationMethodRecord> = emptyList(),
    val verificationRelationship: List<DidVerificationRelationshipRecord> = emptyList(),
    val service: List<DidServiceRecord> = emptyList(),
    val keyMapping: List<DidKeyMappingRecord> = emptyList(),
)

/**
 * Filter criteria for querying DID records.
 *
 * @property tenantId Required on tenant-aware dialects (PostgreSQL, MySQL); may be null in
 *           IDK dev mode (Memory, SQLite). Null means "any tenant".
 * @property method Filter by DID method.
 * @property alias Filter by alias (exact match).
 * @property role Filter by role.
 * @property search Case-insensitive substring match against the DID string and alias.
 *                  `null` disables the search filter.
 * @property includeDeactivated Include `deactivated = true` rows in results.
 * @property includeDeleted Include `deleted_at IS NOT NULL` rows in results.
 * @property sort Column to order results by.
 * @property sortDirection Ascending or descending.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidRecordFilter", exact = true)
@JsExportCompat
@Serializable
data class DidRecordFilter(
    val tenantId: String? = null,
    val method: String? = null,
    val alias: String? = null,
    val role: DidRole? = null,
    val search: String? = null,
    val includeDeactivated: Boolean = false,
    val includeDeleted: Boolean = false,
    /** Zero-based page index. Combined with [size] to slice the result set. */
    val page: Int = 0,
    /**
     * Page size. `null` means no pagination — return every matching aggregate. Repository
     * implementations push this down to the storage layer (LIMIT/OFFSET in SQL, slice in
     * memory) so callers never load the full set when they only need a window.
     */
    val size: Int? = null,
    val sort: DidSortField = DidSortField.CREATED_AT,
    val sortDirection: SortDirection = SortDirection.DESC,
)
