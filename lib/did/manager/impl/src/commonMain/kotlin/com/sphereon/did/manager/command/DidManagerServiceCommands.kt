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

package com.sphereon.did.manager.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidExpand
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.manager.MethodCapabilitySummary
import com.sphereon.did.manager.VerificationRelationship
import com.sphereon.did.manager.impl.command.toServiceResponse
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.persistence.DidAlsoKnownAsRecord
import com.sphereon.did.persistence.DidControllerRecord
import com.sphereon.did.persistence.DidEquivalentIdRecord
import com.sphereon.did.resolver.DidResolutionResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Instant

/*
 * Typed ServiceCommand layer for the DID Manager REST surface.
 *
 * Mirrors the KMS pattern: each HTTP endpoint has a typed command interface that workflow code
 * can call directly with input/output data classes (no GenericHttpRequest blob construction).
 * The HTTP endpoint commands in [DidManagerEndpointCommands] / [DidSubResourceCommands]
 * delegate to these service commands; the HTTP layer is purely request parsing + response
 * serialization, while business logic lives in the service command implementations.
 *
 * `pathPattern` values include the `/dids` prefix because the resolver matches against the
 * full request path (the mount basePath is not joined). `commandId` values match the existing
 * `XxxEndpointCommand.COMMAND_ID` constants verbatim so the multibinding registry stays
 * consistent across HTTP and binary transports.
 */

// ===== Shared input wrappers =====

