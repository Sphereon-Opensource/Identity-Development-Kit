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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.utils.WebLocation
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

// File overview: bidirectional bridge between the W3C wire model
// (DidDocument / VerificationMethod / DidService) and the persistence aggregate
// (DidDetail and its child records).
//
//  - Decompose (DidDocument.toDidDetail): flattens a wire document into normalised rows,
//    expands relative VM IDs to absolute form, splits verification relationships into
//    per-purpose rows, and captures unknown JSON keys verbatim in the various
//    `extensionPropertiesJson` columns so the wire shape round-trips losslessly.
//  - Recompose (DidDetail.toDidDocument): rebuilds the wire document from the rows,
//    honouring `ordinal` columns for stable ordering and the inline-vs-reference rules
//    described on DidVerificationMethodRecord.inlineInJson.
//
// `toManagedDid` and `toDidRecordFilter` are the small manager-facing projections.

/**
 * JSON codec used for every encode/decode in this file. Lenient and tolerant of unknown
 * keys so we don't reject documents containing newer DID Core properties or vendor
 * extensions; defaults are not emitted to keep stored JSON minimal.
 */
private val json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

// ==================================================================================
// Decomposition — DidDocument → DidDetail
// ==================================================================================

/**
 * Inputs the caller must supply alongside a [DidDocument] when decomposing it to a
 * [DidDetail]. Kept as a small data class rather than a long parameter list
 * so new audit / tenancy fields can be added without breaking every call site.
 *
 * @property recordId Stable UUID for the [DidRecord] row. New on create, reused on update.
 * @property did The DID string — must equal [DidDocument.id].
 * @property method DID method portion (`key`, `web`, …). Matches [DidRecord.method].
 * @property role MANAGED or EXTERNAL.
 * @property tenantId Tenant that owns this aggregate. Dev/test callers may pass `""`.
 * @property idGen Source of row ids for every child record.
 * @property clock Source of timestamps for new audit columns.
 * @property createdAt When the parent record was originally created — preserved on update.
 *           Pass `null` on first insert (uses `clock.now()`).
 * @property createdById Actor that originally created the record. Preserved on update.
 * @property updatedById Actor performing the current save.
 * @property alias Optional human-readable alias.
 * @property canonicalId DID 1.1 §6.4 — canonical equivalent identifier, must match one
 *           of the `equivalentId` values when provided.
 * @property equivalentIds Additional identifiers that refer to the same subject.
 * @property deactivated Whether the DID is deactivated.
 * @property keyMappings Key mappings the manager has already resolved. Decompose itself cannot
 *           manufacture these — the REST/manager layer produces them when handling the incoming
 *           request (IDK-19 `KeyMappingCreateRequest` carries the KMS triple).
 * @property vmKmsBindings KMS coordinates (`providerId`, `alias`, optional `kid`) per VM, keyed
 *           by the VM's absolute wire id (e.g. `did:example:123#key-1`). Required because wire
 *           documents carry `publicKeyJwk`/`publicKeyMultibase`/`blockchainAccountId` but not
 *           KMS identifiers; the caller resolves the binding via IDK-17 before decompose. A VM
 *           without a binding entry causes decompose to return `Err`.
 * @property inlineInByVmId Purposes each VM should be rendered inline into, keyed by absolute
 *           VM id. When the wire document has a VM embedded inline inside a relationship, that
 *           purpose is added automatically; this field lets the caller declare additional
 *           inline intents (e.g., for REST `VerificationMethodCreateRequest.inlineIn` paths
 *           where the request arrives separately from a document).
 */
data class DecomposeContext(
    val recordId: String,
    val did: String,
    val method: String,
    val role: DidRole,
    val tenantId: String,
    val idGen: IdGenerator,
    val clock: Clock,
    val createdAt: Instant? = null,
    val createdById: String? = null,
    val updatedById: String? = null,
    val alias: String? = null,
    val canonicalId: String? = null,
    val equivalentIds: List<String> = emptyList(),
    val deactivated: Boolean = false,
    val keyMappings: List<DidKeyMappingRecord> = emptyList(),
    val vmKmsBindings: Map<String, VmKmsBinding> = emptyMap(),
    val inlineInByVmId: Map<String, Set<VerificationPurpose>> = emptyMap(),
)

