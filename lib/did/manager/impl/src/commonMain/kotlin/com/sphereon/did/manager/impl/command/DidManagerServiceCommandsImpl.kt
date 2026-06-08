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

package com.sphereon.did.manager.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.AddKeyMappingInput
import com.sphereon.did.manager.DidAggregateReplacement
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.manager.DidRelationshipReplacement
import com.sphereon.did.manager.KeyInfoPatch
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.manager.PatchValue
import com.sphereon.did.manager.ServicePatch
import com.sphereon.did.manager.VerificationMethodPatch
import com.sphereon.did.manager.command.AddAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.AddControllerServiceCommand
import com.sphereon.did.manager.command.AddDidServiceServiceCommand
import com.sphereon.did.manager.command.AddEquivalentIdServiceCommand
import com.sphereon.did.manager.command.AddKeyMappingServiceCommand
import com.sphereon.did.manager.command.AddVerificationMethodServiceCommand
import com.sphereon.did.manager.command.AddVerificationRelationshipInput
import com.sphereon.did.manager.command.AddVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.AlsoKnownAsListResponse
import com.sphereon.did.manager.command.ControllerListResponse
import com.sphereon.did.manager.command.CreateAlsoKnownAsInput
import com.sphereon.did.manager.command.CreateControllerInput
import com.sphereon.did.manager.command.CreateDidInput
import com.sphereon.did.manager.command.CreateDidServiceCommand
import com.sphereon.did.manager.command.CreateDidServiceInput
import com.sphereon.did.manager.command.CreateEquivalentIdInput
import com.sphereon.did.manager.command.CreateKeyMappingInput
import com.sphereon.did.manager.command.CreateVerificationMethodInput
import com.sphereon.did.manager.command.DeactivateDidInput
import com.sphereon.did.manager.command.DeactivateDidServiceCommand
import com.sphereon.did.manager.command.DeleteAlsoKnownAsInput
import com.sphereon.did.manager.command.DeleteAlsoKnownAsOutput
import com.sphereon.did.manager.command.DeleteControllerInput
import com.sphereon.did.manager.command.DeleteControllerOutput
import com.sphereon.did.manager.command.DeleteDidOutput
import com.sphereon.did.manager.command.DeleteDidServiceCommand
import com.sphereon.did.manager.command.DeleteDidServiceOutput
import com.sphereon.did.manager.command.DeleteEquivalentIdInput
import com.sphereon.did.manager.command.DeleteEquivalentIdOutput
import com.sphereon.did.manager.command.DeleteKeyMappingInput
import com.sphereon.did.manager.command.DeleteKeyMappingOutput
import com.sphereon.did.manager.command.DeleteVerificationMethodOutput
import com.sphereon.did.manager.command.Did
import com.sphereon.did.manager.command.DidAlsoKnownAsView
import com.sphereon.did.manager.command.DidControllerView
import com.sphereon.did.manager.command.DidEquivalentIdView
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.DidServiceListResponse
import com.sphereon.did.manager.command.DidServiceUpdateBody
import com.sphereon.did.manager.command.EquivalentIdListResponse
import com.sphereon.did.manager.command.GetCachedDidDocumentServiceCommand
import com.sphereon.did.manager.command.GetDidInput
import com.sphereon.did.manager.command.GetDidServiceCommand
import com.sphereon.did.manager.command.GetDidServiceInput
import com.sphereon.did.manager.command.GetDidServiceServiceCommand
import com.sphereon.did.manager.command.GetMethodCapabilitiesServiceCommand
import com.sphereon.did.manager.command.GetMethodCapabilitySummaryServiceCommand
import com.sphereon.did.manager.command.GetVerificationMethodInput
import com.sphereon.did.manager.command.GetVerificationMethodServiceCommand
import com.sphereon.did.manager.command.InvalidateDidDocumentServiceCommand
import com.sphereon.did.manager.command.KeyMappingCreateBody
import com.sphereon.did.manager.command.KeyMappingListResponse
import com.sphereon.did.manager.command.KeyMappingResponse
import com.sphereon.did.manager.command.ListAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.ListControllersServiceCommand
import com.sphereon.did.manager.command.ListDidServicesServiceCommand
import com.sphereon.did.manager.command.ListDidsOutput
import com.sphereon.did.manager.command.ListDidsServiceCommand
import com.sphereon.did.manager.command.ListEquivalentIdsServiceCommand
import com.sphereon.did.manager.command.ListKeyMappingsServiceCommand
import com.sphereon.did.manager.command.ListSupportedMethodsServiceCommand
import com.sphereon.did.manager.command.ListVerificationMethodsServiceCommand
import com.sphereon.did.manager.command.ListVerificationRelationshipsInput
import com.sphereon.did.manager.command.ListVerificationRelationshipsServiceCommand
import com.sphereon.did.manager.command.MethodCapabilityListResponse
import com.sphereon.did.manager.command.MethodInput
import com.sphereon.did.manager.command.PageMeta
import com.sphereon.did.manager.command.RemoveAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.RemoveControllerServiceCommand
import com.sphereon.did.manager.command.RemoveDidServiceServiceCommand
import com.sphereon.did.manager.command.RemoveEquivalentIdServiceCommand
import com.sphereon.did.manager.command.RemoveKeyMappingServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationMethodServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationRelationshipInput
import com.sphereon.did.manager.command.RemoveVerificationRelationshipOutput
import com.sphereon.did.manager.command.RemoveVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.ReplaceDidBody
import com.sphereon.did.manager.command.ReplaceDidInput
import com.sphereon.did.manager.command.ReplaceDidServiceCommand
import com.sphereon.did.manager.command.ResolveAndCacheDidServiceCommand
import com.sphereon.did.manager.command.ResolveDidInput
import com.sphereon.did.manager.command.ResolveDidServiceCommand
import com.sphereon.did.manager.command.TrackExternalDidInput
import com.sphereon.did.manager.command.TrackExternalDidServiceCommand
import com.sphereon.did.manager.command.UpdateDidInput
import com.sphereon.did.manager.command.UpdateDidServiceCommand
import com.sphereon.did.manager.command.UpdateDidServiceInput
import com.sphereon.did.manager.command.UpdateDidServiceServiceCommand
import com.sphereon.did.manager.command.UpdateVerificationMethodInput
import com.sphereon.did.manager.command.UpdateVerificationMethodServiceCommand
import com.sphereon.did.manager.command.VerificationMethodListResponse
import com.sphereon.did.manager.command.VerificationMethodResponse
import com.sphereon.did.manager.command.VerificationRelationshipListResponse
import com.sphereon.did.manager.command.VerificationRelationshipView
import com.sphereon.did.manager.command.parseExpand
import com.sphereon.did.manager.command.toWire
import com.sphereon.did.manager.impl.requireCapability
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.persistence.DidAlsoKnownAsRecord
import com.sphereon.did.persistence.DidControllerRecord
import com.sphereon.did.persistence.DidDetail
import com.sphereon.did.persistence.DidDocumentContextRecord
import com.sphereon.did.persistence.DidEquivalentIdRecord
import com.sphereon.did.persistence.DidKeyMappingRecord
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.DidServiceRecord
import com.sphereon.did.persistence.DidVerificationMethodRecord
import com.sphereon.did.persistence.DidVerificationRelationshipRecord
import com.sphereon.did.persistence.Uuid4Generator
import com.sphereon.did.persistence.toDidRecordFilter
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolverRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock

/*
 * Service-command implementations for the DID Manager REST surface.
 *
 * Each impl contains the actual business logic that the matching HTTP endpoint command
 * delegates to. Pure DID-record CRUD operations talk to [DidRepository] directly;
 * provider-aware operations (VM add/remove, service add/remove, lifecycle) go through
 * [DidManager]; document refresh / resolve use [DidResolverRegistry].
 *
 * The HTTP endpoint commands hold these as injected collaborators; they handle request
 * parsing and response serialization only.
 */

// `internal` so the sibling `DidSubResourceServiceCommandsImpl.kt` file (split out under
// VDX-infra-u3l) can share this configured Json instance — both files live in the same
// package and module so internal visibility is sufficient.
internal val serviceJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

// ===== Internal helpers shared across impls =====

/** Resolves a DID string to its `did_record.id` UUID, returning NOT_FOUND when unknown. */
private suspend fun DidRepository.requireDidRecordId(
    tenantId: String,
    did: String,
): IdkResult<String, IdkError> {
    val detail =
        findByDid(tenantId, did).getOrElse { return Err(it) }
            ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
    return Ok(detail.record.id)
}

/** Decodes a stored `purposesJson`/`inlineInJson` array column. */
internal fun String?.toServicePurposeList(): List<VerificationPurpose> =
    if (isNullOrBlank()) {
        emptyList()
    } else {
        (serviceJson.parseToJsonElement(this) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(VerificationPurpose::fromValue) }
            .orEmpty()
    }

/** Decodes a stored extension-properties JSON column to a `JsonObject`, or `null` when blank. */
internal fun String?.toServiceJsonObjectOrNull(): JsonObject? =
    if (isNullOrBlank()) {
        null
    } else {
        serviceJson.parseToJsonElement(this) as? JsonObject
    }

/** Encodes an extension-properties bag for storage. Empty bag becomes `null` (never `"{}"`). */
internal fun JsonObject?.toServiceStorageJsonOrNull(): String? =
    this?.takeIf { it.isNotEmpty() }?.let {
        serviceJson.encodeToString(JsonObject.serializer(), it)
    }

/** Expands a relative DID URL fragment (`#frag`) to its absolute form (`did#frag`). */
internal fun normalizeServiceDidUrl(
    did: String,
    value: String,
): String = if (value.startsWith("#")) did + value else value

/** Projects a persistence VM row into the wire [VerificationMethodResponse]. */
internal fun DidVerificationMethodRecord.toServiceResponse(detail: DidDetail): VerificationMethodResponse {
    val embeddedPurpose = inlineInJson.toServicePurposeList().firstOrNull()
    val referencePurposes =
        detail.verificationRelationship
            .filter { rel -> rel.entryRefDidUrl?.let { normalizeServiceDidUrl(detail.record.did, it) } == vmId }
            .mapNotNull { VerificationPurpose.fromValue(it.purpose) }
    return VerificationMethodResponse(
        id = vmId,
        type = type,
        controller = controller,
        keyInfo =
            com.sphereon.crypto.core.KeyInfo<com.sphereon.crypto.core.KeyType>(
                providerId = kmsProviderId,
                alias = kmsKeyAlias,
                kid = kmsKid,
            ),
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        valueVerificationRelation = embeddedPurpose,
        referenceVerificationRelations = referencePurposes,
        extensionProperties = extensionPropertiesJson.toServiceJsonObjectOrNull(),
        ordinal = ordinal,
    )
}

/** Folds the per-purpose relationship rows of an aggregate into the read-only views. */
internal fun DidDetail.toServiceRelationshipViews(): List<VerificationRelationshipView> {
    val vmByUuid = verificationMethod.associateBy { it.id }
    return VerificationPurpose.ALL.mapNotNull { purpose ->
        val rows = verificationRelationship.filter { it.purpose == purpose.value }.sortedBy { it.ordinal }
        if (rows.isEmpty()) return@mapNotNull null
        VerificationRelationshipView(
            purpose = purpose,
            embeddedVerificationMethodId =
                rows.firstNotNullOfOrNull { rel ->
                    rel.entryEmbeddedVmId?.let { vmByUuid[it]?.vmId ?: it }
                },
            referenceVerificationMethodIds = rows.mapNotNull { rel -> rel.entryRefDidUrl },
        )
    }
}

/** Projects a persistence service row into the wire [DidService] shape. */
internal fun DidServiceRecord.toServiceWireService(): DidService =
    DidService(
        id = serviceId,
        type = serviceJson.decodeFromString(ListSerializer(String.serializer()), typeJson),
        serviceEndpoint = serviceJson.decodeFromString(JsonElement.serializer(), serviceEndpointJson),
        extensions = extensionPropertiesJson.toServiceJsonObjectOrNull()?.toMap().orEmpty(),
    )

/**
 * Projects a persistence key-mapping row into the wire [KeyMappingResponse] shape.
 *
 * IDK-18 o77 split the verification-method reference into two columns:
 * `verificationMethodId` is the local VM record UUID (FK target) and
 * `verificationMethodDidUrl` is the wire DID URL. Wire responses surface the URL
 * form so internal UUIDs are never exposed; the fallback to the UUID column only
 * kicks in if a row was written before o77 landed (legacy data).
 */
internal fun DidKeyMappingRecord.toServiceResponse(keyref: com.sphereon.crypto.key.persistence.KeyReferenceRecord? = null,): KeyMappingResponse =
    KeyMappingResponse(
        id = id,
        verificationMethod = verificationMethodDidUrl ?: verificationMethodId,
        keyInfo =
            com.sphereon.crypto.core.KeyInfo<com.sphereon.crypto.core.KeyType>(
                providerId = kmsProviderId,
                alias = kmsKeyAlias,
                kid = kmsKid,
                keyType = keyref?.keyType,
                signatureAlgorithm = keyref?.signatureAlgorithm,
                keyEncoding = keyref?.keyEncoding,
            ),
        purposes = purposesJson.toServicePurposeList(),
    )

/**
 * Projects an SDK [com.sphereon.did.manager.DidKeyMapping] (carried on [com.sphereon.did.manager.ManagedDid.keys])
 * into the wire [KeyMappingResponse] used by both the dedicated `/dids/{did}/key-mappings`
 * endpoints and the inline `?expand=keys` projection on `GET /dids` / `GET /dids/{did}`.
 *
 * Without this mapping the SDK shape — which carries the storage-layer `purposesJson`
 * string and the raw `kmsKeyAlias` / `kmsProviderId` columns — would leak straight through
 * to REST clients, diverging from the OpenAPI `KeyMapping` schema. The keyref-store lookup
 * fields (`keyType`, `signatureAlgorithm`, `keyEncoding`) stay null on this path because
 * `ManagedDid.keys` is built without the per-mapping keyref-store fetch; dedicated
 * key-mapping endpoints that *do* perform that fetch still populate them via the record
 * mapper above.
 */
internal fun com.sphereon.did.manager.DidKeyMapping.toServiceResponse(): KeyMappingResponse =
    KeyMappingResponse(
        id = id,
        verificationMethod = verificationMethodId,
        keyInfo =
            com.sphereon.crypto.core.KeyInfo<com.sphereon.crypto.core.KeyType>(
                providerId = kmsProviderId,
                alias = kmsKeyAlias,
            ),
        purposes = purposesJson.toServicePurposeList(),
    )

