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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.conf.getProperty
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.AddKeyMappingInput
import com.sphereon.did.manager.AddKeyOptions
import com.sphereon.did.manager.DidAggregateReplacement
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.DidKeyMapping
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.manager.DidRole
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.manager.MethodCapabilitySummary
import com.sphereon.did.manager.PatchValue
import com.sphereon.did.manager.ServicePatch
import com.sphereon.did.manager.VerificationMethodPatch
import com.sphereon.did.manager.VerificationRelationship
import com.sphereon.did.manager.applyOr
import com.sphereon.did.manager.impl.command.normalizeServiceDidUrl
import com.sphereon.did.manager.impl.command.toServiceWireService
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.persistence.DecomposeContext
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
import com.sphereon.did.persistence.IdGenerator
import com.sphereon.did.persistence.VmKmsBinding
import com.sphereon.did.persistence.toDidDetail
import com.sphereon.did.persistence.toDidDocument
import com.sphereon.did.persistence.toDidRecordFilter
import com.sphereon.did.persistence.toManagedDid
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.utils.ParsedDid
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Default [DidManager] implementation — persists DIDs as normalized aggregates via
 * [DidRepository].
 *
 * Every mutation method delegates to the registered [DidProvider] for method-specific
 * validation (e.g. did:key rejects key additions because the key material is derived from the
 * DID string) before the aggregate is persisted. IDs for new rows come from [idGen]; audit
 * timestamps from the injected [clock].
 *
 * Two classes of DIDs are kept side by side:
 *  - MANAGED: created locally. The wire document is reconstructed from the normalized child
 *    tables and `publicKeyJwk` is re-hydrated from the KMS. The reconstructed document is
 *    cached via the IDK [CacheService] so subsequent reads avoid graph reconstruction. Any
 *    mutation invalidates the cache; the normalized graph remains the source of truth for
 *    signing operations. Cache TTL defaults to 30 minutes and is overridable per tenant via
 *    [TenantConfigService] property `did.cache.managed.ttlSeconds`.
 *  - EXTERNAL: imported via [com.sphereon.did.resolver.DidResolverRegistry]. The resolver's
 *    document is cached out-of-band via the IDK [CacheService] (tenant-scoped) so subsequent
 *    reads avoid re-resolving. Cache TTL defaults to 30 minutes and is overridable per tenant
 *    via [TenantConfigService] property `did.cache.external.ttlSeconds`. Mutations and
 *    deletions on an EXTERNAL aggregate invalidate the cache entry.
 *
 * The active tenant is pulled from the session context ([SessionExecution.sessionContext]).
 * For anonymous sessions this resolves to `IdentityConstants.ANONYMOUS_TENANT_ID`; all child
 * rows the manager writes inherit that value.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidManager>())