/**
 * Decomposes [this] document into a [DidDetail].
 *
 * Implements the rules described in the IDK-18 plan §Decomposition Rules, including the
 * edge cases:
 *  - VMs that appear only inline in a relationship get a [DidVerificationMethodRecord] with
 *    `inlineInJson` populated (set of purposes where the VM is embedded) plus a matching
 *    [DidVerificationRelationshipRecord] with `entryEmbeddedVmId` pointing at the VM row.
 *  - Relationship entries that are plain string references carry the wire string in
 *    `entryRefDidUrl`. When the reference points at a VM held in this same document the
 *    local VM row's UUID is also stored in `entryEmbeddedVmId` so cascade deletes can
 *    operate on a stable FK; `entryRefDidUrl` remains the source of truth for round-trip
 *    fidelity. References to VMs outside the document leave `entryEmbeddedVmId = null`.
 *  - Relative VM IDs (`#fragment`) inside `verificationMethod[]` are expanded to absolute form.
 *  - Duplicate VM IDs across inline and top-level VMs yield [IdkError.ILLEGAL_ARGUMENT_ERROR].
 *  - Service `id` collisions yield [IdkError.ILLEGAL_ARGUMENT_ERROR].
 *  - Empty extension maps become `NULL` JSON, never `"{}"`.
 *  - Ordinals are populated from the input array indices so re-composition reproduces the
 *    original array order.
 */