/** Input for endpoints that take only a DID path parameter. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidIdInput", exact = true)
@Serializable
data class DidIdInput(
    val did: String,
)

/**
 * Wire payload for POST /dids — mirrors the OpenAPI `DidCreateRequest` shape (providerId/kid/
 * alias/didAlias) instead of leaking the SDK [DidCreateOptions] type to clients. The service
 * command impl maps this to a [DidCreateOptions] with a single VerificationMethodConfig pinned
 * to the KMS key resolved from [keyInfo].
 *
 * Key material is **never** supplied by the caller. [keyInfo] references an already-registered
 * KMS key by `(providerId, alias)` or `(providerId, kid)`; the server resolves the public JWK
 * from KMS and uses it for did:key / did:jwk derivation (and for did:web document
 * generation). The caller is responsible for first registering the key with the KMS via the
 * KMS API; this endpoint never accepts wire-supplied JWK material.
 *
 * `alsoKnownAs` is accepted for spec parity but currently rejected with `UNPROCESSABLE_ENTITY`
 * at the impl layer pending manager support — create the DID first, then POST to
 * `/dids/{did}/also-known-as`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateDidInput", exact = true)
@Serializable
data class CreateDidInput(
    val method: String,
    val keyInfo: KeyInfo<KeyType>,
    val didAlias: String? = null,
    val controllers: List<String>? = null,
    val alsoKnownAs: List<String>? = null,
    val options: Map<String, String> = emptyMap(),
)

/** Composite input wrapping a `did` path param + an [DidUpdateOptions] body. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UpdateDidInput", exact = true)
@Serializable
data class UpdateDidInput(
    val did: String,
    val options: DidUpdateOptions,
)

/**
 * Wire payload for the declarative PUT /dids/{did} replace operation. Every listed collection
 * is fully replaced; verification methods + key mappings are intentionally excluded — they
 * must be mutated via their dedicated sub-resource endpoints to keep KMS-lifecycle invariants
 * intact.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReplaceDidBody", exact = true)
@Serializable
data class ReplaceDidBody(
    val alias: String? = null,
    val controllers: List<String> = emptyList(),
    val alsoKnownAs: List<String> = emptyList(),
    val equivalentIds: List<String> = emptyList(),
    val contexts: List<String> = emptyList(),
    val canonicalId: String? = null,
    val deactivated: Boolean? = null,
    val services: List<DidService> = emptyList(),
    val relationships: List<ReplaceRelationshipBodyEntry> = emptyList(),
)

/**
 * One entry in [ReplaceDidBody.relationships]. Exactly one of [verificationMethodId] (embed by
 * reference into a local VM row) or [referenceDidUrl] (point at an external DID URL) must be
 * set; both-null and both-set are rejected by the service command.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReplaceRelationshipBodyEntry", exact = true)
@Serializable
data class ReplaceRelationshipBodyEntry(
    val purpose: String,
    val verificationMethodId: String? = null,
    val referenceDidUrl: String? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ReplaceDidInput", exact = true)
@Serializable
data class ReplaceDidInput(
    val did: String,
    val body: ReplaceDidBody,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeactivateDidInput", exact = true)
@Serializable
data class DeactivateDidInput(
    val did: String,
    val options: DidDeactivateOptions = DidDeactivateOptions(),
)

/**
 * Wire payload for POST `/dids/{did}/actions/deactivate`. Mirrors the OpenAPI
 * `DeactivateDidRequest` schema: an optional `options` envelope carrying
 * method-specific [DidDeactivateOptions].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeactivateDidRequest", exact = true)
@Serializable
data class DeactivateDidRequest(
    val options: DidDeactivateOptions? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveDidInput", exact = true)
@Serializable
data class ResolveDidInput(
    val did: String,
    val accept: String? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("TrackExternalDidInput", exact = true)
@Serializable
data class TrackExternalDidInput(
    val did: String,
    val alias: String? = null,
)

/**
 * Wire shape for a single DID. Mirrors the OpenAPI `Did` schema in
 * `did-manager-components.yml` and is the response type of `GetDidServiceCommand`
 * and the item type of `ListDidsOutput.items`.
 *
 * Default projection includes the lightweight summary fields. The optional [document]
 * and [keys] projections are populated only when the request opted in via `?expand=`
 * (translated to a [DidExpand] set on the service-command input). Internal SDK fields
 * present on [ManagedDid] (`id`, `deactivated`) are intentionally dropped on the way
 * to the wire — `deactivated` is DID-document state accessible via the document, and
 * `id` is an internal join column.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Did", exact = true)
@Serializable
data class Did(
    val did: String,
    val method: String,
    val alias: String? = null,
    val role: DidRole,
    val alsoKnownAs: List<String> = emptyList(),
    val equivalentIds: List<String> = emptyList(),
    val canonicalId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    // Optional projections — populated only when ?expand requests them.
    val document: DidDocument? = null,
    /**
     * Inline list of KMS key mappings for this DID's managed verification methods.
     * Populated only when the request used `?expand=keys` (or `?expand=all`); `null`
     * otherwise. Items use the shared [KeyMappingResponse] wire shape — the SAME shape
     * the dedicated `/dids/{did}/key-mappings` endpoints return — so a client only has
     * to model `KeyMapping` once. The SDK's storage-layer `DidKeyMapping` (with its
     * `purposesJson` string column and raw `kms*` fields) is intentionally NOT exposed
     * on the wire; the projection happens in [com.sphereon.did.manager.impl.command.toServiceResponse].
     */
    val keys: List<KeyMappingResponse>? = null,
)