/** Projects a persistence controller row into the wire [DidControllerView] shape. */
internal fun DidControllerRecord.toServiceView(): DidControllerView =
    DidControllerView(
        id = id,
        controllerDid = controllerDid,
        ordinal = ordinal,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Projects a persistence alsoKnownAs row into the wire [DidAlsoKnownAsView] shape. */
internal fun DidAlsoKnownAsRecord.toServiceView(): DidAlsoKnownAsView =
    DidAlsoKnownAsView(
        id = id,
        akaUri = akaUri,
        ordinal = ordinal,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Projects a persistence equivalentId row into the wire [DidEquivalentIdView] shape. */
internal fun DidEquivalentIdRecord.toServiceView(): DidEquivalentIdView =
    DidEquivalentIdView(
        id = id,
        equivalentDid = equivalentDid,
        ordinal = ordinal,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Wire `KeyMappingCreateBody` → manager `AddKeyMappingInput`. */
internal fun KeyMappingCreateBody.toAddKeyMappingInput(): AddKeyMappingInput =
    AddKeyMappingInput(
        verificationMethodId = verificationMethod,
        keyInfo = keyInfo,
        purposes = purposes,
    )

/**
 * Maps the wire-side `DidServiceUpdateBody` (JSON Merge Patch) to the manager-side
 * `ServicePatch`. Returns `Err(ILLEGAL_ARGUMENT_ERROR)` when the `type` field is present
 * but neither a string nor a string-array.
 */
internal fun DidServiceUpdateBody.toServicePatch(): IdkResult<ServicePatch, IdkError> {
    val typeList =
        type?.let { typeElement ->
            when (typeElement) {
                is JsonArray -> typeElement.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> typeElement.contentOrNull?.let { listOf(it) }
                else -> null
            } ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Service type must be a string or array of strings"))
        }
    return Ok(
        ServicePatch(
            type = if (typeList != null) PatchValue.Set(typeList) else PatchValue.Unchanged,
            serviceEndpoint = serviceEndpoint?.let { PatchValue.Set(it) } ?: PatchValue.Unchanged,
        ),
    )
}

/** Maps the wire-side `ReplaceDidBody` to the manager-side `DidAggregateReplacement`. */
internal fun ReplaceDidBody.toAggregateReplacement(): DidAggregateReplacement =
    DidAggregateReplacement(
        alias = alias,
        controllers = controllers,
        alsoKnownAs = alsoKnownAs,
        equivalentIds = equivalentIds,
        contexts = contexts,
        canonicalId = canonicalId,
        deactivated = deactivated,
        services = services,
        relationships =
            relationships.map { entry ->
                DidRelationshipReplacement(
                    purpose = entry.purpose,
                    verificationMethodId = entry.verificationMethodId,
                    referenceDidUrl = entry.referenceDidUrl,
                )
            },
    )

// ===== DID lifecycle impls =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateDidServiceCommand>())
class CreateDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
    private val keyManager: KeyManagerService,
) : TypedServiceCommandAdapter<CreateDidInput, ManagedDid, IdkError>(
        commandId = CreateDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateDidInput>(),
        outputTypeToken = typeToken<ManagedDid>(),
    ),
    CreateDidServiceCommand {
    override val commandId = CreateDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateDidInput,
        applyDuring: (CreateDidInput) -> CreateDidInput,
    ): IdkResult<ManagedDid, IdkError> {
        val input = applyDuring(args)
        val resolvedKey = resolveKmsKey(input.keyInfo).getOrElse { return Err(it) }
        val options = input.toDidCreateOptions(resolvedKey).getOrElse { return Err(it) }
        val created = didManager.create(options).getOrElse { return Err(it) }
        return Ok(created)
    }

    /**
     * Resolves the registered KMS key referenced by [keyInfo] and returns its canonical alias
     * plus public JWK. `(providerId, alias)` and `(providerId, kid)` are both supported — the
     * caller passes whichever they have on hand and the KMS returns the same [ManagedKeyInfoType].
     *
     * This endpoint never accepts wire-supplied JWK material; the key must already be
     * registered with the KMS before POST /dids is issued.
     */
    private suspend fun resolveKmsKey(keyInfo: KeyInfo<KeyType>): IdkResult<ResolvedKmsKey, IdkError> {
        val providerId =
            keyInfo.providerId ?: return Err(
                IdkError.fromString(
                    message = "DidCreateRequest.keyInfo requires `providerId` (KMS provider id)",
                    code = "ILLEGAL_ARGUMENT",
                    category = com.sphereon.core.api.error.ErrorCategory.VALIDATION,
                ),
            )
        if (keyInfo.alias == null && keyInfo.kid == null) {
            return Err(
                IdkError.fromString(
                    message = "DidCreateRequest.keyInfo requires at least one of `alias` or `kid` to locate the registered KMS key",
                    code = "ILLEGAL_ARGUMENT",
                    category = com.sphereon.core.api.error.ErrorCategory.VALIDATION,
                ),
            )
        }
        val managed =
            keyManager.getKey(
                KeyInfo<KeyType>(
                    alias = keyInfo.alias,
                    kid = keyInfo.kid,
                    providerId = providerId,
                ),
            )
        val resolvedAlias =
            managed.alias ?: return Err(
                IdkError.fromString(
                    message = "KMS-resolved key (providerId=$providerId, alias=${keyInfo.alias}, kid=${keyInfo.kid}) has no alias; cannot build DidKeyMapping",
                    code = "ILLEGAL_STATE",
                ),
            )
        val jwk =
            CoseJoseKeyMappingService.toJwkKeyInfo(managed.toManagedPublicKeyInfo()).key
                ?: return Err(
                    IdkError.fromString(
                        message = "KMS key (providerId=$providerId, alias=$resolvedAlias) has no public JWK material",
                        code = "ILLEGAL_STATE",
                    ),
                )
        return Ok(ResolvedKmsKey(alias = resolvedAlias, providerId = providerId, jwk = jwk))
    }
}

/**
 * KMS lookup result: the canonical key alias + public JWK retrieved from the registered key,
 * regardless of whether the caller passed `alias` or `kid` on the wire.
 */
private data class ResolvedKmsKey(
    val alias: String,
    val providerId: String,
    val jwk: com.sphereon.crypto.core.jose.Jwk,
)

/**
 * Maps the wire shape + the KMS-resolved key to the SDK [DidCreateOptions]. The KMS lookup
 * collapses `(providerId, alias)` and `(providerId, kid)` to the same canonical alias + public
 * JWK regardless of which one the caller passed; that pair folds into a single
 * [VerificationMethodConfig] entry pinned to `verificationMethodId="key-1"` (the single-VM
 * convention used by did:key) with the standard default purpose set.
 *
 * `alsoKnownAs` cannot be set at creation time today (no field on [DidCreateOptions]) — once
 * the DID is created callers must use the AKA sub-resource endpoint. Reject up front rather
 * than dropping the field silently.
 *
 * The wire `options` map carries method-specific creation options. Well-known did:web keys
 * are lifted into the typed [DidCreateOptions] fields the providers actually read:
 * `options["domain"]` → [DidCreateOptions.domain] and `options["path"]` (slash-separated,
 * e.g. `"user/alice"`) → [DidCreateOptions.path]. The full map is still passed through as
 * [DidCreateOptions.methodOptions] for forward compatibility.
 */
private fun CreateDidInput.toDidCreateOptions(resolvedKey: ResolvedKmsKey): IdkResult<DidCreateOptions, IdkError> {
    if (alsoKnownAs?.isNotEmpty() == true) {
        return Err(
            IdkError.fromString(
                message =
                    "alsoKnownAs cannot be set during DID creation; create the DID first, then POST to " +
                        "/dids/{did}/also-known-as",
                code = "UNSUPPORTED_OPERATION",
                category = com.sphereon.core.api.error.ErrorCategory.UNPROCESSABLE_ENTITY,
            ),
        )
    }
    return Ok(
        DidCreateOptions(
            method = method,
            alias = didAlias,
            domain = options["domain"],
            path =
                options["path"]
                    ?.split("/")
                    ?.filter { segment -> segment.isNotEmpty() }
                    ?.takeIf { segments -> segments.isNotEmpty() },
            controllers = controllers ?: emptyList(),
            publicKeyJwk = resolvedKey.jwk,
            verificationMethods =
                listOf(
                    VerificationMethodConfig(
                        kmsKeyAlias = resolvedKey.alias,
                        kmsProviderId = resolvedKey.providerId,
                        verificationMethodId = "key-1",
                        purposes =
                            listOf(
                                VerificationPurpose.AUTHENTICATION,
                                VerificationPurpose.ASSERTION_METHOD,
                            ),
                        // Normally the DidCreationDslProcessor resolves this from KMS; this REST
                        // mapping bypasses the DSL, so populate it from the KMS lookup done above.
                        // Providers that emit document VMs from configs (e.g. did:web) skip
                        // entries without a JWK.
                        publicKeyJwk = resolvedKey.jwk,
                    ),
                ),
            methodOptions = options,
        ),
    )
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListDidsServiceCommand>())
class ListDidsServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidFilter, ListDidsOutput, IdkError>(
        commandId = ListDidsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidFilter>(),
        outputTypeToken = typeToken<ListDidsOutput>(),
    ),
    ListDidsServiceCommand {
    override val commandId = ListDidsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidFilter,
        applyDuring: (DidFilter) -> DidFilter,
    ): IdkResult<ListDidsOutput, IdkError> {
        val filter = applyDuring(args)
        val expand = parseExpand(filter.expand).getOrElse { return Err(it) }
        val items = didManager.list(filter).getOrElse { return Err(it) }
        val pageSize = filter.size
        val totalElements =
            if (pageSize == null) {
                items.size
            } else {
                didManager.count(filter).getOrElse { return Err(it) }.toInt()
            }
        val effectiveSize = pageSize ?: totalElements.coerceAtLeast(1)
        val totalPages =
            if (effectiveSize <= 0) 0 else (totalElements + effectiveSize - 1) / effectiveSize
        val offset = filter.page * effectiveSize
        val hasMore = offset + items.size < totalElements
        return Ok(
            ListDidsOutput(
                items = items.map { it.toWire(expand) },
                page =
                    PageMeta(
                        limit = effectiveSize,
                        offset = offset,
                        page = filter.page,
                        size = effectiveSize,
                        total = totalElements,
                        totalPages = totalPages,
                        hasMore = hasMore,
                    ),
            ),
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TrackExternalDidServiceCommand>())
class TrackExternalDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<TrackExternalDidInput, ManagedDid, IdkError>(
        commandId = TrackExternalDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<TrackExternalDidInput>(),
        outputTypeToken = typeToken<ManagedDid>(),
    ),
    TrackExternalDidServiceCommand {
    override val commandId = TrackExternalDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: TrackExternalDidInput,
        applyDuring: (TrackExternalDidInput) -> TrackExternalDidInput,
    ): IdkResult<ManagedDid, IdkError> {
        val input = applyDuring(args)
        val tracked = didManager.trackExternal(input.did, input.alias).getOrElse { return Err(it) }
        return Ok(tracked)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDidServiceCommand>())
class GetDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<GetDidInput, Did, IdkError>(
        commandId = GetDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDidInput>(),
        outputTypeToken = typeToken<Did>(),
    ),
    GetDidServiceCommand {
    override val commandId = GetDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDidInput,
        applyDuring: (GetDidInput) -> GetDidInput,
    ): IdkResult<Did, IdkError> {
        val input = applyDuring(args)
        val expand = parseExpand(input.expand).getOrElse { return Err(it) }
        val managed = didManager.get(input.did).getOrElse { return Err(it) }
        return Ok(managed.toWire(expand))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateDidServiceCommand>())
class UpdateDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<UpdateDidInput, ManagedDid, IdkError>(
        commandId = UpdateDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateDidInput>(),
        outputTypeToken = typeToken<ManagedDid>(),
    ),
    UpdateDidServiceCommand {
    override val commandId = UpdateDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateDidInput,
        applyDuring: (UpdateDidInput) -> UpdateDidInput,
    ): IdkResult<ManagedDid, IdkError> {
        val input = applyDuring(args)
        return didManager.update(input.did, input.options)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ReplaceDidServiceCommand>())
class ReplaceDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<ReplaceDidInput, ManagedDid, IdkError>(
        commandId = ReplaceDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ReplaceDidInput>(),
        outputTypeToken = typeToken<ManagedDid>(),
    ),
    ReplaceDidServiceCommand {
    override val commandId = ReplaceDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ReplaceDidInput,
        applyDuring: (ReplaceDidInput) -> ReplaceDidInput,
    ): IdkResult<ManagedDid, IdkError> {
        val input = applyDuring(args)
        val body = input.body
        return didManager.replaceDidAggregate(input.did, body.toAggregateReplacement())
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteDidServiceCommand>())
class DeleteDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<DidIdInput, DeleteDidOutput, IdkError>(
        commandId = DeleteDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<DeleteDidOutput>(),
    ),
    DeleteDidServiceCommand {
    override val commandId = DeleteDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<DeleteDidOutput, IdkError> {
        val input = applyDuring(args)
        didManager.delete(input.did).getOrElse { return Err(it) }
        return Ok(DeleteDidOutput(did = input.did, deleted = true))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeactivateDidServiceCommand>())
class DeactivateDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<DeactivateDidInput, ManagedDid, IdkError>(
        commandId = DeactivateDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeactivateDidInput>(),
        outputTypeToken = typeToken<ManagedDid>(),
    ),
    DeactivateDidServiceCommand {
    override val commandId = DeactivateDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeactivateDidInput,
        applyDuring: (DeactivateDidInput) -> DeactivateDidInput,
    ): IdkResult<ManagedDid, IdkError> {
        val input = applyDuring(args)
        didManager.deactivate(input.did, input.options).getOrElse { return Err(it) }
        return didManager.get(input.did)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveDidServiceCommand>())
class ResolveDidServiceCommandImpl(
    execution: SessionExecution,
    private val resolverRegistry: DidResolverRegistry,
) : TypedServiceCommandAdapter<ResolveDidInput, DidResolutionResult, IdkError>(
        commandId = ResolveDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveDidInput>(),
        outputTypeToken = typeToken<DidResolutionResult>(),
    ),
    ResolveDidServiceCommand {
    override val commandId = ResolveDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ResolveDidInput,
        applyDuring: (ResolveDidInput) -> ResolveDidInput,
    ): IdkResult<DidResolutionResult, IdkError> {
        val input = applyDuring(args)
        val options = DidResolutionOptions(accept = input.accept)
        return resolverRegistry.resolve(input.did, options)
    }
}

// ===== Verification methods =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListVerificationMethodsServiceCommand>())
class ListVerificationMethodsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidIdInput, VerificationMethodListResponse, IdkError>(
        commandId = ListVerificationMethodsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<VerificationMethodListResponse>(),
    ),
    ListVerificationMethodsServiceCommand {
    override val commandId = ListVerificationMethodsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<VerificationMethodListResponse, IdkError> {
        val did = applyDuring(args).did
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(VerificationMethodListResponse(detail.verificationMethod.map { it.toServiceResponse(detail) }))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddVerificationMethodServiceCommand>())
class AddVerificationMethodServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<CreateVerificationMethodInput, VerificationMethodResponse, IdkError>(
        commandId = AddVerificationMethodServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateVerificationMethodInput>(),
        outputTypeToken = typeToken<VerificationMethodResponse>(),
    ),
    AddVerificationMethodServiceCommand {
    override val commandId = AddVerificationMethodServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateVerificationMethodInput,
        applyDuring: (CreateVerificationMethodInput) -> CreateVerificationMethodInput,
    ): IdkResult<VerificationMethodResponse, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val body = input.body
        val alias =
            body.keyInfo.alias ?: body.keyInfo.kid
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Verification method requires at least one of keyInfo.alias or keyInfo.kid"))
        val providerId =
            body.keyInfo.providerId
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Verification method requires keyInfo.providerId"))
        val type =
            VerificationMethodType.fromValue(body.type)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported verification method type: ${body.type}"))
        val vmFragmentId = body.id?.removePrefix("#") ?: Uuid4Generator.next()
        val config =
            VerificationMethodConfig(
                kmsKeyAlias = alias,
                kmsProviderId = providerId,
                verificationMethodId = vmFragmentId,
                purposes = (body.referenceVerificationRelations + body.purposes).distinct(),
                valueVerificationRelation = body.valueVerificationRelation,
                referenceVerificationRelations = body.referenceVerificationRelations.ifEmpty { body.purposes },
                extensionProperties = body.extensionProperties?.toMap().orEmpty(),
                type = type,
                controller = body.controller,
            )
        didManager.addVerificationMethod(did, config).getOrElse { return Err(it) }
        val absoluteVmId = "$did#$vmFragmentId"
        val saved =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val created =
            saved.verificationMethod.firstOrNull { it.vmId == absoluteVmId }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found after create: $absoluteVmId"))
        return Ok(created.toServiceResponse(saved))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetVerificationMethodServiceCommand>())
class GetVerificationMethodServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<GetVerificationMethodInput, VerificationMethodResponse, IdkError>(
        commandId = GetVerificationMethodServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetVerificationMethodInput>(),
        outputTypeToken = typeToken<VerificationMethodResponse>(),
    ),
    GetVerificationMethodServiceCommand {
    override val commandId = GetVerificationMethodServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetVerificationMethodInput,
        applyDuring: (GetVerificationMethodInput) -> GetVerificationMethodInput,
    ): IdkResult<VerificationMethodResponse, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val methodId = input.methodId
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val vm =
            detail.verificationMethod.firstOrNull { it.id == methodId || it.vmId == methodId || it.vmId.endsWith("#$methodId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found: $methodId"))
        return Ok(vm.toServiceResponse(detail))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateVerificationMethodServiceCommand>())
class UpdateVerificationMethodServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<UpdateVerificationMethodInput, VerificationMethodResponse, IdkError>(
        commandId = UpdateVerificationMethodServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateVerificationMethodInput>(),
        outputTypeToken = typeToken<VerificationMethodResponse>(),
    ),
    UpdateVerificationMethodServiceCommand {
    override val commandId = UpdateVerificationMethodServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateVerificationMethodInput,
        applyDuring: (UpdateVerificationMethodInput) -> UpdateVerificationMethodInput,
    ): IdkResult<VerificationMethodResponse, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val methodId = input.methodId
        val raw = input.rawBody

        val body =
            try {
                serviceJson.decodeFromJsonElement(VerificationMethodUpdateBody.serializer(), raw)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid request body: ${expected.message}", throwable = expected))
            }

        // Wire DTO (JSON Merge Patch) → typed manager-side patch (tri-state PatchValue).
        // Field presence (`raw.containsKey`) distinguishes Unchanged vs Set(value) — only
        // present at the wire boundary; the manager API uses PatchValue exclusively.
        // KMS sub-fields (providerId/alias/kid) flow through KeyInfoPatch so the manager can
        // distinguish absent-from-PATCH ('keep existing') vs explicit-null ('clear field').
        // providerId is non-clearable — wire-side present-null is silently dropped to
        // 'unchanged' (matches the pre-IDK-20 behaviour `providerId ?: existing.kmsProviderId`).
        val providerIdPatch: PatchValue<String> =
            if (raw.containsKey("providerId") && body.providerId != null) PatchValue.Set(body.providerId) else PatchValue.Unchanged
        val aliasPatch: PatchValue<String?> =
            if (raw.containsKey("alias")) PatchValue.Set(body.alias) else PatchValue.Unchanged
        val kidPatch: PatchValue<String?> =
            if (raw.containsKey("kid")) PatchValue.Set(body.kid) else PatchValue.Unchanged
        val keyInfoPatch = KeyInfoPatch(providerId = providerIdPatch, alias = aliasPatch, kid = kidPatch)
        val patch =
            VerificationMethodPatch(
                controller = body.controller?.let { PatchValue.Set(it) } ?: PatchValue.Unchanged,
                keyInfo = keyInfoPatch,
                expiresAt = if (raw.containsKey("expiresAt")) PatchValue.Set(body.expiresAt) else PatchValue.Unchanged,
                revokedAt = if (raw.containsKey("revokedAt")) PatchValue.Set(body.revokedAt) else PatchValue.Unchanged,
                extensionProperties =
                    if (raw.containsKey("extensionProperties")) PatchValue.Set(body.extensionProperties) else PatchValue.Unchanged,
                valueVerificationRelation =
                    if (raw.containsKey("valueVerificationRelation")) {
                        PatchValue.Set(body.valueVerificationRelation)
                    } else {
                        PatchValue.Unchanged
                    },
                referenceVerificationRelations =
                    if (raw.containsKey("referenceVerificationRelations")) {
                        PatchValue.Set(body.referenceVerificationRelations)
                    } else {
                        PatchValue.Unchanged
                    },
            )

        didManager.patchVerificationMethod(did, methodId, patch).getOrElse { return Err(it) }

        // Project the post-patch state to the wire VerificationMethodResponse. Re-fetch the
        // aggregate so the response sees the canonical persisted form (relationships, ordinals).
        val saved =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val patched =
            saved.verificationMethod.firstOrNull {
                it.id == methodId || it.vmId == methodId || it.vmId.endsWith("#$methodId")
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found: $methodId"))
        return Ok(patched.toServiceResponse(saved))
    }
}