class DidManagerServiceImpl(
    private val providerRegistry: DidProviderRegistry,
    private val resolverRegistry: DidResolverRegistry,
    private val repository: DidRepository,
    private val execution: SessionExecution,
    private val tenantConfigService: TenantConfigService,
    private val cacheService: CacheService,
    private val keyManagerService: KeyManagerService,
    private val keyReferenceStore: KeyReferenceStore,
    private val idGen: IdGenerator,
    private val clock: Clock,
) : DidManager {
    private val json = Json { encodeDefaults = false }
    private val logger = execution.log.logManager.withTag("DidManagerService")

    private val tenantId: String
        get() = execution.sessionContext.context.tenant.tenantId

    private val actorId: String?
        get() =
            execution.sessionContext.context.principal
                ?.toString()
                ?.takeIf { it.isNotBlank() }

    /**
     * External-DID resolution cache. Tenant-scoped namespace registered with the IDK
     * [CacheService]; values are JSON-encoded [CachedDidResolution] payloads. The default TTL
     * is 30 minutes; per-put TTL is overridable per tenant via [TenantConfigService] property
     * `did.cache.external.ttlSeconds`.
     */
    private val externalDocumentCache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = EXTERNAL_DID_CACHE_NAMESPACE,
                ttlConfig = CacheTtlConfig(tenant = DEFAULT_EXTERNAL_CACHE_TTL_SECONDS.seconds),
            ),
        )
    }

    /**
     * Managed-DID document cache. The reconstructed document (including JWK-hydrated VMs) is
     * cached so subsequent reads skip graph reconstruction and KMS round-trips. Invalidated on
     * any mutation. The normalized aggregate remains the source of truth for signing operations.
     */
    private val managedDocumentCache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = MANAGED_DID_CACHE_NAMESPACE,
                ttlConfig = CacheTtlConfig(tenant = DEFAULT_MANAGED_CACHE_TTL_SECONDS.seconds),
            ),
        )
    }

    private val externalCacheTtlSeconds: Long
        get() = tenantConfigService.getProperty<Long>("did.cache.external.ttlSeconds") ?: DEFAULT_EXTERNAL_CACHE_TTL_SECONDS

    private val managedCacheTtlSeconds: Long
        get() = tenantConfigService.getProperty<Long>("did.cache.managed.ttlSeconds") ?: DEFAULT_MANAGED_CACHE_TTL_SECONDS

    private companion object {
        const val EXTERNAL_DID_CACHE_NAMESPACE: String = "did.resolution.external"
        const val MANAGED_DID_CACHE_NAMESPACE: String = "did.resolution.managed"
        const val DEFAULT_EXTERNAL_CACHE_TTL_SECONDS: Long = 30L * 60L
        const val DEFAULT_MANAGED_CACHE_TTL_SECONDS: Long = 30L * 60L
    }

    override fun getCapabilities(method: String): DidMethodCapabilities? = providerRegistry.getCapabilities(method)

    override fun getSupportedMethods(): List<String> = providerRegistry.getSupportedMethods()

    override fun getMethodCapabilitySummary(method: String): MethodCapabilitySummary? =
        providerRegistry.getCapabilities(method)?.let { caps ->
            MethodCapabilitySummary(
                method = caps.method,
                canCreate = caps.canCreate(),
                canUpdate = caps.canUpdate(),
                canDeactivate = caps.canDeactivate(),
                canDelete = caps.lifecycle.delete,
                isImmutable = caps.isImmutable(),
                isMutable = caps.isMutable(),
                supportsKeyManagement = caps.supportsKeyManagement(),
                supportsServiceManagement = caps.supportsServiceManagement(),
                allowsCaching = caps.allowsCaching(),
            )
        }

    override fun listSupportedMethodsWithCapabilities(): List<DidMethodCapabilities> = providerRegistry.getSupportedMethods().mapNotNull { providerRegistry.getCapabilities(it) }

    /**
     * Capability precheck — fails fast with `UNSUPPORTED_OPERATION` when the method's declared
     * capabilities do not permit [operationName]. Defense-in-depth alongside provider rejection.
     */
    private fun requireCapability(
        method: String,
        operationName: String,
        check: (DidMethodCapabilities) -> Boolean,
    ): IdkResult<Unit, IdkError> {
        val caps =
            providerRegistry.getCapabilities(method)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No provider registered for DID method: $method"))
        return if (check(caps)) {
            Ok(Unit)
        } else {
            Err(
                IdkError.fromString(
                    message = "DID method '$method' does not support $operationName",
                    code = "UNSUPPORTED_OPERATION",
                    category = ErrorCategory.UNPROCESSABLE_ENTITY,
                ),
            )
        }
    }

    override suspend fun get(did: String): IdkResult<ManagedDid, IdkError> {
        cachedManagedDid(did)?.let { return Ok(it) }
        val loaded =
            repository.findByDid(tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return loaded.toManagedDidWithJwks()
    }

    override suspend fun getByAlias(alias: String): IdkResult<ManagedDid, IdkError> {
        val loaded =
            repository.findByAlias(tenantId, alias).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found for alias: $alias"))
        return loaded.toManagedDidWithJwks()
    }

    override suspend fun list(filter: DidFilter?): IdkResult<List<ManagedDid>, IdkError> {
        val recordFilter = (filter ?: DidFilter()).toDidRecordFilter(tenantId = tenantId)
        val aggregates = repository.findAll(recordFilter).getOrElse { return Err(it) }
        val managed = mutableListOf<ManagedDid>()
        for (aggregate in aggregates) {
            managed += aggregate.toManagedDidWithJwks().getOrElse { return Err(it) }
        }
        return Ok(managed)
    }

    override suspend fun count(filter: DidFilter?): IdkResult<Long, IdkError> {
        // count() ignores pagination — the persistence layer applies the same filter without
        // LIMIT/OFFSET when size is null, so strip page/size before delegating.
        val base = filter ?: DidFilter()
        val recordFilter = base.copy(page = 0, size = null).toDidRecordFilter(tenantId = tenantId)
        return repository.count(recordFilter)
    }

    // ============ Create ============

    override suspend fun create(options: DidCreateOptions): IdkResult<ManagedDid, IdkError> {
        val provider = requireProvider(options.method).getOrElse { return Err(it) }
        val result = provider.create(options).getOrElse { return Err(it) }

        val recordId = idGen.next()
        val now = clock.now()

        val docVms = result.didDocument.verificationMethod.orEmpty()
        val configs = options.verificationMethods

        val bindings = linkedMapOf<String, VmKmsBinding>()
        val keyMappings = mutableListOf<DidKeyMappingRecord>()

        when {
            docVms.isEmpty() && configs.isEmpty() -> {
                Unit
            }

            // no VMs emitted — unusual but legal

            docVms.size == configs.size -> {
                docVms.zip(configs).forEach { (vm, cfg) ->
                    val keyReferenceId =
                        findKeyReferenceId(
                            providerId = cfg.kmsProviderId,
                            alias = cfg.kmsKeyAlias,
                            kid = null,
                            role = DidRole.MANAGED,
                        ).getOrElse { return Err(it) }
                    bindings[vm.id] =
                        VmKmsBinding(
                            keyInfo =
                                KeyInfo<KeyType>(
                                    providerId = cfg.kmsProviderId,
                                    alias = cfg.kmsKeyAlias,
                                ),
                            keyReferenceId = keyReferenceId,
                        )
                    keyMappings +=
                        DidKeyMappingRecord(
                            id = idGen.next(),
                            didRecordId = recordId,
                            verificationMethodId = vm.id,
                            kmsProviderId = cfg.kmsProviderId,
                            kmsKeyAlias = cfg.kmsKeyAlias,
                            kmsKid = null,
                            keyReferenceId = keyReferenceId,
                            purposesJson = cfg.purposes.toWireJsonArray(),
                            createdAt = now,
                            updatedAt = now,
                        )
                }
            }

            else -> {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Provider emitted ${docVms.size} verification methods but ${configs.size} " +
                                "VerificationMethodConfig entries were supplied. Each VM must have a matching KMS binding — " +
                                "use the DidCreationDslProcessor to build options.",
                    ),
                )
            }
        }

        val ctx =
            DecomposeContext(
                recordId = recordId,
                did = result.did,
                method = options.method,
                role = DidRole.MANAGED,
                tenantId = tenantId,
                idGen = idGen,
                clock = clock,
                createdById = actorId,
                updatedById = actorId,
                alias = options.alias ?: result.alias,
                keyMappings = keyMappings,
                vmKmsBindings = bindings,
            )

        val detail = result.didDocument.toDidDetail(ctx).getOrElse { return Err(it) }
        invalidateManagedCache(result.did)
        repository.save(detail).getOrElse { return Err(it) }
        return detail.toManagedDidWithJwks()
    }

    // ============ Verification method mutations ============

    override suspend fun addVerificationMethod(
        did: String,
        config: VerificationMethodConfig,
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "verification method addition") {
            it.keyManagement.addition
        }.getOrElse { return Err(it) }

        // Delegate to the provider so method-specific rules (e.g., did:key immutability) apply.
        // did:web requires currentDocument for sub-resource mutations.
        provider.addKey(did, config.toAddKeyOptions(currentDocument = composite.toDidDocument())).getOrElse { return Err(it) }

        val now = clock.now()
        val absoluteVmId = "$did#${config.verificationMethodId}"
        val keyReferenceId =
            findKeyReferenceId(
                providerId = config.kmsProviderId,
                alias = config.kmsKeyAlias,
                kid = null,
                role = composite.record.role,
            ).getOrElse { return Err(it) }
        val publicKeyJwk =
            config.publicKeyJwk
                ?: resolveJwkFromKms(
                    kmsProviderId = config.kmsProviderId,
                    kmsKeyAlias = config.kmsKeyAlias,
                    vmId = absoluteVmId,
                ).getOrElse { return Err(it) }

        if (composite.verificationMethod.any { it.vmId == absoluteVmId }) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Verification method already exists: $absoluteVmId"),
            )
        }

        val newVm =
            DidVerificationMethodRecord(
                id = idGen.next(),
                didRecordId = composite.record.id,
                vmId = absoluteVmId,
                type = config.type.value,
                controller = config.controller ?: did,
                kmsProviderId = config.kmsProviderId,
                kmsKeyAlias = config.kmsKeyAlias,
                kmsKid = null,
                keyReferenceId = keyReferenceId,
                publicKeyJwkJson = json.encodeToString(Jwk.serializer(), publicKeyJwk),
                inlineInJson = config.valueVerificationRelation?.let { listOf(it).toWireJsonArray() },
                expiresAt = null,
                revokedAt = null,
                blockchainAccountId = null,
                extensionPropertiesJson =
                    if (config.extensionProperties.isEmpty()) {
                        null
                    } else {
                        json.encodeToString(
                            kotlinx.serialization.json.JsonObject
                                .serializer(),
                            kotlinx.serialization.json.JsonObject(config.extensionProperties)
                        )
                    },
                ordinal = composite.verificationMethod.size,
                createdAt = now,
                updatedAt = now,
            )

        val embeddedRel =
            config.valueVerificationRelation?.let { purpose ->
                DidVerificationRelationshipRecord(
                    id = idGen.next(),
                    didRecordId = composite.record.id,
                    purpose = purpose.value,
                    entryEmbeddedVmId = newVm.id,
                    entryRefDidUrl = null,
                    ordinal = composite.verificationRelationship.count { it.purpose == purpose.value },
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val newRels =
            config.referenceVerificationRelations.map { purpose ->
                val existingInPurpose = composite.verificationRelationship.count { it.purpose == purpose.value }
                DidVerificationRelationshipRecord(
                    id = idGen.next(),
                    didRecordId = composite.record.id,
                    purpose = purpose.value,
                    entryEmbeddedVmId = null,
                    entryRefDidUrl = absoluteVmId,
                    ordinal = existingInPurpose,
                    createdAt = now,
                    updatedAt = now,
                )
            } + listOfNotNull(embeddedRel)

        val newMapping =
            DidKeyMappingRecord(
                id = idGen.next(),
                didRecordId = composite.record.id,
                // IDK-18 o77: verificationMethodId is the local VM record UUID (FK target);
                // the wire DID URL goes in verificationMethodDidUrl.
                verificationMethodId = newVm.id,
                verificationMethodDidUrl = absoluteVmId,
                kmsProviderId = config.kmsProviderId,
                kmsKeyAlias = config.kmsKeyAlias,
                kmsKid = null,
                keyReferenceId = keyReferenceId,
                purposesJson = config.purposes.toWireJsonArray(),
                createdAt = now,
                updatedAt = now,
            )

        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                verificationMethod = composite.verificationMethod + newVm,
                verificationRelationship = composite.verificationRelationship + newRels,
                keyMapping = composite.keyMapping + newMapping,
            )
        invalidateManagedCache(did)
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    override suspend fun removeVerificationMethod(
        did: String,
        verificationMethodId: String,
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }

        requireCapability(composite.record.method, "verification method removal") {
            it.keyManagement.removal
        }.getOrElse { return Err(it) }

        val absoluteVmId = if (verificationMethodId.startsWith("#")) did + verificationMethodId else verificationMethodId
        val target =
            composite.verificationMethod.firstOrNull { it.vmId == absoluteVmId }
                ?: composite.verificationMethod.firstOrNull { it.vmId.endsWith("#$verificationMethodId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found: $verificationMethodId"))

        provider.removeKey(did, target.vmId, currentDocument = composite.toDidDocument()).getOrElse { return Err(it) }

        val now = clock.now()
        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                verificationMethod = composite.verificationMethod.filter { it.id != target.id },
                verificationRelationship =
                    composite.verificationRelationship.filter { rel ->
                        rel.entryEmbeddedVmId != target.id &&
                            normalizeVmRef(rel.entryRefDidUrl, did) != target.vmId
                    },
                // IDK-18 o77: key-mapping FK targets the local VM UUID, not the wire DID URL.
                keyMapping = composite.keyMapping.filter { it.verificationMethodId != target.id },
            )
        invalidateManagedCache(did)
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    /**
     * Normalises a string-ref VM identifier to its absolute DID URL form so equality compares
     * against [DidVerificationMethodRecord.vmId] regardless of whether the ref was authored
     * relative ("#key-1") or absolute ("did:example:123#key-1").
     */
    private fun normalizeVmRef(
        ref: String?,
        did: String
    ): String? =
        when {
            ref == null -> null
            ref.startsWith("#") -> did + ref
            else -> ref
        }

    // ============ Service mutations ============

    override suspend fun addService(
        did: String,
        service: DidService
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "service addition") {
            it.serviceManagement.addition
        }.getOrElse { return Err(it) }

        if (composite.service.any { it.serviceId == service.id }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Service already exists: ${service.id}"))
        }

        provider.addService(did, service, currentDocument = composite.toDidDocument()).getOrElse { return Err(it) }

        val now = clock.now()
        val newService =
            DidServiceRecord(
                id = idGen.next(),
                didRecordId = composite.record.id,
                serviceId = service.id,
                typeJson = json.encodeToString(ListSerializer(String.serializer()), service.type),
                serviceEndpointJson =
                    json.encodeToString(
                        kotlinx.serialization.json.JsonElement
                            .serializer(),
                        service.serviceEndpoint,
                    ),
                extensionPropertiesJson =
                    if (service.extensions.isEmpty()) {
                        null
                    } else {
                        json.encodeToString(
                            kotlinx.serialization.json.JsonObject
                                .serializer(),
                            kotlinx.serialization.json.JsonObject(service.extensions),
                        )
                    },
                ordinal = composite.service.size,
                createdAt = now,
                updatedAt = now,
            )

        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                service = composite.service + newService,
            )
        invalidateManagedCache(did)
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    override suspend fun removeService(
        did: String,
        serviceId: String
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "service removal") {
            it.serviceManagement.removal
        }.getOrElse { return Err(it) }

        val target =
            composite.service.firstOrNull { it.serviceId == serviceId }
                ?: composite.service.firstOrNull { it.serviceId.endsWith("#$serviceId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Service not found: $serviceId"))

        provider.removeService(did, target.serviceId, currentDocument = composite.toDidDocument()).getOrElse { return Err(it) }

        val now = clock.now()
        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                service = composite.service.filter { it.id != target.id },
            )
        invalidateManagedCache(did)
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    // ============ Update / deactivate / delete ============

    override suspend fun update(
        did: String,
        options: DidUpdateOptions
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "update") {
            it.lifecycle.update
        }.getOrElse { return Err(it) }

        provider.update(did, options).getOrElse { return Err(it) }

        val now = clock.now()
        var updated = composite

        options.removeVerificationMethodIds?.forEach { vmId ->
            val absolute = if (vmId.startsWith("#")) did + vmId else vmId
            val target =
                updated.verificationMethod.firstOrNull { it.vmId == absolute }
                    ?: updated.verificationMethod.firstOrNull { it.vmId.endsWith("#$vmId") }
            if (target != null) {
                updated =
                    updated.copy(
                        verificationMethod = updated.verificationMethod.filter { it.id != target.id },
                        verificationRelationship =
                            updated.verificationRelationship.filter { rel ->
                                rel.entryEmbeddedVmId != target.id &&
                                    normalizeVmRef(rel.entryRefDidUrl, did) != target.vmId
                            },
                        // IDK-18 o77: key-mapping FK targets the local VM UUID, not the wire DID URL.
                        keyMapping = updated.keyMapping.filter { it.verificationMethodId != target.id },
                    )
            }
        }

        options.removeServiceIds?.forEach { sid ->
            updated = updated.copy(service = updated.service.filter { it.serviceId != sid && !it.serviceId.endsWith("#$sid") })
        }

        options.addServices?.forEach { svc ->
            if (updated.service.any { it.serviceId == svc.id }) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Service already exists: ${svc.id}"))
            }
            updated = updated.copy(service = updated.service + svc.toRecord(updated.record.id, updated.service.size, now))
        }

        // NOTE: options.controller was removed in IDK-21 review (VDX-infra-agt) because it
        // diverged from AddController semantics (replace-list vs append-row). Manage the
        // controllers list exclusively through Add/Remove controller endpoints, or use
        // ReplaceDid for full replacement.

        // VDX-infra-knk: alias and canonicalId update support. null = leave unchanged;
        // empty string = clear (DidRecord stores them as nullable so the empty form is
        // distinguishable from "no change").
        val aliasOpt = options.alias
        val newAlias =
            when {
                aliasOpt == null -> updated.record.alias
                aliasOpt.isEmpty() -> null
                else -> aliasOpt
            }
        val canonicalIdOpt = options.canonicalId
        val newCanonicalId =
            when {
                canonicalIdOpt == null -> updated.record.canonicalId
                canonicalIdOpt.isEmpty() -> null
                else -> canonicalIdOpt
            }
        updated =
            updated.copy(
                record =
                    updated.record.copy(
                        alias = newAlias,
                        canonicalId = newCanonicalId,
                        updatedAt = now,
                        updatedById = actorId,
                    ),
            )
        invalidateManagedCache(did)
        repository.save(updated).getOrElse { return Err(it) }
        if (updated.record.role == DidRole.EXTERNAL) {
            externalDocumentCache.removeTenant(tenantId, did)
        }
        return updated.toManagedDidWithJwks()
    }

    override suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions
    ): IdkResult<Unit, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "deactivation") {
            it.lifecycle.deactivate
        }.getOrElse { return Err(it) }
        provider.deactivate(did, options).getOrElse { return Err(it) }

        val now = clock.now()
        val updated =
            composite.copy(
                record = composite.record.copy(deactivated = true, updatedAt = now, updatedById = actorId),
            )
        repository.save(updated).getOrElse { return Err(it) }
        invalidateManagedCache(did)
        if (updated.record.role == DidRole.EXTERNAL) {
            externalDocumentCache.removeTenant(tenantId, did)
        }
        return Ok(Unit)
    }

    override suspend fun delete(did: String): IdkResult<Unit, IdkError> {
        loadDidDetail(did).getOrElse { return Err(it) }
        val now = clock.now()
        repository.softDelete(tenantId, did, deletedAt = now, deletedBy = actorId).getOrElse { return Err(it) }
        invalidateManagedCache(did)
        externalDocumentCache.removeTenant(tenantId, did)
        return Ok(Unit)
    }

    // ============ External DID tracking ============

    override suspend fun trackExternal(
        did: String,
        alias: String?,
    ): IdkResult<ManagedDid, IdkError> {
        val method =
            parseDidMethod(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID: $did"))

        if (repository.findByDid(tenantId, did).getOrElse { return Err(it) } != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "DID already tracked: $did"))
        }

        val resolution = resolverRegistry.resolve(did).getOrElse { return Err(it) }
        val document =
            resolution.didDocument ?: return Err(
                IdkError.NOT_FOUND_ERROR(
                    message = "Resolver could not locate $did: ${resolution.didResolutionMetadata.error ?: "unknown"}",
                ),
            )

        // External DIDs are persisted as the DID string only (plus minimal record metadata).
        // The resolver-supplied document is held by the IDK CacheService — never decomposed
        // into VM/service/relationship rows and never bound to KMS keys (KMS is for managed
        // DIDs only).
        val now = clock.now()
        val record =
            com.sphereon.did.persistence.DidRecord(
                id = idGen.next(),
                tenantId = tenantId,
                did = did,
                method = method,
                alias = alias,
                role = DidRole.EXTERNAL,
                canonicalId = resolution.didDocumentMetadata.canonicalId,
                deactivated = resolution.didDocumentMetadata.deactivated == true,
                createdAt = now,
                createdById = actorId,
                updatedAt = now,
                updatedById = actorId,
            )
        val detail = DidDetail(record = record)
        repository.save(detail).getOrElse { return Err(it) }
        // Pre-warm the resolution cache so the first read after tracking doesn't re-resolve.
        // Forward the original resolution verbatim — wrapping it in DidResolutionResult.success
        // would drop didResolutionMetadata (content type) and didDocumentMetadata (created,
        // updated, nextUpdate, versionId, equivalentId, canonicalId, deactivated).
        cacheResolvedResolution(
            did = did,
            method = method,
            resolution = resolution,
            now = now,
        )
        return detail.toManagedDidWithJwks()
    }

    // ============ Verification method replacement ============

    override suspend fun updateVerificationMethod(
        did: String,
        verificationMethodId: String,
        config: VerificationMethodConfig,
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "verification method replacement") {
            it.keyManagement.replacement
        }.getOrElse { return Err(it) }

        val absoluteVmId = if (verificationMethodId.startsWith("#")) did + verificationMethodId else verificationMethodId
        val target =
            composite.verificationMethod.firstOrNull { it.vmId == absoluteVmId }
                ?: composite.verificationMethod.firstOrNull { it.vmId.endsWith("#$verificationMethodId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found: $verificationMethodId"))

        provider.updateKey(did, target.vmId, config.toAddKeyOptions(currentDocument = composite.toDidDocument())).getOrElse { return Err(it) }

        val now = clock.now()
        val keyReferenceId =
            findKeyReferenceId(
                providerId = config.kmsProviderId,
                alias = config.kmsKeyAlias,
                kid = null,
            ).getOrElse { return Err(it) }
        val publicKeyJwk =
            config.publicKeyJwk
                ?: resolveJwkFromKms(
                    kmsProviderId = config.kmsProviderId,
                    kmsKeyAlias = config.kmsKeyAlias,
                    vmId = target.vmId,
                ).getOrElse { return Err(it) }

        val replacedVm =
            target.copy(
                type = config.type.value,
                controller = config.controller ?: target.controller,
                kmsProviderId = config.kmsProviderId,
                kmsKeyAlias = config.kmsKeyAlias,
                keyReferenceId = keyReferenceId,
                publicKeyJwkJson = json.encodeToString(Jwk.serializer(), publicKeyJwk),
                updatedAt = now,
            )

        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                verificationMethod = composite.verificationMethod.map { if (it.id == target.id) replacedVm else it },
                keyMapping =
                    composite.keyMapping.map { mapping ->
                        // IDK-18 o77: key-mapping FK targets the local VM UUID, not the wire DID URL.
                        if (mapping.verificationMethodId == target.id) {
                            mapping.copy(
                                kmsProviderId = config.kmsProviderId,
                                kmsKeyAlias = config.kmsKeyAlias,
                                keyReferenceId = keyReferenceId,
                                purposesJson = config.purposes.toWireJsonArray(),
                                updatedAt = now,
                            )
                        } else {
                            mapping
                        }
                    },
            )
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    // ============ Service replacement ============

    override suspend fun updateService(
        did: String,
        serviceId: String,
        service: DidService,
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val provider = requireProvider(composite.record.method).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "service replacement") {
            it.serviceManagement.replacement
        }.getOrElse { return Err(it) }

        val target =
            composite.service.firstOrNull { it.serviceId == serviceId }
                ?: composite.service.firstOrNull { it.serviceId.endsWith("#$serviceId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Service not found: $serviceId"))

        provider.updateService(did, target.serviceId, service, currentDocument = composite.toDidDocument()).getOrElse { return Err(it) }

        val now = clock.now()
        val replaced =
            target.copy(
                serviceId = service.id,
                typeJson = json.encodeToString(ListSerializer(String.serializer()), service.type),
                serviceEndpointJson =
                    json.encodeToString(
                        kotlinx.serialization.json.JsonElement
                            .serializer(),
                        service.serviceEndpoint,
                    ),
                extensionPropertiesJson =
                    if (service.extensions.isEmpty()) {
                        null
                    } else {
                        json.encodeToString(
                            kotlinx.serialization.json.JsonObject
                                .serializer(),
                            kotlinx.serialization.json.JsonObject(service.extensions),
                        )
                    },
                updatedAt = now,
            )
        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                service = composite.service.map { if (it.id == target.id) replaced else it },
            )
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    // ============ Service PATCH ============

    @Suppress("ReturnCount")
    override suspend fun patchService(
        did: String,
        serviceId: String,
        patch: ServicePatch,
    ): IdkResult<DidService, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "service replacement") {
            it.serviceManagement.replacement
        }.getOrElse { return Err(it) }

        val existing =
            composite.service.firstOrNull {
                it.id == serviceId || it.serviceId == serviceId || it.serviceId.endsWith("#$serviceId")
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Service not found: $serviceId"))

        val now = clock.now()
        val newTypeJson =
            when (val t = patch.type) {
                is PatchValue.Unchanged -> existing.typeJson
                is PatchValue.Set -> json.encodeToString(ListSerializer(String.serializer()), t.value)
            }
        val newEndpointJson =
            when (val e = patch.serviceEndpoint) {
                is PatchValue.Unchanged -> existing.serviceEndpointJson
                is PatchValue.Set -> json.encodeToString(JsonElement.serializer(), e.value)
            }
        val replaced =
            existing.copy(
                typeJson = newTypeJson,
                serviceEndpointJson = newEndpointJson,
                updatedAt = now,
            )
        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                service = composite.service.map { if (it.id == existing.id) replaced else it },
            )
        repository.save(updated).getOrElse { return Err(it) }
        return Ok(replaced.toServiceWireService())
    }

    // ============ Verification method PATCH ============

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    override suspend fun patchVerificationMethod(
        did: String,
        verificationMethodId: String,
        patch: VerificationMethodPatch,
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "verification method replacement") {
            it.keyManagement.replacement
        }.getOrElse { return Err(it) }

        val existing =
            composite.verificationMethod.firstOrNull {
                it.id == verificationMethodId ||
                    it.vmId == verificationMethodId ||
                    it.vmId.endsWith("#$verificationMethodId")
            } ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found: $verificationMethodId"))

        val newController = patch.controller.applyOr(existing.controller)
        // Per-sub-field merge inside the KMS binding: PatchValue.Unchanged keeps the existing
        // value; PatchValue.Set applies the supplied value (including explicit null for the
        // clearable fields alias/kid). The post-merge invariant (providerId non-blank, and
        // at least one of alias/kid non-null) is validated below before any persistence write.
        val newProviderId = patch.keyInfo.providerId.applyOr(existing.kmsProviderId)
        val newAlias = patch.keyInfo.alias.applyOr(existing.kmsKeyAlias)
        val newKid = patch.keyInfo.kid.applyOr(existing.kmsKid)
        // Post-merge invariant: either fully unbound (all three null) or fully bound
        // (providerId non-blank + at least one of alias/kid non-blank). Anything in between
        // is a coherence violation regardless of which sub-fields the caller supplied.
        val hasAnyBindingField =
            !newProviderId.isNullOrBlank() || !newAlias.isNullOrBlank() || !newKid.isNullOrBlank()
        if (hasAnyBindingField) {
            if (newProviderId.isNullOrBlank()) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "KMS binding requires a non-blank providerId after merge",
                    ),
                )
            }
            if (newAlias.isNullOrBlank() && newKid.isNullOrBlank()) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "KMS binding requires at least one of alias or kid after merge",
                    ),
                )
            }
        }
        val newExpiresAt = patch.expiresAt.applyOr(existing.expiresAt)
        val newRevokedAt = patch.revokedAt.applyOr(existing.revokedAt)
        val newExtensionPropertiesJson =
            when (val e = patch.extensionProperties) {
                is PatchValue.Unchanged -> existing.extensionPropertiesJson
                is PatchValue.Set -> e.value?.let { json.encodeToString(JsonObject.serializer(), it) }
            }

        val valueRelationProvided = patch.valueVerificationRelation is PatchValue.Set
        val refRelationsProvided = patch.referenceVerificationRelations is PatchValue.Set

        val newInlineInJson =
            when (val v = patch.valueVerificationRelation) {
                is PatchValue.Unchanged -> {
                    existing.inlineInJson
                }

                is PatchValue.Set -> {
                    v.value?.let {
                        json.encodeToString(ListSerializer(String.serializer()), listOf(it.value))
                    }
                }
            }

        val now = clock.now()
        val replacement =
            existing.copy(
                controller = newController,
                kmsProviderId = newProviderId,
                kmsKeyAlias = newAlias,
                kmsKid = newKid,
                expiresAt = newExpiresAt,
                revokedAt = newRevokedAt,
                inlineInJson = newInlineInJson,
                extensionPropertiesJson = newExtensionPropertiesJson,
                updatedAt = now,
            )

        // Relationship-row recomposition.
        val existingRefRows =
            composite.verificationRelationship.filter { rel ->
                rel.entryRefDidUrl?.let { normalizeServiceDidUrl(composite.record.did, it) } == existing.vmId
            }
        val existingEmbeddedRows =
            composite.verificationRelationship.filter { rel -> rel.entryEmbeddedVmId == existing.id }
        val requestedRefPurposes = (patch.referenceVerificationRelations as? PatchValue.Set)?.value
        val withoutReplacedRows =
            composite.verificationRelationship.filterNot { rel ->
                (refRelationsProvided && rel in existingRefRows) ||
                    (valueRelationProvided && rel in existingEmbeddedRows)
            }
        val replacementRefRows =
            requestedRefPurposes
                ?.map { purpose ->
                    DidVerificationRelationshipRecord(
                        id = idGen.next(),
                        didRecordId = composite.record.id,
                        purpose = purpose.value,
                        entryEmbeddedVmId = null,
                        entryRefDidUrl = existing.vmId,
                        ordinal = composite.verificationRelationship.count { it.purpose == purpose.value },
                        createdAt = now,
                        updatedAt = now,
                    )
                }.orEmpty()
        val replacementEmbeddedRow =
            if (valueRelationProvided) {
                (patch.valueVerificationRelation as PatchValue.Set).value?.let { purpose ->
                    DidVerificationRelationshipRecord(
                        id = idGen.next(),
                        didRecordId = composite.record.id,
                        purpose = purpose.value,
                        entryEmbeddedVmId = existing.id,
                        entryRefDidUrl = null,
                        ordinal = composite.verificationRelationship.count { it.purpose == purpose.value },
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            } else {
                null
            }
        val updatedRelationships =
            if (!refRelationsProvided && !valueRelationProvided) {
                composite.verificationRelationship
            } else {
                withoutReplacedRows + replacementRefRows + listOfNotNull(replacementEmbeddedRow)
            }

        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                verificationMethod =
                    composite.verificationMethod.map {
                        if (it.id == existing.id) replacement else it
                    },
                verificationRelationship = updatedRelationships,
            )
        repository.save(updated).getOrElse { return Err(it) }
        return updated.toManagedDidWithJwks()
    }

    // ============ Aggregate replace ============

    @Suppress("LongMethod", "ReturnCount")
    override suspend fun replaceDidAggregate(
        did: String,
        replacement: DidAggregateReplacement,
    ): IdkResult<ManagedDid, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "DID replacement") {
            it.canUpdate()
        }.getOrElse { return Err(it) }

        val now = clock.now()
        val recordId = composite.record.id

        // Resolve embedded relationship references to local VM UUIDs; reject dangling FKs.
        val vmUuidsByAnyId =
            buildMap<String, String> {
                composite.verificationMethod.forEach { vm ->
                    put(vm.id, vm.id)
                    put(vm.vmId, vm.id)
                }
            }
        val newRelationships =
            replacement.relationships.mapIndexed { idx, entry ->
                val resolvedEmbedded =
                    entry.verificationMethodId?.let {
                        vmUuidsByAnyId[it] ?: return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Relationship references unknown verification method: $it",
                            ),
                        )
                    }
                if ((resolvedEmbedded == null) == (entry.referenceDidUrl == null)) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Each relationship entry must set exactly one of verificationMethodId or referenceDidUrl",
                        ),
                    )
                }
                DidVerificationRelationshipRecord(
                    id = idGen.next(),
                    didRecordId = recordId,
                    purpose = entry.purpose,
                    entryEmbeddedVmId = resolvedEmbedded,
                    entryRefDidUrl = entry.referenceDidUrl,
                    ordinal = idx,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val newServices =
            replacement.services.mapIndexed { idx, svc ->
                DidServiceRecord(
                    id = idGen.next(),
                    didRecordId = recordId,
                    serviceId = svc.id,
                    typeJson = json.encodeToString(ListSerializer(String.serializer()), svc.type),
                    serviceEndpointJson = json.encodeToString(JsonElement.serializer(), svc.serviceEndpoint),
                    extensionPropertiesJson =
                        if (svc.extensions.isEmpty()) {
                            null
                        } else {
                            json.encodeToString(JsonObject.serializer(), JsonObject(svc.extensions))
                        },
                    ordinal = idx,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val newControllers =
            replacement.controllers.mapIndexed { idx, value ->
                DidControllerRecord(
                    id = idGen.next(),
                    didRecordId = recordId,
                    controllerDid = value,
                    ordinal = idx,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val newAlsoKnownAs =
            replacement.alsoKnownAs.mapIndexed { idx, value ->
                DidAlsoKnownAsRecord(
                    id = idGen.next(),
                    didRecordId = recordId,
                    akaUri = value,
                    ordinal = idx,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val newEquivalentIds =
            replacement.equivalentIds.mapIndexed { idx, value ->
                DidEquivalentIdRecord(
                    id = idGen.next(),
                    didRecordId = recordId,
                    equivalentDid = value,
                    ordinal = idx,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val newContexts =
            replacement.contexts.mapIndexed { idx, uri ->
                DidDocumentContextRecord(
                    id = idGen.next(),
                    didRecordId = recordId,
                    contextUri = uri,
                    ordinal = idx,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        val replaced =
            composite.copy(
                record =
                    composite.record.copy(
                        alias = replacement.alias ?: composite.record.alias,
                        canonicalId = replacement.canonicalId ?: composite.record.canonicalId,
                        deactivated = replacement.deactivated ?: composite.record.deactivated,
                        updatedAt = now,
                        updatedById = actorId,
                    ),
                controller = newControllers,
                alsoKnownAs = newAlsoKnownAs,
                equivalentId = newEquivalentIds,
                context = newContexts,
                verificationRelationship = newRelationships,
                service = newServices,
            )
        repository.save(replaced).getOrElse { return Err(it) }
        return replaced.toManagedDidWithJwks()
    }

    // ============ Key mappings ============

    override suspend fun listKeyMappings(did: String): IdkResult<List<DidKeyMapping>, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        return Ok(composite.keyMapping.map { it.toDidKeyMapping() })
    }

    @Suppress("ReturnCount")
    override suspend fun addKeyMapping(
        did: String,
        input: AddKeyMappingInput,
    ): IdkResult<DidKeyMapping, IdkError> {
        val alias =
            input.keyInfo.alias ?: input.keyInfo.kid ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Key mapping requires at least one of keyInfo.alias or keyInfo.kid"),
            )
        val providerId =
            input.keyInfo.providerId ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Key mapping requires keyInfo.providerId"),
            )
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        // IDK-18 o77: input.verificationMethodId is polymorphic (callers may pass either the local VM
        // record UUID or the wire DID URL). Resolve to the actual VM record up front so the FK column
        // can hold the UUID and the wire-form is preserved in verificationMethodDidUrl for API surfaces.
        val targetVm =
            composite.verificationMethod.firstOrNull { vm ->
                vm.id == input.verificationMethodId || vm.vmId == input.verificationMethodId
            } ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Verification method '${input.verificationMethodId}' not found on DID '$did'",
                ),
            )
        val keyref =
            if (keyReferenceStore.isAvailable) {
                val byAlias = keyReferenceStore.findByAlias(tenantId, alias, providerId).getOrElse { return Err(it) }
                val resolved =
                    byAlias ?: input.keyInfo.kid?.let {
                        keyReferenceStore.findByKid(tenantId, it, providerId).getOrElse { return Err(it) }
                    }
                resolved ?: return Err(
                    IdkError.fromString(
                        message =
                            "Key reference not found in keyref-store: providerId=$providerId, alias=$alias" +
                                (input.keyInfo.kid?.let { ", kid=$it" } ?: ""),
                        code = "UNSUPPORTED_OPERATION",
                        category = ErrorCategory.UNPROCESSABLE_ENTITY,
                    ),
                )
            } else {
                null
            }
        val now = clock.now()
        val purposesJson = input.purposes.toWireJsonArray()
        val mapping =
            DidKeyMappingRecord(
                id = idGen.next(),
                didRecordId = composite.record.id,
                verificationMethodId = targetVm.id,
                verificationMethodDidUrl = targetVm.vmIdAuthored ?: targetVm.vmId,
                kmsProviderId = providerId,
                kmsKeyAlias = alias,
                kmsKid = input.keyInfo.kid,
                keyReferenceId = keyref?.id,
                purposesJson = purposesJson,
                createdAt = now,
                updatedAt = now,
            )
        repository.saveKeyMapping(mapping).getOrElse { return Err(it) }
        return Ok(mapping.toDidKeyMapping())
    }

    /** Local projector — DidKeyMappingRecord → manager-facing [DidKeyMapping]. */
    private fun DidKeyMappingRecord.toDidKeyMapping(): DidKeyMapping =
        DidKeyMapping(
            id = id,
            verificationMethodId = verificationMethodDidUrl ?: verificationMethodId,
            kmsKeyAlias = kmsKeyAlias,
            kmsProviderId = kmsProviderId,
            purposesJson = purposesJson,
        )

    override suspend fun removeKeyMapping(
        did: String,
        mappingId: String,
    ): IdkResult<Unit, IdkError> {
        // Verify the mapping actually belongs to *this* DID before deleting. Without this
        // guard a same-tenant caller holding DID A's session could delete DID B's mappings by
        // id alone, and a missing row would silently return "deleted = true".
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        composite.keyMapping.firstOrNull { it.id == mappingId } ?: return Err(
            IdkError.NOT_FOUND_ERROR(message = "Key mapping not found on DID $did: $mappingId"),
        )
        return repository.deleteKeyMapping(tenantId, mappingId)
    }

    // ============ Verification relationships ============

    override suspend fun listVerificationRelationships(
        did: String,
        purpose: String?,
    ): IdkResult<List<VerificationRelationship>, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        val vmById = composite.verificationMethod.associateBy { it.id }
        val rels =
            composite.verificationRelationship
                .filter { purpose == null || it.purpose == purpose }
                .sortedBy { it.ordinal }
                .map { rel ->
                    VerificationRelationship(
                        id = rel.id,
                        purpose = rel.purpose,
                        embeddedVerificationMethodId = rel.entryEmbeddedVmId?.let { vmById[it]?.vmId },
                        referencedVerificationMethodId = rel.entryRefDidUrl,
                        ordinal = rel.ordinal,
                    )
                }
        return Ok(rels)
    }

    override suspend fun addVerificationRelationship(
        did: String,
        verificationMethodId: String,
        purpose: String,
        embed: Boolean,
    ): IdkResult<VerificationRelationship, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "verification relationship addition") {
            it.keyManagement.addition
        }.getOrElse { return Err(it) }

        val absoluteVmId = if (verificationMethodId.startsWith("#")) did + verificationMethodId else verificationMethodId
        val targetVm =
            composite.verificationMethod.firstOrNull { it.vmId == absoluteVmId }
                ?: composite.verificationMethod.firstOrNull { it.vmId.endsWith("#$verificationMethodId") }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verification method not found: $verificationMethodId"))

        val duplicate =
            composite.verificationRelationship.any { rel ->
                rel.purpose == purpose &&
                    if (embed) rel.entryEmbeddedVmId == targetVm.id else rel.entryRefDidUrl == targetVm.vmId
            }
        if (duplicate) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message =
                        "Relationship already exists: $purpose -> ${if (embed) targetVm.id + " (embedded)" else targetVm.vmId}",
                ),
            )
        }

        val now = clock.now()
        val ordinal = composite.verificationRelationship.count { it.purpose == purpose }
        val newRel =
            DidVerificationRelationshipRecord(
                id = idGen.next(),
                didRecordId = composite.record.id,
                purpose = purpose,
                entryEmbeddedVmId = if (embed) targetVm.id else null,
                entryRefDidUrl = if (embed) null else targetVm.vmId,
                ordinal = ordinal,
                createdAt = now,
                updatedAt = now,
            )

        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                verificationRelationship = composite.verificationRelationship + newRel,
            )
        repository.save(updated).getOrElse { return Err(it) }
        return Ok(
            VerificationRelationship(
                id = newRel.id,
                purpose = newRel.purpose,
                embeddedVerificationMethodId = if (embed) targetVm.vmId else null,
                referencedVerificationMethodId = newRel.entryRefDidUrl,
                ordinal = newRel.ordinal,
            ),
        )
    }

    override suspend fun removeVerificationRelationship(
        did: String,
        relationshipId: String,
    ): IdkResult<Unit, IdkError> {
        val composite = loadDidDetail(did).getOrElse { return Err(it) }
        requireCapability(composite.record.method, "verification relationship removal") {
            it.keyManagement.removal
        }.getOrElse { return Err(it) }

        val target =
            composite.verificationRelationship.firstOrNull { it.id == relationshipId }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Relationship not found: $relationshipId"))

        val now = clock.now()
        val updated =
            composite.copy(
                record = composite.record.copy(updatedAt = now, updatedById = actorId),
                verificationRelationship = composite.verificationRelationship.filter { it.id != target.id },
            )
        repository.save(updated).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    // ============ Document cache ============

    override suspend fun getCachedDocument(did: String): IdkResult<DidDocument?, IdkError> {
        loadDidDetail(did).getOrElse { return Err(it) }
        val encoded = externalDocumentCache.getTenant(tenantId, did) ?: return Ok(null)
        val document = decodeCachedDocument(did, encoded).getOrElse { return Err(it) }
        return Ok(document)
    }

    override suspend fun resolveAndCache(did: String): IdkResult<DidDocument, IdkError> {
        val detail = loadDidDetail(did).getOrElse { return Err(it) }
        externalDocumentCache.removeTenant(tenantId, did)
        val resolution = resolverRegistry.resolve(did).getOrElse { return Err(it) }
        val document =
            resolution.didDocument
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Resolver returned no document for $did"))
        cacheResolvedResolution(did = did, method = detail.record.method, resolution = resolution, now = clock.now())
        return Ok(document)
    }

    override suspend fun invalidateCache(did: String): IdkResult<Unit, IdkError> {
        loadDidDetail(did).getOrElse { return Err(it) }
        externalDocumentCache.removeTenant(tenantId, did)
        return Ok(Unit)
    }

    override suspend fun refreshExternalDocument(did: String): IdkResult<DidDocument, IdkError> {
        val detail = loadDidDetail(did).getOrElse { return Err(it) }
        if (detail.record.role != DidRole.EXTERNAL) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Cache refresh is only valid for imported (EXTERNAL) DIDs: $did",
                ),
            )
        }
        externalDocumentCache.removeTenant(tenantId, did)
        val resolution = resolverRegistry.resolve(did).getOrElse { return Err(it) }
        val document =
            resolution.didDocument
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Resolver returned no document for $did"))
        cacheResolvedResolution(did = did, method = detail.record.method, resolution = resolution, now = clock.now())
        return Ok(document)
    }

    private fun parseDidMethod(did: String): String? = ParsedDid.tryParse(did)?.method

    private fun DidService.toRecord(
        didRecordId: String,
        ordinal: Int,
        now: Instant
    ): DidServiceRecord =
        DidServiceRecord(
            id = idGen.next(),
            didRecordId = didRecordId,
            serviceId = id,
            typeJson = json.encodeToString(ListSerializer(String.serializer()), type),
            serviceEndpointJson =
                json.encodeToString(
                    kotlinx.serialization.json.JsonElement
                        .serializer(),
                    serviceEndpoint,
                ),
            extensionPropertiesJson =
                if (extensions.isEmpty()) {
                    null
                } else {
                    json.encodeToString(
                        kotlinx.serialization.json.JsonObject
                            .serializer(),
                        kotlinx.serialization.json.JsonObject(extensions),
                    )
                },
            ordinal = ordinal,
            createdAt = now,
            updatedAt = now,
        )

    // ============ Helpers ============

    private suspend fun loadDidDetail(did: String): IdkResult<DidDetail, IdkError> {
        val composite =
            repository.findByDid(tenantId, did).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID not found: $did"))
        return Ok(composite)
    }

    private fun requireProvider(method: String): IdkResult<DidProvider, IdkError> =
        providerRegistry.getProvider(method)?.let { Ok(it) }
            ?: Err(IdkError.NOT_FOUND_ERROR(message = "No provider registered for DID method: $method"))

    private fun VerificationMethodConfig.toAddKeyOptions(currentDocument: DidDocument? = null): AddKeyOptions =
        AddKeyOptions(
            currentDocument = currentDocument,
            publicKeyJwk = publicKeyJwk,
            verificationMethodId = verificationMethodId,
            verificationMethodType = type,
            controller = controller,
            purposes = purposes,
            config = this,
        )

    private fun List<VerificationPurpose>.toWireJsonArray(): String = json.encodeToString(ListSerializer(String.serializer()), this.map { it.value })

    private suspend fun findKeyReferenceId(
        providerId: String,
        alias: String,
        kid: String?,
        role: DidRole = DidRole.EXTERNAL,
    ): IdkResult<String?, IdkError> {
        if (!keyReferenceStore.isAvailable) {
            return if (role == DidRole.MANAGED) {
                Err(
                    IdkError.INVALID_STATE(
                        message =
                            "findKeyReferenceId: IDK-17 key-reference store is unavailable, " +
                                "but this is required for MANAGED VMs (providerId=$providerId, alias=$alias). " +
                                "Refusing to persist an unbound key.",
                    )
                )
            } else {
                Ok(null)
            }
        }
        val record =
            if (kid != null) {
                keyReferenceStore
                    .findByKid(tenantId = tenantId, kid = kid, providerId = providerId)
                    .getOrElse { return Err(it) }
                    ?: keyReferenceStore
                        .findByAlias(tenantId = tenantId, alias = alias, providerId = providerId)
                        .getOrElse { return Err(it) }
            } else {
                keyReferenceStore
                    .findByAlias(tenantId = tenantId, alias = alias, providerId = providerId)
                    .getOrElse { return Err(it) }
            }
        return Ok(record?.id)
    }

    private suspend fun resolveJwkFromKms(
        kmsProviderId: String,
        kmsKeyAlias: String,
        vmId: String,
    ): IdkResult<Jwk, IdkError> {
        val info =
            keyManagerService.getKey(
                KeyInfo<JwkType>(alias = kmsKeyAlias, providerId = kmsProviderId),
            )
        val jwk =
            when (val key = info.key) {
                is Jwk -> key
                is JwkType -> Jwk.from(key)
                else -> null
            } ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Verification method $vmId did not resolve to a JWK from $kmsProviderId/$kmsKeyAlias",
                ),
            )
        return Ok(jwk)
    }

    private fun DidDocument.hydrateVerificationMethods(jwksByVmId: Map<String, Jwk>): DidDocument =
        copy(
            verificationMethod = verificationMethod?.map { vm -> vm.hydrate(jwksByVmId) },
            authentication = authentication.hydrate(jwksByVmId),
            assertionMethod = assertionMethod.hydrate(jwksByVmId),
            keyAgreement = keyAgreement.hydrate(jwksByVmId),
            capabilityInvocation = capabilityInvocation.hydrate(jwksByVmId),
            capabilityDelegation = capabilityDelegation.hydrate(jwksByVmId),
        )

    private fun List<VerificationMethodOrReference>?.hydrate(jwksByVmId: Map<String, Jwk>,): List<VerificationMethodOrReference>? =
        this?.map { entry ->
            val embedded = entry.embedded ?: return@map entry
            VerificationMethodOrReference.fromEmbedded(embedded.hydrate(jwksByVmId))
        }

    private fun VerificationMethod.hydrate(jwksByVmId: Map<String, Jwk>): VerificationMethod = jwksByVmId[id]?.let { jwk -> copy(publicKeyJwk = jwk) } ?: this

    /**
     * Rehydrates managed VMs from the KMS only for legacy rows that do not already carry
     * persisted public-key material. External and direct-JWK VMs keep their stored key material.
     */
    private suspend fun DidDetail.toManagedDidWithJwks(): IdkResult<ManagedDid, IdkError> {
        val managed = toManagedDid()
        val doc = managed.document ?: return Ok(managed)

        if (record.role == DidRole.EXTERNAL) {
            val externalDocument = resolveExternalDocument(record.did, record.method).getOrElse { return Err(it) }
            return Ok(managed.copy(document = externalDocument))
        }

        val jwksByVmId = mutableMapOf<String, Jwk>()
        verificationMethod.forEach { vm ->
            if (vm.publicKeyJwkJson != null || vm.publicKeyMultibase != null || vm.blockchainAccountId != null) {
                return@forEach
            }
            val providerId = vm.kmsProviderId ?: return@forEach
            val alias = vm.kmsKeyAlias ?: return@forEach
            jwksByVmId[vm.vmId] =
                resolveJwkFromKms(
                    kmsProviderId = providerId,
                    kmsKeyAlias = alias,
                    vmId = vm.vmId,
                ).getOrElse { return Err(it) }
        }

        val result = if (jwksByVmId.isEmpty()) managed else managed.copy(document = doc.hydrateVerificationMethods(jwksByVmId))
        cacheManagedDid(record.did, result)
        return Ok(result)
    }

    private suspend fun cacheManagedDid(
        did: String,
        managed: ManagedDid
    ) {
        try {
            val payload = json.encodeToString(ManagedDid.serializer(), managed)
            managedDocumentCache.putTenant(
                tenantId = tenantId,
                key = did,
                value = payload,
                ttl = managedCacheTtlSeconds.seconds,
            )
        } catch (expected: Exception) {
            logger.warn("Failed to cache managed DID document for $did: ${expected.message}")
        }
    }

    private suspend fun cachedManagedDid(did: String): ManagedDid? =
        try {
            managedDocumentCache.getTenant(tenantId, did)?.let { encoded ->
                json.decodeFromString(ManagedDid.serializer(), encoded)
            }
        } catch (expected: Exception) {
            logger.warn("Failed to decode cached managed DID document for $did: ${expected.message}")
            null
        }

    private suspend fun invalidateManagedCache(did: String) {
        managedDocumentCache.removeTenant(tenantId, did)
    }

    /**
     * Returns the cached resolved document for [did] when present, otherwise re-resolves via
     * the resolver registry, populates the cache, and returns the fresh document. The
     * `fallback` (the document reconstructed from the persistence graph) is intentionally
     * unused here: an identity manager must surface resolver failure rather than silently
     * serve a possibly-stale persisted document. Callers that want stale-fallback semantics
     * must opt in explicitly.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun resolveExternalDocument(
        did: String,
        method: String,
    ): IdkResult<DidDocument, IdkError> {
        externalDocumentCache.getTenant(tenantId, did)?.let { encoded ->
            return decodeCachedDocument(did, encoded)
        }
        val resolution = resolverRegistry.resolve(did).getOrElse { return Err(it) }
        val document =
            resolution.didDocument
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Resolver returned no document for external DID: $did (${resolution.didResolutionMetadata.error ?: "no error detail"})",
                    ),
                )
        cacheResolvedResolution(did = did, method = method, resolution = resolution, now = clock.now())
        return Ok(document)
    }

    private suspend fun cacheResolvedResolution(
        did: String,
        method: String,
        resolution: DidResolutionResult,
        now: Instant,
    ) {
        // Cache the full resolver output so didDocumentMetadata (created/updated/deactivated/
        // nextUpdate/versionId/nextVersionId/equivalentId/canonicalId) and the resolution
        // metadata (content type etc.) survive to subsequent reads.
        val payload =
            CachedDidResolution(
                resolutionJson = json.encodeToString(DidResolutionResult.serializer(), resolution),
                resolvedAt = now,
            )
        externalDocumentCache.putTenant(
            tenantId = tenantId,
            key = did,
            value = json.encodeToString(CachedDidResolution.serializer(), payload),
            ttl = externalCacheTtl(method).seconds,
        )
    }

    private fun decodeCachedDocument(
        did: String,
        encoded: String,
    ): IdkResult<DidDocument, IdkError> =
        try {
            val payload = json.decodeFromString(CachedDidResolution.serializer(), encoded)
            val document =
                json.decodeFromString(DidResolutionResult.serializer(), payload.resolutionJson).didDocument
                    ?: return Err(
                        IdkError.INVALID_STATE(
                            message = "Cached resolution for $did has no didDocument",
                        ),
                    )
            Ok(document)
        } catch (expected: Exception) {
            logger.warn("Failed to decode cached DID document for $did: ${expected.message}")
            Err(
                IdkError.INVALID_STATE(
                    message = "Poisoned cache entry for $did: ${expected.message}",
                ),
            )
        }

    private fun externalCacheTtl(method: String): Long =
        resolverRegistry
            .getCapabilities(method)
            ?.resolution
            ?.cacheTtlSeconds
            ?.takeIf { ttl -> ttl > 0 }
            ?: externalCacheTtlSeconds

    // Internal so manager-impl tests can decode cache entries and assert that the full
    // resolver metadata round-trips. Not part of the public API.
    @Serializable
    internal data class CachedDidResolution(
        // JSON-encoded [DidResolutionResult] — preserves didResolutionMetadata (content type)
        // and didDocumentMetadata (created/updated/deactivated/nextUpdate/versionId/
        // nextVersionId/equivalentId/canonicalId) alongside the document itself.
        val resolutionJson: String,
        val resolvedAt: Instant,
    )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val didManager: DidManager
    }
}