/**
 * Canonical pagination metadata, mirroring the OpenAPI `common-components.PageMeta` schema and the
 * `pagination` object emitted by `ResponseBuilder.paginated()`. The `limit`/`offset`/`total`/
 * `hasMore` fields are the stable legacy fields; `page`/`size`/`totalPages` are the additive
 * unified fields.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PageMeta", exact = true)
@Serializable
data class PageMeta(
    val limit: Int,
    val offset: Int,
    val page: Int,
    val size: Int,
    val total: Int,
    val totalPages: Int,
    val hasMore: Boolean,
)

/**
 * Output wrapper for `ListDidsServiceCommand`. Wire-shape matches the OpenAPI `DidListResponse`
 * schema: `{ items, page }`, where items are wire-shape [Did] and `page` is the [PageMeta]
 * envelope.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListDidsOutput", exact = true)
@Serializable
data class ListDidsOutput(
    val items: List<Did>,
    val page: PageMeta,
)

/**
 * Input for `GetDidServiceCommand`. Distinct from [DidIdInput] (used by
 * delete/deactivate/etc.) because get supports an additional [expand] selector for
 * optional projections on the returned [Did].
 *
 * @property expand Comma-separated `?expand=` query value (e.g. `document,keys`/`all`),
 *                  parsed by the impl into a [DidExpand] set.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetDidInput", exact = true)
@Serializable
data class GetDidInput(
    val did: String,
    val expand: String? = null,
)

/**
 * Parses a raw `?expand=` query value (comma-separated, e.g. `document,keys` or `all`)
 * into a typed projection set. Null/blank → empty set (default lightweight projection).
 * Unknown values fail-fast with `ILLEGAL_ARGUMENT_ERROR` so the dispatcher renders 400.
 */
fun parseExpand(raw: String?): IdkResult<Set<DidExpand>, IdkError> {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return Ok(emptySet())
    val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val expansions = mutableSetOf<DidExpand>()
    for (token in tokens) {
        when (token.lowercase()) {
            "all" -> {
                expansions += DidExpand.entries
            }

            "document" -> {
                expansions += DidExpand.DOCUMENT
            }

            "keys" -> {
                expansions += DidExpand.KEYS
            }

            else -> {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Invalid value for query parameter 'expand': '$token'. " +
                                "Expected a comma-separated subset of [all, document, keys].",
                    ),
                )
            }
        }
    }
    return Ok(expansions)
}

/**
 * Maps a [ManagedDid] (SDK aggregate) to the wire-shape [Did].
 *
 * The optional [Did.document] and [Did.keys] projections are populated only when [expand]
 * requests them. The wire-shape fields [Did.alsoKnownAs], [Did.equivalentIds],
 * [Did.canonicalId] and [Did.deletedAt] are not present on [ManagedDid] and remain at their
 * defaults (empty / null) here — callers that need them must look up the underlying
 * `DidDetail` aggregate.
 */
fun ManagedDid.toWire(expand: Set<DidExpand>): Did =
    Did(
        did = did,
        method = method,
        alias = alias,
        role = role,
        createdAt = createdAt,
        updatedAt = updatedAt,
        document = if (DidExpand.DOCUMENT in expand) document else null,
        keys = if (DidExpand.KEYS in expand) keys.map { it.toServiceResponse() } else null,
    )

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteDidOutput", exact = true)
@Serializable
data class DeleteDidOutput(
    val did: String,
    val deleted: Boolean = true,
)

// ===== Verification-method DTOs =====