fun DidDocument.toDidDetail(ctx: DecomposeContext): IdkResult<DidDetail, IdkError> {
    if (this.id != ctx.did) {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "DidDocument.id (${this.id}) does not match context.did (${ctx.did})"))
    }

    if (ctx.canonicalId != null && ctx.canonicalId !in ctx.equivalentIds) {
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message =
                    "canonicalId '${ctx.canonicalId}' must appear in equivalentIds " +
                        "(equivalentIds=${ctx.equivalentIds})",
            )
        )
    }

    val now = ctx.clock.now()
    val createdAt = ctx.createdAt ?: now

    val record =
        DidRecord(
            id = ctx.recordId,
            tenantId = ctx.tenantId,
            did = ctx.did,
            method = ctx.method,
            alias = ctx.alias,
            role = ctx.role,
            canonicalId = ctx.canonicalId,
            webLocation = WebLocation.fromDid(ctx.method, ctx.did),
            deactivated = ctx.deactivated,
            extensionPropertiesJson = extensions.toJsonStringOrNull(),
            createdAt = createdAt,
            createdById = ctx.createdById,
            updatedAt = now,
            updatedById = ctx.updatedById,
        )

    val controllers =
        controller.mapIndexed { idx, ctrlDid ->
            DidControllerRecord(
                id = ctx.idGen.next(),
                didRecordId = ctx.recordId,
                controllerDid = ctrlDid,
                ordinal = idx,
                createdAt = createdAt,
                createdById = ctx.createdById,
                updatedAt = now,
                updatedById = ctx.updatedById,
            )
        }

    val alsoKnown =
        (alsoKnownAs ?: emptyList()).mapIndexed { idx, uri ->
            DidAlsoKnownAsRecord(
                id = ctx.idGen.next(),
                didRecordId = ctx.recordId,
                akaUri = uri,
                ordinal = idx,
                createdAt = createdAt,
                createdById = ctx.createdById,
                updatedAt = now,
                updatedById = ctx.updatedById,
            )
        }

    val equivalents =
        ctx.equivalentIds.mapIndexed { idx, eq ->
            DidEquivalentIdRecord(
                id = ctx.idGen.next(),
                didRecordId = ctx.recordId,
                equivalentDid = eq,
                ordinal = idx,
                createdAt = createdAt,
                createdById = ctx.createdById,
                updatedAt = now,
                updatedById = ctx.updatedById,
            )
        }

    val contexts =
        context.mapIndexed { idx, uri ->
            DidDocumentContextRecord(
                id = ctx.idGen.next(),
                didRecordId = ctx.recordId,
                contextUri = uri,
                ordinal = idx,
                createdAt = createdAt,
                updatedAt = now,
            )
        }

    // Rule #4 — expand relative VM ids (`#frag`) to absolute form on decompose.
    fun absoluteVmId(raw: String): String =
        when {
            raw.startsWith("#") -> ctx.did + raw
            else -> raw
        }

    // First pass — collect top-level VMs keyed by absolute id. We defer creating the records
    // until inline purposes are known so `inlineInJson` can be populated in one shot.
    val topLevelWireByAbsoluteId = linkedMapOf<String, Pair<Int, VerificationMethod>>()
    (verificationMethod ?: emptyList()).forEachIndexed { idx, vm ->
        val absoluteId = absoluteVmId(vm.id)
        if (topLevelWireByAbsoluteId.containsKey(absoluteId)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Duplicate VM id in verificationMethod[]: $absoluteId"))
        }
        topLevelWireByAbsoluteId[absoluteId] = idx to vm
    }

    // Second pass — walk relationships, collecting inline-purpose sets for each VM id and
    // producing relationship rows. Inline VMs allocate their own record slot.
    val inlineWireVms = linkedMapOf<String, VerificationMethod>()
    val inlinePurposesByVmId = linkedMapOf<String, MutableSet<VerificationPurpose>>()
    val pendingRelationships = mutableListOf<PendingRelationship>()

    for (purpose in VerificationPurpose.entries) {
        val list =
            when (purpose) {
                VerificationPurpose.AUTHENTICATION -> authentication
                VerificationPurpose.ASSERTION_METHOD -> assertionMethod
                VerificationPurpose.KEY_AGREEMENT -> keyAgreement
                VerificationPurpose.CAPABILITY_INVOCATION -> capabilityInvocation
                VerificationPurpose.CAPABILITY_DELEGATION -> capabilityDelegation
            } ?: continue

        list.forEachIndexed { ordinal, entry ->
            val embedded = entry.embedded
            val reference = entry.reference
            when {
                embedded != null -> {
                    val absoluteId = absoluteVmId(embedded.id)
                    // Rule #2 — inline VM colliding with a top-level VM by id is illegal.
                    if (topLevelWireByAbsoluteId.containsKey(absoluteId)) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Duplicate VM id: $absoluteId appears both inline and in verificationMethod[]",
                            ),
                        )
                    }
                    inlineWireVms[absoluteId]?.let { existing ->
                        if (existing != embedded) {
                            return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "Conflicting inline VM bodies for id: $absoluteId",
                                ),
                            )
                        }
                    } ?: run {
                        inlineWireVms[absoluteId] = embedded
                    }
                    inlinePurposesByVmId.getOrPut(absoluteId) { linkedSetOf() }.add(purpose)
                    pendingRelationships +=
                        PendingRelationship(
                            purpose = purpose,
                            ordinal = ordinal,
                            embeddedVmAbsoluteId = absoluteId,
                            refDidUrl = null,
                        )
                }

                reference != null -> {
                    pendingRelationships +=
                        PendingRelationship(
                            purpose = purpose,
                            ordinal = ordinal,
                            embeddedVmAbsoluteId = null,
                            refDidUrl = reference,
                        )
                }
            }
        }
    }

    // Merge caller-declared inlineIn intents (REST paths) with those observed from the document.
    for ((vmId, purposes) in ctx.inlineInByVmId) {
        inlinePurposesByVmId.getOrPut(vmId) { linkedSetOf() }.addAll(purposes)
    }

    // Materialize VM records now that inlineIn is known. Top-level VMs first (preserve ordinal),
    // then inline-only VMs appended with increasing ordinals.
    val vmRecordsByAbsoluteId = linkedMapOf<String, DidVerificationMethodRecord>()
    for ((absoluteId, pair) in topLevelWireByAbsoluteId) {
        val (idx, vm) = pair
        val binding = ctx.vmKmsBindings[absoluteId]
        if (binding == null && ctx.role == DidRole.MANAGED) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Missing VmKmsBinding for managed verification method $absoluteId",
                ),
            )
        }
        val rec =
            vm.toRecord(
                ctx = ctx,
                absoluteId = absoluteId,
                binding = binding,
                inlinePurposes = inlinePurposesByVmId[absoluteId].orEmpty(),
                ordinal = idx,
                now = now,
                createdAt = createdAt,
            )
        vmRecordsByAbsoluteId[absoluteId] = rec
    }
    var nextInlineOrdinal = topLevelWireByAbsoluteId.size
    for ((absoluteId, vm) in inlineWireVms) {
        val binding = ctx.vmKmsBindings[absoluteId]
        if (binding == null && ctx.role == DidRole.MANAGED) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Missing VmKmsBinding for managed inline verification method $absoluteId",
                ),
            )
        }
        val rec =
            vm.toRecord(
                ctx = ctx,
                absoluteId = absoluteId,
                binding = binding,
                inlinePurposes = inlinePurposesByVmId[absoluteId].orEmpty(),
                ordinal = nextInlineOrdinal++,
                now = now,
                createdAt = createdAt,
            )
        vmRecordsByAbsoluteId[absoluteId] = rec
    }

    val relationshipRecords =
        pendingRelationships.map { pr ->
            // For embedded entries, resolve the absolute VM id to its local UUID. For string-ref
            // entries, also try to resolve the ref against the local VM map: if it points at a VM
            // held in the same document we keep BOTH the wire-form string (preserving the
            // originally authored relative/absolute form) AND the local FK so cascade deletes can
            // operate on a stable id without losing round-trip fidelity.
            val embeddedUuid = pr.embeddedVmAbsoluteId?.let { vmRecordsByAbsoluteId[it]?.id }
            val refLocalUuid =
                pr.refDidUrl?.let { ref ->
                    val absolute = if (ref.startsWith("#")) ctx.did + ref else ref
                    vmRecordsByAbsoluteId[absolute]?.id
                }
            DidVerificationRelationshipRecord(
                id = ctx.idGen.next(),
                didRecordId = ctx.recordId,
                purpose = pr.purpose.wire(),
                entryEmbeddedVmId = embeddedUuid ?: refLocalUuid,
                entryRefDidUrl = pr.refDidUrl,
                ordinal = pr.ordinal,
                createdAt = createdAt,
                updatedAt = now,
            )
        }

    val allVmRecords = vmRecordsByAbsoluteId.values.toList()

    val serviceRecords =
        (service ?: emptyList()).mapIndexed { idx, svc ->
            if (svc.type.isEmpty()) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Service ${svc.id} has no type"))
            }
            DidServiceRecord(
                id = ctx.idGen.next(),
                didRecordId = ctx.recordId,
                serviceId = svc.id,
                typeJson = json.encodeToString(ListSerializer(String.serializer()), svc.type),
                serviceEndpointJson = json.encodeToString(JsonElement.serializer(), svc.serviceEndpoint),
                extensionPropertiesJson = svc.extensions.toJsonStringOrNull(),
                ordinal = idx,
                createdAt = createdAt,
                createdById = ctx.createdById,
                updatedAt = now,
                updatedById = ctx.updatedById,
            )
        }

    // Rule #9 — service id collision within one document.
    serviceRecords
        .groupBy { it.serviceId }
        .firstNotNullOfOrNull { (id, group) ->
            if (group.size > 1) id else null
        }?.let { dup ->
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Duplicate service id: $dup"))
        }

    // IDK-18 o77: rewrite manager-supplied keyMappings so verificationMethodId is the local
    // VM record UUID (FK target) and verificationMethodDidUrl carries the wire form. Manager
    // callsites originally populate verificationMethodId with the wire DID URL (relative or
    // absolute fragment); we resolve to UUID here against vmRecordsByAbsoluteId.
    val rewrittenKeyMappings =
        ctx.keyMappings.map { km ->
            val absolute = absoluteVmId(km.verificationMethodId)
            val vmRecord = vmRecordsByAbsoluteId[absolute]
            if (vmRecord != null) {
                km.copy(
                    verificationMethodId = vmRecord.id,
                    verificationMethodDidUrl = km.verificationMethodDidUrl ?: km.verificationMethodId,
                )
            } else {
                // Caller already supplied a UUID-shaped id; pass through. The verificationMethodDidUrl
                // remains caller-supplied (or null).
                km
            }
        }

    return Ok(
        DidDetail(
            record = record,
            controller = controllers,
            alsoKnownAs = alsoKnown,
            equivalentId = equivalents,
            context = contexts,
            verificationMethod = allVmRecords,
            verificationRelationship = relationshipRecords,
            service = serviceRecords,
            keyMapping = rewrittenKeyMappings,
        ),
    )
}