/**
 * Internal decode-target for the JSON Merge Patch body of [UpdateVerificationMethodServiceCommand].
 * Lives here because [UpdateVerificationMethodInput] passes the raw [JsonObject] through (so the
 * impl can distinguish field-absent from field-null), and this is the typed shape the impl
 * decodes into for non-merge-sensitive fields.
 */
@kotlinx.serialization.Serializable
internal data class VerificationMethodUpdateBody(
    val controller: String? = null,
    val providerId: String? = null,
    val alias: String? = null,
    val kid: String? = null,
    val expiresAt: kotlin.time.Instant? = null,
    val revokedAt: kotlin.time.Instant? = null,
    val valueVerificationRelation: VerificationPurpose? = null,
    val referenceVerificationRelations: List<VerificationPurpose>? = null,
    val extensionProperties: JsonObject? = null,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveVerificationMethodServiceCommand>())
class RemoveVerificationMethodServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<GetVerificationMethodInput, DeleteVerificationMethodOutput, IdkError>(
        commandId = RemoveVerificationMethodServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetVerificationMethodInput>(),
        outputTypeToken = typeToken<DeleteVerificationMethodOutput>(),
    ),
    RemoveVerificationMethodServiceCommand {
    override val commandId = RemoveVerificationMethodServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetVerificationMethodInput,
        applyDuring: (GetVerificationMethodInput) -> GetVerificationMethodInput,
    ): IdkResult<DeleteVerificationMethodOutput, IdkError> {
        val input = applyDuring(args)
        didManager.removeVerificationMethod(input.did, input.methodId).getOrElse { return Err(it) }
        return Ok(DeleteVerificationMethodOutput(did = input.did, methodId = input.methodId, deleted = true))
    }
}