/**
 * Wire payload for POST /dids/{did}/verification/methods. The IDK-19 inversion makes the VM
 * own its relationship membership: [valueVerificationRelation] (at most one purpose to embed
 * this VM by value into) and [referenceVerificationRelations] (purposes that reference the
 * VM by DID URL).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerificationMethodCreateBody", exact = true)
@Serializable
data class VerificationMethodCreateBody(
    val id: String? = null,
    val type: String,
    val controller: String,
    val keyInfo: KeyInfo<KeyType>,
    val purposes: List<VerificationPurpose> = emptyList(),
    val valueVerificationRelation: VerificationPurpose? = null,
    val referenceVerificationRelations: List<VerificationPurpose> = emptyList(),
    val extensionProperties: JsonObject? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateVerificationMethodInput", exact = true)
@Serializable
data class CreateVerificationMethodInput(
    val did: String,
    val body: VerificationMethodCreateBody,
)

/**
 * Wire payload for PATCH /dids/{did}/verification/methods/{vm}. All fields nullable. The
 * caller passes the raw [JsonObject] so the impl can distinguish "field absent" (preserve)
 * from "field present and null" (clear) per the JSON Merge Patch contract — kotlinx
 * decoding alone collapses both to `null` and silently ignores explicit clears.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UpdateVerificationMethodInput", exact = true)
@Serializable
data class UpdateVerificationMethodInput(
    val did: String,
    val methodId: String,
    val rawBody: JsonObject,
)

/**
 * Wire response for a single VM. Mirrors [VerificationMethodCreateBody] plus the
 * server-assigned [ordinal] (position within the source `verificationMethod[]` array).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerificationMethodResponse", exact = true)
@Serializable
data class VerificationMethodResponse(
    val id: String,
    val type: String,
    val controller: String,
    val keyInfo: KeyInfo<KeyType>? = null,
    val expiresAt: Instant? = null,
    val revokedAt: Instant? = null,
    val valueVerificationRelation: VerificationPurpose? = null,
    val referenceVerificationRelations: List<VerificationPurpose> = emptyList(),
    val extensionProperties: JsonObject? = null,
    val ordinal: Int,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("VerificationMethodListResponse", exact = true)
@Serializable
data class VerificationMethodListResponse(
    val items: List<VerificationMethodResponse>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetVerificationMethodInput", exact = true)
@Serializable
data class GetVerificationMethodInput(
    val did: String,
    val methodId: String,
)

/**
 * Dedicated input for [RemoveVerificationMethodServiceCommand]. Same shape as
 * [GetVerificationMethodInput] today, but the type distinction is intentional: a code reader
 * (or tool that wires inputs to commands by type) needs to see remove-vs-get semantics in
 * the type. Filed as a separate data class instead of an alias so future remove-only options
 * can land here without expanding Get's surface.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveVerificationMethodInput", exact = true)
@Serializable
data class RemoveVerificationMethodInput(
    val did: String,
    val methodId: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteVerificationMethodOutput", exact = true)
@Serializable
data class DeleteVerificationMethodOutput(
    val did: String,
    val methodId: String,
    val deleted: Boolean = true,
)

// ===== Verification-relationship DTOs =====

/**
 * Read-only per-purpose view of a verification relationship, derived from VM rows on the DID.
 * Wire shape for GET /dids/{did}/verification/relationships after the IDK-19 inversion
 * (relationships are no longer first-class persistence rows).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerificationRelationshipView", exact = true)
@Serializable
data class VerificationRelationshipView(
    val purpose: VerificationPurpose,
    val embeddedVerificationMethodId: String? = null,
    val referenceVerificationMethodIds: List<String> = emptyList(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("VerificationRelationshipListResponse", exact = true)
@Serializable
data class VerificationRelationshipListResponse(
    val items: List<VerificationRelationshipView>,
)

// ===== DID service DTOs =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("DidServiceListResponse", exact = true)
@Serializable
data class DidServiceListResponse(
    val items: List<DidService>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateDidServiceInput", exact = true)
@Serializable
data class CreateDidServiceInput(
    val did: String,
    val service: DidService,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetDidServiceInput", exact = true)
@Serializable
data class GetDidServiceInput(
    val did: String,
    val serviceId: String,
)

/**
 * Dedicated input for [RemoveDidServiceServiceCommand]. Same shape as [GetDidServiceInput]
 * today; introduced as a distinct type so remove-vs-get semantics are visible in the type
 * system and so future remove-only options can land here without expanding Get's surface.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveDidServiceInput", exact = true)
@Serializable
data class RemoveDidServiceInput(
    val did: String,
    val serviceId: String,
)

/**
 * Wire payload for PATCH /dids/{did}/services/{serviceId} — JSON Merge Patch shape per the
 * OpenAPI `DidServiceUpdateRequest` schema. Both fields are nullable so callers send only
 * what they want changed; absent fields preserve the existing value.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidServiceUpdateBody", exact = true)
@Serializable
data class DidServiceUpdateBody(
    val type: JsonElement? = null,
    val serviceEndpoint: JsonElement? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("UpdateDidServiceInput", exact = true)
@Serializable
data class UpdateDidServiceInput(
    val did: String,
    val serviceId: String,
    val body: DidServiceUpdateBody,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteDidServiceOutput", exact = true)
@Serializable
data class DeleteDidServiceOutput(
    val did: String,
    val serviceId: String,
    val deleted: Boolean = true,
)

// ===== Key mapping DTOs =====

/** Wire payload for POST /dids/{did}/keymappings. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyMappingCreateBody", exact = true)
@Serializable
data class KeyMappingCreateBody(
    val verificationMethod: String,
    val keyInfo: KeyInfo<KeyType>,
    val purposes: List<VerificationPurpose> = emptyList(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateKeyMappingInput", exact = true)
@Serializable
data class CreateKeyMappingInput(
    val did: String,
    val body: KeyMappingCreateBody,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyMappingResponse", exact = true)
@Serializable
data class KeyMappingResponse(
    val id: String,
    val verificationMethod: String,
    /**
     * KMS key reference. `providerId` and one of `alias`/`kid` are always populated
     * post-creation. The optional `keyType`, `signatureAlgorithm`, and `keyEncoding`
     * fields are resolved from the keyref-store entry pinned by
     * `(tenantId, providerId, alias|kid)` — null when the keyref-store binding is the
     * no-op stub (VDX-infra-otp not yet wired) or when the lookup record carries no
     * such metadata.
     */
    val keyInfo: KeyInfo<KeyType>,
    val purposes: List<VerificationPurpose>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyMappingListResponse", exact = true)