/**
 * Intermediate representation built during decompose: one entry per occurrence of a VM
 * inside a per-purpose relationship array. Resolved later into either an inline
 * ([embeddedVmAbsoluteId] set) or by-reference ([refDidUrl] set) row in
 * `did_verification_relationship`. Exactly one of the two must be non-null.
 */
private data class PendingRelationship(
    val purpose: VerificationPurpose,
    val ordinal: Int,
    val embeddedVmAbsoluteId: String?,
    val refDidUrl: String?,
)

/**
 * Builds a [DidVerificationMethodRecord] for one VM. KMS coordinates from the optional
 * [binding] are folded in for managed VMs; [inlinePurposes] becomes the comma-collapsed
 * `inlineInJson` column that drives recompose's inline-vs-top-level rendering decision.
 * [createdAt] is preserved on update; [now] becomes `updatedAt`.
 */
private fun VerificationMethod.toRecord(
    ctx: DecomposeContext,
    absoluteId: String,
    binding: VmKmsBinding?,
    inlinePurposes: Set<VerificationPurpose>,
    ordinal: Int,
    now: Instant,
    createdAt: Instant,
): DidVerificationMethodRecord =
    DidVerificationMethodRecord(
        id = ctx.idGen.next(),
        didRecordId = ctx.recordId,
        vmId = absoluteId,
        // Preserve authored form for byte-identical round-trip when the source document used a
        // relative reference like "#key-1". `null` when authored form already matches absolute.
        vmIdAuthored = id.takeIf { it != absoluteId },
        type = type,
        controller = controller,
        kmsProviderId = binding?.keyInfo?.providerId,
        kmsKeyAlias = binding?.keyInfo?.alias,
        kmsKid = binding?.keyInfo?.kid,
        keyReferenceId = binding?.keyReferenceId,
        publicKeyJwkJson = publicKeyJwk?.let { json.encodeToString(Jwk.serializer(), it) },
        publicKeyMultibase = publicKeyMultibase,
        inlineInJson = inlinePurposes.toJsonArrayStringOrNull(),
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        blockchainAccountId = blockchainAccountId,
        extensionPropertiesJson = extensions.toJsonStringOrNull(),
        ordinal = ordinal,
        createdAt = createdAt,
        createdById = ctx.createdById,
        updatedAt = now,
        updatedById = ctx.updatedById,
    )