// ===== Verification relationships =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListVerificationRelationshipsServiceCommand>())
class ListVerificationRelationshipsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<ListVerificationRelationshipsInput, VerificationRelationshipListResponse, IdkError>(
        commandId = ListVerificationRelationshipsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListVerificationRelationshipsInput>(),
        outputTypeToken = typeToken<VerificationRelationshipListResponse>(),
    ),
    ListVerificationRelationshipsServiceCommand {
    override val commandId = ListVerificationRelationshipsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: ListVerificationRelationshipsInput,
        applyDuring: (ListVerificationRelationshipsInput) -> ListVerificationRelationshipsInput,
    ): IdkResult<VerificationRelationshipListResponse, IdkError> {
        val input = applyDuring(args)
        val detail =
            repository.findByDid(execution.tenantId, input.did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: ${input.did}"))
        val views =
            detail
                .toServiceRelationshipViews()
                .let { all -> input.purpose?.let { p -> all.filter { it.purpose == p } } ?: all }
        return Ok(VerificationRelationshipListResponse(views))
    }
}

// ===== Services =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListDidServicesServiceCommand>())
class ListDidServicesServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidIdInput, DidServiceListResponse, IdkError>(
        commandId = ListDidServicesServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<DidServiceListResponse>(),
    ),
    ListDidServicesServiceCommand {
    override val commandId = ListDidServicesServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<DidServiceListResponse, IdkError> {
        val did = applyDuring(args).did
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(DidServiceListResponse(detail.service.map { it.toServiceWireService() }))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddDidServiceServiceCommand>())
class AddDidServiceServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<CreateDidServiceInput, DidService, IdkError>(
        commandId = AddDidServiceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateDidServiceInput>(),
        outputTypeToken = typeToken<DidService>(),
    ),
    AddDidServiceServiceCommand {
    override val commandId = AddDidServiceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateDidServiceInput,
        applyDuring: (CreateDidServiceInput) -> CreateDidServiceInput,
    ): IdkResult<DidService, IdkError> {
        val input = applyDuring(args)
        didManager.addService(input.did, input.service).getOrElse { return Err(it) }
        return Ok(input.service)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetDidServiceServiceCommand>())
class GetDidServiceServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<GetDidServiceInput, DidService, IdkError>(
        commandId = GetDidServiceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDidServiceInput>(),
        outputTypeToken = typeToken<DidService>(),
    ),
    GetDidServiceServiceCommand {
    override val commandId = GetDidServiceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDidServiceInput,
        applyDuring: (GetDidServiceInput) -> GetDidServiceInput,
    ): IdkResult<DidService, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val serviceId = input.serviceId
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val service =
            detail.service.firstOrNull { it.id == serviceId || it.serviceId == serviceId || it.serviceId.endsWith("#$serviceId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Service not found: $serviceId"))
        return Ok(service.toServiceWireService())
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateDidServiceServiceCommand>())
class UpdateDidServiceServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<UpdateDidServiceInput, DidService, IdkError>(
        commandId = UpdateDidServiceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateDidServiceInput>(),
        outputTypeToken = typeToken<DidService>(),
    ),
    UpdateDidServiceServiceCommand {
    override val commandId = UpdateDidServiceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: UpdateDidServiceInput,
        applyDuring: (UpdateDidServiceInput) -> UpdateDidServiceInput,
    ): IdkResult<DidService, IdkError> {
        val input = applyDuring(args)
        // Translate wire DTO (nullable = absent) to the typed manager-side ServicePatch
        // (PatchValue tri-state). The wire schema does not currently support explicit-null
        // semantics on these fields — both are required when present — so a nullable absent
        // maps directly to PatchValue.Unchanged.
        val patch = input.body.toServicePatch().getOrElse { return Err(it) }
        return didManager.patchService(input.did, input.serviceId, patch)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveDidServiceServiceCommand>())
class RemoveDidServiceServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<GetDidServiceInput, DeleteDidServiceOutput, IdkError>(
        commandId = RemoveDidServiceServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetDidServiceInput>(),
        outputTypeToken = typeToken<DeleteDidServiceOutput>(),
    ),
    RemoveDidServiceServiceCommand {
    override val commandId = RemoveDidServiceServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: GetDidServiceInput,
        applyDuring: (GetDidServiceInput) -> GetDidServiceInput,
    ): IdkResult<DeleteDidServiceOutput, IdkError> {
        val input = applyDuring(args)
        didManager.removeService(input.did, input.serviceId).getOrElse { return Err(it) }
        return Ok(DeleteDidServiceOutput(did = input.did, serviceId = input.serviceId, deleted = true))
    }
}

// ===== Key mappings =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListKeyMappingsServiceCommand>())
class ListKeyMappingsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidIdInput, KeyMappingListResponse, IdkError>(
        commandId = ListKeyMappingsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<KeyMappingListResponse>(),
    ),
    ListKeyMappingsServiceCommand {
    override val commandId = ListKeyMappingsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<KeyMappingListResponse, IdkError> {
        val did = applyDuring(args).did
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(KeyMappingListResponse(detail.keyMapping.map { it.toServiceResponse() }))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddKeyMappingServiceCommand>())
class AddKeyMappingServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
    private val repository: DidRepository,
    private val keyReferenceStore: com.sphereon.crypto.key.persistence.KeyReferenceStore,
) : TypedServiceCommandAdapter<CreateKeyMappingInput, KeyMappingResponse, IdkError>(
        commandId = AddKeyMappingServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateKeyMappingInput>(),
        outputTypeToken = typeToken<KeyMappingResponse>(),
    ),
    AddKeyMappingServiceCommand {
    override val commandId = AddKeyMappingServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateKeyMappingInput,
        applyDuring: (CreateKeyMappingInput) -> CreateKeyMappingInput,
    ): IdkResult<KeyMappingResponse, IdkError> {
        val input = applyDuring(args)
        val body = input.body
        val created = didManager.addKeyMapping(input.did, body.toAddKeyMappingInput()).getOrElse { return Err(it) }
        // The manager returns the simple DidKeyMapping projection. Re-fetch the persisted row
        // by its returned id and look up the keyref to enrich the wire response with
        // KeyInfo.keyType / signatureAlgorithm / keyEncoding when the keyref-store is available.
        return projectAddedKeyMappingResponse(input.did, created.id)
    }

    private suspend fun projectAddedKeyMappingResponse(
        did: String,
        mappingId: String,
    ): IdkResult<KeyMappingResponse, IdkError> {
        val saved =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val mappingRow =
            saved.keyMapping.firstOrNull { it.id == mappingId }
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Key mapping persisted but could not be re-located: $mappingId",
                    ),
                )
        val keyref =
            mappingRow.keyReferenceId?.let { krefId ->
                if (keyReferenceStore.isAvailable) {
                    keyReferenceStore.findById(execution.tenantId, krefId).getOrElse { return Err(it) }
                } else {
                    null
                }
            }
        return Ok(mappingRow.toServiceResponse(keyref))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveKeyMappingServiceCommand>())
class RemoveKeyMappingServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<DeleteKeyMappingInput, DeleteKeyMappingOutput, IdkError>(
        commandId = RemoveKeyMappingServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteKeyMappingInput>(),
        outputTypeToken = typeToken<DeleteKeyMappingOutput>(),
    ),
    RemoveKeyMappingServiceCommand {
    override val commandId = RemoveKeyMappingServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteKeyMappingInput,
        applyDuring: (DeleteKeyMappingInput) -> DeleteKeyMappingInput,
    ): IdkResult<DeleteKeyMappingOutput, IdkError> {
        val input = applyDuring(args)
        didManager.removeKeyMapping(input.did, input.mappingId).getOrElse { return Err(it) }
        return Ok(DeleteKeyMappingOutput(mappingId = input.mappingId, deleted = true))
    }
}

// ===== Controllers =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListControllersServiceCommand>())
class ListControllersServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidIdInput, ControllerListResponse, IdkError>(
        commandId = ListControllersServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<ControllerListResponse>(),
    ),
    ListControllersServiceCommand {
    override val commandId = ListControllersServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<ControllerListResponse, IdkError> {
        val did = applyDuring(args).did
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(ControllerListResponse(detail.controller.map { it.toServiceView() }))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddControllerServiceCommand>())
class AddControllerServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<CreateControllerInput, DidControllerView, IdkError>(
        commandId = AddControllerServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateControllerInput>(),
        outputTypeToken = typeToken<DidControllerView>(),
    ),
    AddControllerServiceCommand {
    override val commandId = AddControllerServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateControllerInput,
        applyDuring: (CreateControllerInput) -> CreateControllerInput,
    ): IdkResult<DidControllerView, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val body = input.body
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val now = Clock.System.now()
        val rec =
            DidControllerRecord(
                id = Uuid4Generator.next(),
                didRecordId = detail.record.id,
                controllerDid = body.value,
                ordinal = detail.controller.size,
                createdAt = now,
                updatedAt = now,
            )
        repository.saveController(rec).getOrElse { return Err(it) }
        return Ok(rec.toServiceView())
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveControllerServiceCommand>())
class RemoveControllerServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DeleteControllerInput, DeleteControllerOutput, IdkError>(
        commandId = RemoveControllerServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteControllerInput>(),
        outputTypeToken = typeToken<DeleteControllerOutput>(),
    ),
    RemoveControllerServiceCommand {
    override val commandId = RemoveControllerServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteControllerInput,
        applyDuring: (DeleteControllerInput) -> DeleteControllerInput,
    ): IdkResult<DeleteControllerOutput, IdkError> {
        val input = applyDuring(args)
        val detail =
            repository.findByDid(execution.tenantId, input.did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: ${input.did}"))
        if (detail.controller.none { it.id == input.controllerId }) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Controller '${input.controllerId}' not found on DID '${input.did}'"))
        }
        repository.deleteController(execution.tenantId, input.controllerId).getOrElse { return Err(it) }
        return Ok(DeleteControllerOutput(controllerId = input.controllerId, deleted = true))
    }
}

// ===== AlsoKnownAs =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListAlsoKnownAsServiceCommand>())
class ListAlsoKnownAsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidIdInput, AlsoKnownAsListResponse, IdkError>(
        commandId = ListAlsoKnownAsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<AlsoKnownAsListResponse>(),
    ),
    ListAlsoKnownAsServiceCommand {
    override val commandId = ListAlsoKnownAsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<AlsoKnownAsListResponse, IdkError> {
        val did = applyDuring(args).did
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(AlsoKnownAsListResponse(detail.alsoKnownAs.map { it.toServiceView() }))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddAlsoKnownAsServiceCommand>())
class AddAlsoKnownAsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<CreateAlsoKnownAsInput, DidAlsoKnownAsView, IdkError>(
        commandId = AddAlsoKnownAsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAlsoKnownAsInput>(),
        outputTypeToken = typeToken<DidAlsoKnownAsView>(),
    ),
    AddAlsoKnownAsServiceCommand {
    override val commandId = AddAlsoKnownAsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateAlsoKnownAsInput,
        applyDuring: (CreateAlsoKnownAsInput) -> CreateAlsoKnownAsInput,
    ): IdkResult<DidAlsoKnownAsView, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val body = input.body
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val now = Clock.System.now()
        val rec =
            DidAlsoKnownAsRecord(
                id = Uuid4Generator.next(),
                didRecordId = detail.record.id,
                akaUri = body.value,
                ordinal = detail.alsoKnownAs.size,
                createdAt = now,
                updatedAt = now,
            )
        repository.saveAlsoKnownAs(rec).getOrElse { return Err(it) }
        return Ok(rec.toServiceView())
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveAlsoKnownAsServiceCommand>())
class RemoveAlsoKnownAsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DeleteAlsoKnownAsInput, DeleteAlsoKnownAsOutput, IdkError>(
        commandId = RemoveAlsoKnownAsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteAlsoKnownAsInput>(),
        outputTypeToken = typeToken<DeleteAlsoKnownAsOutput>(),
    ),
    RemoveAlsoKnownAsServiceCommand {
    override val commandId = RemoveAlsoKnownAsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteAlsoKnownAsInput,
        applyDuring: (DeleteAlsoKnownAsInput) -> DeleteAlsoKnownAsInput,
    ): IdkResult<DeleteAlsoKnownAsOutput, IdkError> {
        val input = applyDuring(args)
        val detail =
            repository.findByDid(execution.tenantId, input.did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: ${input.did}"))
        if (detail.alsoKnownAs.none { it.id == input.akaId }) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "AlsoKnownAs entry '${input.akaId}' not found on DID '${input.did}'"))
        }
        repository.deleteAlsoKnownAs(execution.tenantId, input.akaId).getOrElse { return Err(it) }
        return Ok(DeleteAlsoKnownAsOutput(akaId = input.akaId, deleted = true))
    }
}