@Serializable
data class KeyMappingListResponse(
    val items: List<KeyMappingResponse>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyMappingInput", exact = true)
@Serializable
data class DeleteKeyMappingInput(
    val did: String,
    val mappingId: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteKeyMappingOutput", exact = true)
@Serializable
data class DeleteKeyMappingOutput(
    val mappingId: String,
    val deleted: Boolean = true,
)

// ===== Controllers / AlsoKnownAs / EquivalentIds =====

/** Generic single-string-value request body (controllers, AKAs, equivalent ids). */
@OptIn(ExperimentalObjCName::class)
@ObjCName("StringValueBody", exact = true)
@Serializable
data class StringValueBody(
    val value: String,
)

/**
 * Wire view of a controller entry. Exposes only public-facing fields — the persistence
 * `didRecordId` and `*ById` audit columns are intentionally stripped from the wire shape
 * (those are internal joins / audit detail surfaced through dedicated audit endpoints).
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidControllerView", exact = true)
@Serializable
data class DidControllerView(
    val id: String,
    val controllerDid: String,
    val ordinal: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ControllerListResponse", exact = true)
@Serializable
data class ControllerListResponse(
    val items: List<DidControllerView>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateControllerInput", exact = true)
@Serializable
data class CreateControllerInput(
    val did: String,
    val body: StringValueBody,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteControllerInput", exact = true)
@Serializable
data class DeleteControllerInput(
    /** Parent DID — required so the service command can verify the controller belongs to it. */
    val did: String,
    val controllerId: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteControllerOutput", exact = true)
@Serializable
data class DeleteControllerOutput(
    val controllerId: String,
    val deleted: Boolean = true,
)

/**
 * Wire view of an alsoKnownAs entry. Same scrubbing as [DidControllerView] —
 * `didRecordId` and audit columns are stripped from the wire shape.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidAlsoKnownAsView", exact = true)
@Serializable
data class DidAlsoKnownAsView(
    val id: String,
    val akaUri: String,
    val ordinal: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("AlsoKnownAsListResponse", exact = true)
@Serializable
data class AlsoKnownAsListResponse(
    val items: List<DidAlsoKnownAsView>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAlsoKnownAsInput", exact = true)
@Serializable
data class CreateAlsoKnownAsInput(
    val did: String,
    val body: StringValueBody,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteAlsoKnownAsInput", exact = true)
@Serializable
data class DeleteAlsoKnownAsInput(
    /** Parent DID — required so the service command can verify the AKA entry belongs to it. */
    val did: String,
    val akaId: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteAlsoKnownAsOutput", exact = true)
@Serializable
data class DeleteAlsoKnownAsOutput(
    val akaId: String,
    val deleted: Boolean = true,
)

/**
 * Wire view of an equivalentId entry. Same scrubbing as [DidControllerView].
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidEquivalentIdView", exact = true)
@Serializable
data class DidEquivalentIdView(
    val id: String,
    val equivalentDid: String,
    val ordinal: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("EquivalentIdListResponse", exact = true)
@Serializable
data class EquivalentIdListResponse(
    val items: List<DidEquivalentIdView>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateEquivalentIdInput", exact = true)
@Serializable
data class CreateEquivalentIdInput(
    val did: String,
    val body: StringValueBody,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteEquivalentIdInput", exact = true)
@Serializable
data class DeleteEquivalentIdInput(
    /** Parent DID — required so the service command can verify the equivalent-id row belongs to it. */
    val did: String,
    val equivalentIdRowId: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteEquivalentIdOutput", exact = true)
@Serializable
data class DeleteEquivalentIdOutput(
    val equivalentIdRowId: String,
    val deleted: Boolean = true,
)

// ===== Service Command interfaces =====

// ===== DID lifecycle =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateDidServiceCommand", exact = true)
interface CreateDidServiceCommand : ServiceCommand<CreateDidInput, ManagedDid, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.create"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListDidsServiceCommand", exact = true)
interface ListDidsServiceCommand : ServiceCommand<DidFilter, ListDidsOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("TrackExternalDidServiceCommand", exact = true)
interface TrackExternalDidServiceCommand : ServiceCommand<TrackExternalDidInput, ManagedDid, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.track-external"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetDidServiceCommand", exact = true)
interface GetDidServiceCommand : ServiceCommand<GetDidInput, Did, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.get"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("UpdateDidServiceCommand", exact = true)
interface UpdateDidServiceCommand : ServiceCommand<UpdateDidInput, ManagedDid, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.update"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ReplaceDidServiceCommand", exact = true)
interface ReplaceDidServiceCommand : ServiceCommand<ReplaceDidInput, ManagedDid, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.replace"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeleteDidServiceCommand", exact = true)
interface DeleteDidServiceCommand : ServiceCommand<DidIdInput, DeleteDidOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.delete"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeactivateDidServiceCommand", exact = true)
interface DeactivateDidServiceCommand : ServiceCommand<DeactivateDidInput, ManagedDid, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.deactivate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveDidServiceCommand", exact = true)
interface ResolveDidServiceCommand : ServiceCommand<ResolveDidInput, DidResolutionResult, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.dids.resolve"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