// ==================================================================================
// Recomposition — DidDetail → DidDocument
// ==================================================================================

/**
 * Reconstructs the wire-form DID document from an aggregate. Ordering is preserved via the
 * `ordinal` columns on every child row. Extension bags empty out to `emptyMap()` when the
 * stored JSON was `NULL`.
 */
fun DidDetail.toDidDocument(): DidDocument {
    val contextUris =
        context
            .sortedBy { it.ordinal }
            .map { it.contextUri }
            .ifEmpty { listOf(DidDocument.DEFAULT_CONTEXT) }

    val controllerDids = controller.sortedBy { it.ordinal }.map { it.controllerDid }
    val akaUris = alsoKnownAs.sortedBy { it.ordinal }.map { it.akaUri }

    val vmByUuid = verificationMethod.associateBy { it.id }
    val relationshipsByPurpose = verificationRelationship.groupBy { it.purpose }

    // A VM is rendered top-level (in `verificationMethod[]`) iff:
    //   - it has no inline purposes (empty `inlineInJson`), OR
    //   - at least one relationship points to it by string (entryRefDidUrl matches vmId).
    // This matches the IDK-19 spec: `inlineIn` populated means embed inline in those purposes,
    // "instead of — or in addition to — the top-level array"; the top-level presence is
    // signalled by the existence of a string-ref relationship row.
    //
    // Decompose expanded VM ids to absolute form (Rule #4), but entry_ref_did_url preserves
    // the raw wire string. Normalize relative refs ("#frag" → "<did>#frag") before comparing.
    val referencedByStringVmIds =
        verificationRelationship
            .mapNotNull { it.entryRefDidUrl }
            .map { ref -> if (ref.startsWith("#")) record.did + ref else ref }
            .toSet()

    val topLevelWireVms =
        verificationMethod
            .asSequence()
            .filter { vm ->
                vm.inlineInJson.isNullOrBlank() || vm.vmId in referencedByStringVmIds
            }.sortedBy { it.ordinal }
            .map { it.toWireVerificationMethod() }
            .toList()

    fun rebuild(purpose: VerificationPurpose): List<VerificationMethodOrReference>? {
        val rows = relationshipsByPurpose[purpose.wire()] ?: return null
        return rows.sortedBy { it.ordinal }.map { rel ->
            val embeddedVmId = rel.entryEmbeddedVmId
            val refUrl = rel.entryRefDidUrl
            // Wire form is decided by entryRefDidUrl: if it was authored as a string reference
            // (whether or not the reference points at a VM held in this same document), the
            // entry round-trips as a reference. entryEmbeddedVmId is populated for two
            // different cases — (a) inline-embedded entries, where it's the FK to the inline VM
            // record, and (b) string-ref entries to a same-doc VM, where it's the FK for
            // cascade delete. Only case (a) should emit as embedded on recompose, so check
            // refUrl first.
            when {
                refUrl != null -> {
                    VerificationMethodOrReference.fromReference(refUrl)
                }

                embeddedVmId != null -> {
                    val vmRec =
                        vmByUuid[embeddedVmId]
                            ?: error("Relationship ${rel.id} is embedded but has no resolvable entryEmbeddedVmId")
                    VerificationMethodOrReference.fromEmbedded(vmRec.toWireVerificationMethod())
                }

                else -> {
                    error("Relationship ${rel.id} has neither entryEmbeddedVmId nor entryRefDidUrl")
                }
            }
        }
    }

    val services = service.sortedBy { it.ordinal }.map { it.toWireService() }

    val docExtensions = record.extensionPropertiesJson?.toJsonMapOrEmpty() ?: emptyMap()

    return DidDocument(
        context = contextUris,
        id = record.did,
        controller = controllerDids,
        alsoKnownAs = akaUris.ifEmpty { null },
        verificationMethod = topLevelWireVms.ifEmpty { null },
        authentication = rebuild(VerificationPurpose.AUTHENTICATION),
        assertionMethod = rebuild(VerificationPurpose.ASSERTION_METHOD),
        keyAgreement = rebuild(VerificationPurpose.KEY_AGREEMENT),
        capabilityInvocation = rebuild(VerificationPurpose.CAPABILITY_INVOCATION),
        capabilityDelegation = rebuild(VerificationPurpose.CAPABILITY_DELEGATION),
        service = services.ifEmpty { null },
        extensions = docExtensions,
    )
}