// ===== Equivalent identifiers =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListEquivalentIdsServiceCommand>())
class ListEquivalentIdsServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DidIdInput, EquivalentIdListResponse, IdkError>(
        commandId = ListEquivalentIdsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<EquivalentIdListResponse>(),
    ),
    ListEquivalentIdsServiceCommand {
    override val commandId = ListEquivalentIdsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<EquivalentIdListResponse, IdkError> {
        val did = applyDuring(args).did
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(EquivalentIdListResponse(detail.equivalentId.map { it.toServiceView() }))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddEquivalentIdServiceCommand>())
class AddEquivalentIdServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<CreateEquivalentIdInput, DidEquivalentIdView, IdkError>(
        commandId = AddEquivalentIdServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateEquivalentIdInput>(),
        outputTypeToken = typeToken<DidEquivalentIdView>(),
    ),
    AddEquivalentIdServiceCommand {
    override val commandId = AddEquivalentIdServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateEquivalentIdInput,
        applyDuring: (CreateEquivalentIdInput) -> CreateEquivalentIdInput,
    ): IdkResult<DidEquivalentIdView, IdkError> {
        val input = applyDuring(args)
        val did = input.did
        val body = input.body
        val detail =
            repository.findByDid(execution.tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        val now = Clock.System.now()
        val rec =
            DidEquivalentIdRecord(
                id = Uuid4Generator.next(),
                didRecordId = detail.record.id,
                equivalentDid = body.value,
                ordinal = detail.equivalentId.size,
                createdAt = now,
                updatedAt = now,
            )
        repository.saveEquivalentId(rec).getOrElse { return Err(it) }
        return Ok(rec.toServiceView())
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveEquivalentIdServiceCommand>())
class RemoveEquivalentIdServiceCommandImpl(
    execution: SessionExecution,
    private val repository: DidRepository,
) : TypedServiceCommandAdapter<DeleteEquivalentIdInput, DeleteEquivalentIdOutput, IdkError>(
        commandId = RemoveEquivalentIdServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteEquivalentIdInput>(),
        outputTypeToken = typeToken<DeleteEquivalentIdOutput>(),
    ),
    RemoveEquivalentIdServiceCommand {
    override val commandId = RemoveEquivalentIdServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DeleteEquivalentIdInput,
        applyDuring: (DeleteEquivalentIdInput) -> DeleteEquivalentIdInput,
    ): IdkResult<DeleteEquivalentIdOutput, IdkError> {
        val input = applyDuring(args)
        val detail =
            repository.findByDid(execution.tenantId, input.did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: ${input.did}"))
        if (detail.equivalentId.none { it.id == input.equivalentIdRowId }) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "EquivalentId row '${input.equivalentIdRowId}' not found on DID '${input.did}'"))
        }
        repository.deleteEquivalentId(execution.tenantId, input.equivalentIdRowId).getOrElse { return Err(it) }
        return Ok(DeleteEquivalentIdOutput(equivalentIdRowId = input.equivalentIdRowId, deleted = true))
    }
}

// ===== Document cache =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCachedDidDocumentServiceCommand>())
class GetCachedDidDocumentServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<DidIdInput, DidDocument, IdkError>(
        commandId = GetCachedDidDocumentServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<DidDocument>(),
    ),
    GetCachedDidDocumentServiceCommand {
    override val commandId = GetCachedDidDocumentServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<DidDocument, IdkError> {
        val input = applyDuring(args)
        val doc =
            didManager.getCachedDocument(input.did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No cached document for ${input.did}"))
        return Ok(doc)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveAndCacheDidServiceCommand>())
class ResolveAndCacheDidServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<DidIdInput, DidDocument, IdkError>(
        commandId = ResolveAndCacheDidServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<DidDocument>(),
    ),
    ResolveAndCacheDidServiceCommand {
    override val commandId = ResolveAndCacheDidServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<DidDocument, IdkError> {
        val input = applyDuring(args)
        return didManager.resolveAndCache(input.did)
    }
}

// ===== Verification relationship mutation impls =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddVerificationRelationshipServiceCommand>())
class AddVerificationRelationshipServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<AddVerificationRelationshipInput, com.sphereon.did.manager.VerificationRelationship, IdkError>(
        commandId = AddVerificationRelationshipServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<AddVerificationRelationshipInput>(),
        outputTypeToken = typeToken<com.sphereon.did.manager.VerificationRelationship>(),
    ),
    AddVerificationRelationshipServiceCommand {
    override val commandId = AddVerificationRelationshipServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: AddVerificationRelationshipInput,
        applyDuring: (AddVerificationRelationshipInput) -> AddVerificationRelationshipInput,
    ): IdkResult<com.sphereon.did.manager.VerificationRelationship, IdkError> {
        val input = applyDuring(args)
        return didManager.addVerificationRelationship(
            did = input.did,
            verificationMethodId = input.body.verificationMethodId,
            purpose = input.body.purpose.value,
            embed = input.body.embed,
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveVerificationRelationshipServiceCommand>())
class RemoveVerificationRelationshipServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<RemoveVerificationRelationshipInput, RemoveVerificationRelationshipOutput, IdkError>(
        commandId = RemoveVerificationRelationshipServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RemoveVerificationRelationshipInput>(),
        outputTypeToken = typeToken<RemoveVerificationRelationshipOutput>(),
    ),
    RemoveVerificationRelationshipServiceCommand {
    override val commandId = RemoveVerificationRelationshipServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: RemoveVerificationRelationshipInput,
        applyDuring: (RemoveVerificationRelationshipInput) -> RemoveVerificationRelationshipInput,
    ): IdkResult<RemoveVerificationRelationshipOutput, IdkError> {
        val input = applyDuring(args)
        didManager.removeVerificationRelationship(input.did, input.relationshipId).getOrElse { return Err(it) }
        return Ok(
            RemoveVerificationRelationshipOutput(
                did = input.did,
                relationshipId = input.relationshipId,
                deleted = true,
            ),
        )
    }
}

// ===== Document cache invalidate =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InvalidateDidDocumentServiceCommand>())
class InvalidateDidDocumentServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<DidIdInput, Unit, IdkError>(
        commandId = InvalidateDidDocumentServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DidIdInput>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    InvalidateDidDocumentServiceCommand {
    override val commandId = InvalidateDidDocumentServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: DidIdInput,
        applyDuring: (DidIdInput) -> DidIdInput,
    ): IdkResult<Unit, IdkError> {
        val input = applyDuring(args)
        return didManager.invalidateCache(input.did)
    }
}

// ===== Capability commands =====

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListSupportedMethodsServiceCommand>())
class ListSupportedMethodsServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<Unit, MethodCapabilityListResponse, IdkError>(
        commandId = ListSupportedMethodsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<Unit>(),
        outputTypeToken = typeToken<MethodCapabilityListResponse>(),
    ),
    ListSupportedMethodsServiceCommand {
    override val commandId = ListSupportedMethodsServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: Unit,
        applyDuring: (Unit) -> Unit,
    ): IdkResult<MethodCapabilityListResponse, IdkError> = Ok(MethodCapabilityListResponse(items = didManager.listSupportedMethodsWithCapabilities()))
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetMethodCapabilitiesServiceCommand>())
class GetMethodCapabilitiesServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<MethodInput, com.sphereon.did.capabilities.DidMethodCapabilities, IdkError>(
        commandId = GetMethodCapabilitiesServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<MethodInput>(),
        outputTypeToken = typeToken<com.sphereon.did.capabilities.DidMethodCapabilities>(),
    ),
    GetMethodCapabilitiesServiceCommand {
    override val commandId = GetMethodCapabilitiesServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: MethodInput,
        applyDuring: (MethodInput) -> MethodInput,
    ): IdkResult<com.sphereon.did.capabilities.DidMethodCapabilities, IdkError> {
        val method = applyDuring(args).method
        val caps =
            didManager.getCapabilities(method)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID method not registered: $method"))
        return Ok(caps)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetMethodCapabilitySummaryServiceCommand>())
class GetMethodCapabilitySummaryServiceCommandImpl(
    execution: SessionExecution,
    private val didManager: DidManager,
) : TypedServiceCommandAdapter<MethodInput, com.sphereon.did.manager.MethodCapabilitySummary, IdkError>(
        commandId = GetMethodCapabilitySummaryServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<MethodInput>(),
        outputTypeToken = typeToken<com.sphereon.did.manager.MethodCapabilitySummary>(),
    ),
    GetMethodCapabilitySummaryServiceCommand {
    override val commandId = GetMethodCapabilitySummaryServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: MethodInput,
        applyDuring: (MethodInput) -> MethodInput,
    ): IdkResult<com.sphereon.did.manager.MethodCapabilitySummary, IdkError> {
        val method = applyDuring(args).method
        val summary =
            didManager.getMethodCapabilitySummary(method)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID method not registered: $method"))
        return Ok(summary)
    }
}