// ===== Verification methods =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListVerificationMethodsServiceCommand", exact = true)
interface ListVerificationMethodsServiceCommand : ServiceCommand<DidIdInput, VerificationMethodListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-methods.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddVerificationMethodServiceCommand", exact = true)
interface AddVerificationMethodServiceCommand : ServiceCommand<CreateVerificationMethodInput, VerificationMethodResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-methods.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetVerificationMethodServiceCommand", exact = true)
interface GetVerificationMethodServiceCommand : ServiceCommand<GetVerificationMethodInput, VerificationMethodResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-methods.get"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("UpdateVerificationMethodServiceCommand", exact = true)
interface UpdateVerificationMethodServiceCommand : ServiceCommand<UpdateVerificationMethodInput, VerificationMethodResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-methods.update"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveVerificationMethodServiceCommand", exact = true)
interface RemoveVerificationMethodServiceCommand : ServiceCommand<GetVerificationMethodInput, DeleteVerificationMethodOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-methods.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Verification relationships =====

/**
 * Input for [ListVerificationRelationshipsServiceCommand].
 *
 * @property did   the DID whose relationships to list
 * @property purpose optional purpose filter (e.g. `authentication`); `null` lists all purposes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListVerificationRelationshipsInput", exact = true)
@Serializable
data class ListVerificationRelationshipsInput(
    val did: String,
    val purpose: VerificationPurpose? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListVerificationRelationshipsServiceCommand", exact = true)
interface ListVerificationRelationshipsServiceCommand : ServiceCommand<ListVerificationRelationshipsInput, VerificationRelationshipListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-relationships.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

// ===== Services =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListDidServicesServiceCommand", exact = true)
interface ListDidServicesServiceCommand : ServiceCommand<DidIdInput, DidServiceListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.services.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddDidServiceServiceCommand", exact = true)
interface AddDidServiceServiceCommand : ServiceCommand<CreateDidServiceInput, DidService, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.services.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetDidServiceServiceCommand", exact = true)
interface GetDidServiceServiceCommand : ServiceCommand<GetDidServiceInput, DidService, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.services.get"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("UpdateDidServiceServiceCommand", exact = true)
interface UpdateDidServiceServiceCommand : ServiceCommand<UpdateDidServiceInput, DidService, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.services.update"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.UPDATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveDidServiceServiceCommand", exact = true)
interface RemoveDidServiceServiceCommand : ServiceCommand<GetDidServiceInput, DeleteDidServiceOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.services.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Key mappings =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListKeyMappingsServiceCommand", exact = true)
interface ListKeyMappingsServiceCommand : ServiceCommand<DidIdInput, KeyMappingListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.key-mappings.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddKeyMappingServiceCommand", exact = true)
interface AddKeyMappingServiceCommand : ServiceCommand<CreateKeyMappingInput, KeyMappingResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.key-mappings.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveKeyMappingServiceCommand", exact = true)
interface RemoveKeyMappingServiceCommand : ServiceCommand<DeleteKeyMappingInput, DeleteKeyMappingOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.key-mappings.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Controllers =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListControllersServiceCommand", exact = true)
interface ListControllersServiceCommand : ServiceCommand<DidIdInput, ControllerListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.controllers.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddControllerServiceCommand", exact = true)
interface AddControllerServiceCommand : ServiceCommand<CreateControllerInput, DidControllerView, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.controllers.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveControllerServiceCommand", exact = true)
interface RemoveControllerServiceCommand : ServiceCommand<DeleteControllerInput, DeleteControllerOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.controllers.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== AlsoKnownAs =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListAlsoKnownAsServiceCommand", exact = true)
interface ListAlsoKnownAsServiceCommand : ServiceCommand<DidIdInput, AlsoKnownAsListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.also-known-as.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddAlsoKnownAsServiceCommand", exact = true)
interface AddAlsoKnownAsServiceCommand : ServiceCommand<CreateAlsoKnownAsInput, DidAlsoKnownAsView, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.also-known-as.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveAlsoKnownAsServiceCommand", exact = true)
interface RemoveAlsoKnownAsServiceCommand : ServiceCommand<DeleteAlsoKnownAsInput, DeleteAlsoKnownAsOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.also-known-as.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Equivalent identifiers =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListEquivalentIdsServiceCommand", exact = true)
interface ListEquivalentIdsServiceCommand : ServiceCommand<DidIdInput, EquivalentIdListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.equivalent-ids.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddEquivalentIdServiceCommand", exact = true)
interface AddEquivalentIdServiceCommand : ServiceCommand<CreateEquivalentIdInput, DidEquivalentIdView, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.equivalent-ids.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveEquivalentIdServiceCommand", exact = true)
interface RemoveEquivalentIdServiceCommand : ServiceCommand<DeleteEquivalentIdInput, DeleteEquivalentIdOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.equivalent-ids.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Document cache =====

/**
 * Returns the cached resolved document for the DID if the cache is fresh; if no fresh entry
 * exists the command surfaces a `NOT_FOUND_ERROR` so callers can decide whether to fall back
 * to [ResolveAndCacheDidServiceCommand]. Delegates to `DidManager.getCachedDocument()` — no
 * resolver work is performed by this command.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetCachedDidDocumentServiceCommand", exact = true)
interface GetCachedDidDocumentServiceCommand : ServiceCommand<DidIdInput, DidDocument, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.documents.get-cached"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

/**
 * Resolves the DID (managed or external) and stores the result in the document cache,
 * returning the resolved document. Existing cache entries are overwritten. Delegates to
 * `DidManager.resolveAndCache()`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveAndCacheDidServiceCommand", exact = true)
interface ResolveAndCacheDidServiceCommand : ServiceCommand<DidIdInput, DidDocument, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.documents.resolve-and-cache"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE
}

// ===== Verification relationship mutation DTOs =====

/**
 * Wire payload for `POST /dids/{did}/verification-relationships`.
 *
 * The relationship is by default added in *referenced* form (`did:…#fragment` link). Setting
 * [embed] to `true` creates the relationship in *embedded* form — the persistence row carries
 * `entryEmbeddedVmId` pointing at the local VM UUID, matching the DID-Core inline VM idiom for
 * relationships that should serialize as a full VM object instead of a URL reference.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AddVerificationRelationshipBody", exact = true)
@Serializable
data class AddVerificationRelationshipBody(
    val verificationMethodId: String,
    val purpose: VerificationPurpose,
    val embed: Boolean = false,
)

/** Composite input for AddVerificationRelationshipServiceCommand. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AddVerificationRelationshipInput", exact = true)
@Serializable
data class AddVerificationRelationshipInput(
    val did: String,
    val body: AddVerificationRelationshipBody,
)

/** Composite input for RemoveVerificationRelationshipServiceCommand. */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveVerificationRelationshipInput", exact = true)
@Serializable
data class RemoveVerificationRelationshipInput(
    val did: String,
    val relationshipId: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveVerificationRelationshipOutput", exact = true)
@Serializable
data class RemoveVerificationRelationshipOutput(
    val did: String,
    val relationshipId: String,
    val deleted: Boolean = true,
)

// ===== Capability DTOs =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("MethodInput", exact = true)
@Serializable
data class MethodInput(
    val method: String,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("MethodCapabilityListResponse", exact = true)
@Serializable
data class MethodCapabilityListResponse(
    val items: List<DidMethodCapabilities>,
)

// ===== Verification relationship commands =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("AddVerificationRelationshipServiceCommand", exact = true)
interface AddVerificationRelationshipServiceCommand : ServiceCommand<AddVerificationRelationshipInput, VerificationRelationship, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-relationships.add"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RemoveVerificationRelationshipServiceCommand", exact = true)
interface RemoveVerificationRelationshipServiceCommand : ServiceCommand<RemoveVerificationRelationshipInput, RemoveVerificationRelationshipOutput, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.verification-relationships.remove"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Document cache invalidation =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("InvalidateDidDocumentServiceCommand", exact = true)
interface InvalidateDidDocumentServiceCommand : ServiceCommand<DidIdInput, Unit, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.documents.invalidate"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.DELETE
}

// ===== Capability commands =====

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListSupportedMethodsServiceCommand", exact = true)
interface ListSupportedMethodsServiceCommand : ServiceCommand<Unit, MethodCapabilityListResponse, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.methods.list"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetMethodCapabilitiesServiceCommand", exact = true)
interface GetMethodCapabilitiesServiceCommand : ServiceCommand<MethodInput, DidMethodCapabilities, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.method-capabilities.get"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetMethodCapabilitySummaryServiceCommand", exact = true)
interface GetMethodCapabilitySummaryServiceCommand : ServiceCommand<MethodInput, MethodCapabilitySummary, IdkError> {
    companion object {
        const val COMMAND_ID = "did-manager.method-capabilities.summary"
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
}