/**
 * Rebuilds a wire-shape [VerificationMethod] from a persistence row. KMS coordinates and
 * the `inlineInJson` column are intentionally dropped — they're persistence-only
 * concerns the wire form does not carry.
 */
private fun DidVerificationMethodRecord.toWireVerificationMethod(): VerificationMethod =
    VerificationMethod(
        // Emit authored form when present so a document originally written with relative
        // refs ("#key-1") round-trips byte-identical. Falls back to absolute vmId otherwise.
        id = vmIdAuthored ?: vmId,
        type = type,
        controller = controller,
        publicKeyJwk = publicKeyJwkJson?.let { json.decodeFromString(Jwk.serializer(), it) },
        publicKeyMultibase = publicKeyMultibase,
        blockchainAccountId = blockchainAccountId,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        extensions = extensionPropertiesJson?.toJsonMapOrEmpty() ?: emptyMap(),
    )

/**
 * Rebuilds a wire-shape [DidService] from a persistence row. The `type` column is stored as
 * a JSON array even when the wire form is a single string — that re-collapse happens
 * automatically via [com.sphereon.did.serializers.StringOrStringListSerializer] on encode.
 */
private fun DidServiceRecord.toWireService(): DidService {
    val types = json.decodeFromString(ListSerializer(String.serializer()), typeJson)
    val endpoint = json.decodeFromString(JsonElement.serializer(), serviceEndpointJson)
    return DidService(
        id = serviceId,
        type = types,
        serviceEndpoint = endpoint,
        extensions = extensionPropertiesJson?.toJsonMapOrEmpty() ?: emptyMap(),
    )
}

