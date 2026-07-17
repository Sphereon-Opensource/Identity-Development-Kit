/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.store.CredentialRequestIdentity
import com.sphereon.openid.oid4vci.issuer.store.CredentialRequestIdentityStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialRequestIdentityStore>())
class KvCredentialRequestIdentityStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
) : CredentialRequestIdentityStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci.credential-request-identities",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = CredentialRequestIdentity.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.credential-request-identities",
            scopeBinding = KvStoreScopeBinding.TENANT,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(resolveEffectiveStoreConfig(), execution)
    }

    private fun resolveEffectiveStoreConfig(): KvStoreConfigBase {
        val configured = runCatching { kvStoreService.getStoreConfig(storeConfig.id) }.getOrNull()
        val effective = configured ?: storeConfig
        require(effective.scopeBinding == storeConfig.scopeBinding) {
            "KV store '${storeConfig.id}' must use scopeBinding=${storeConfig.scopeBinding}, but was ${effective.scopeBinding}"
        }
        return effective
    }

    override suspend fun resolveOrCreate(
        protocolSessionId: String,
        instanceId: String,
        ttlSeconds: Long,
    ): IdkResult<CredentialRequestIdentity, IdkError> {
        val candidate = CredentialRequestIdentity(protocolSessionId = protocolSessionId, instanceId = instanceId)
        require(ttlSeconds > 0) { "ttlSeconds must be positive" }

        val versioning = versioningStore().getOrElse { return Err(it) }
        val existing = versioning.getHead(namespace, candidate.protocolSessionId).getOrElse { return Err(it) }
        if (existing != null) return resolveExisting(existing.value, candidate)

        return when (
            val appended =
                versioning
                    .append(
                        namespace = namespace,
                        key = candidate.protocolSessionId,
                        expectedPreviousVersionId = null,
                        value = candidate,
                        ttl = ttlSeconds.seconds,
                    ).getOrElse { return Err(it) }
        ) {
            is KvVersionAppendResult.Applied -> Ok(appended.entry.value)
            is KvVersionAppendResult.Conflict ->
                appended.currentHead?.value?.let { resolveExisting(it, candidate) }
                    ?: Err(bindingConflict())
        }
    }

    private fun resolveExisting(
        existing: CredentialRequestIdentity,
        candidate: CredentialRequestIdentity,
    ): IdkResult<CredentialRequestIdentity, IdkError> =
        if (existing == candidate) {
            Ok(existing)
        } else {
            Err(bindingConflict())
        }

    private fun versioningStore(): IdkResult<KvStoreVersioning, IdkError> =
        (kv as? KvStoreVersioning)?.let(::Ok)
            ?: Err(
                IdkError.fromString(
                    code = "KV_VERSIONING_REQUIRED",
                    message = "Credential-request identity requires an atomic versioned KV backend",
                ),
            )

    private fun bindingConflict(): IdkError =
        IdkError.INVALID_STATE(
            message = "Credential-request protocol session is already bound to another issuer instance",
        )
}