// ==================================================================================
// Manager-facing convenience
// ==================================================================================

/**
 * Builds a [ManagedDid] view of an aggregate for manager-layer responses. Key mappings are
 * projected from the persistence `did_key_mapping` rows into the manager-facing wire shape
 * (`DidKeyMapping`).
 */
fun DidDetail.toManagedDid(): ManagedDid =
    ManagedDid(
        id = record.id,
        did = record.did,
        method = record.method,
        alias = record.alias,
        document = toDidDocument(),
        role = record.role,
        deactivated = record.deactivated,
        keys =
            keyMapping.map {
                com.sphereon.did.manager.DidKeyMapping(
                    id = it.id,
                    // Manager wire surface expects the DID URL form, not the persistence FK UUID.
                    // Falls back to verificationMethodId for legacy rows lacking the new column.
                    verificationMethodId = it.verificationMethodDidUrl ?: it.verificationMethodId,
                    kmsKeyAlias = it.kmsKeyAlias,
                    kmsProviderId = it.kmsProviderId,
                    purposesJson = it.purposesJson,
                )
            },
        createdAt = record.createdAt.toString(),
        updatedAt = record.updatedAt.toString(),
    )

/**
 * Translates the manager-facing [DidFilter] into the persistence [DidRecordFilter]. The
 * tenant dimension is supplied separately by the caller since it is not part of the public
 * manager surface; every other dimension (`includeDeleted` included) is taken from the
 * [DidFilter] itself so REST/CLI flags propagate through without callers having to know
 * about an out-of-band override.
 */
fun DidFilter.toDidRecordFilter(tenantId: String? = null): DidRecordFilter =
    DidRecordFilter(
        tenantId = tenantId,
        method = method,
        alias = alias,
        role = role,
        search = search,
        includeDeactivated = includeDeactivated,
        includeDeleted = includeDeleted,
        page = page,
        size = size,
        sort = sort,
        sortDirection = sortDirection,
    )

// ==================================================================================
// Internal JSON helpers
// ==================================================================================

/** Encodes an extension bag for storage; empty bag becomes `NULL` (never `"{}"`). */
private fun Map<String, JsonElement>.toJsonStringOrNull(): String? = if (isEmpty()) null else json.encodeToString(JsonObject.serializer(), JsonObject(this))

/** Encodes a set of inline purposes as a JSON string array for the `inlineInJson` column. */
private fun Set<VerificationPurpose>.toJsonArrayStringOrNull(): String? {
    if (isEmpty()) return null
    val arr = JsonArray(this.map { JsonPrimitive(it.wire()) })
    return json.encodeToString(JsonArray.serializer(), arr)
}

/** Decodes a stored extension bag. Returns empty when the JSON isn't a top-level object. */
private fun String.toJsonMapOrEmpty(): Map<String, JsonElement> =
    when (val parsed = json.parseToJsonElement(this)) {
        is JsonObject -> parsed.toMap()
        else -> emptyMap()
    }

/** Wire-form name of a verification purpose, matching the W3C DID Core property names. */
private fun VerificationPurpose.wire(): String =
    when (this) {
        VerificationPurpose.AUTHENTICATION -> "authentication"
        VerificationPurpose.ASSERTION_METHOD -> "assertionMethod"
        VerificationPurpose.KEY_AGREEMENT -> "keyAgreement"
        VerificationPurpose.CAPABILITY_INVOCATION -> "capabilityInvocation"
        VerificationPurpose.CAPABILITY_DELEGATION -> "capabilityDelegation"
    }
