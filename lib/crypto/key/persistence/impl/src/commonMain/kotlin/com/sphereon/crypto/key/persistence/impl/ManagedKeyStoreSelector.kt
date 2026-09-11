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

package com.sphereon.crypto.key.persistence.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode.AUTO
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode.ITERATING
import com.sphereon.crypto.core.kms.ManagedKeyStoreMode.PERSISTENT
import com.sphereon.crypto.core.kms.ManagedKeyStoreModeResolver
import com.sphereon.crypto.core.kms.ManagedKeyStoreService
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceResolutionException
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.sphereon.crypto.key.persistence.toKeyReference
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.kms.keystore.managed.ManagedKeyStoreWithProviderLookups
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Strategy selector for [ManagedKeyStoreService] that composes the iterating store
 * with the persistent key reference store based on the configured [ManagedKeyStoreMode].
 *
 * - [ITERATING]: delegates all operations to the iterating store (provider iteration)
 * - [PERSISTENT]: `listKeys()` is served from the database; `getKey()` still resolves through providers
 * - [AUTO]: selects [PERSISTENT] if a key reference store is available on the classpath, else [ITERATING]
 */
@Inject
@SingleIn(SessionScope::class)
class ManagedKeyStoreSelector(
    private val iteratingStore: ManagedKeyStoreWithProviderLookups,
    private val keyReferenceStore: KeyReferenceStore,
    private val modeResolver: ManagedKeyStoreModeResolver,
    private val registrar: ManagedKeyReferenceRegistrar,
    private val execution: SessionExecution,
) : ManagedKeyStoreService {
    private val effectiveMode: ManagedKeyStoreMode
        get() {
            val mode = modeResolver.resolve()
            return when (mode) {
                PERSISTENT -> {
                    if (!keyReferenceStore.isAvailable) {
                        error("Persistent key store mode configured but no KeyReferenceStore implementation on classpath")
                    }
                    PERSISTENT
                }

                AUTO -> {
                    if (keyReferenceStore.isAvailable) {
                        PERSISTENT
                    } else {
                        ITERATING
                    }
                }

                ITERATING -> {
                    ITERATING
                }
            }
        }

    private val tenantId: String
        get() = execution.sessionContext.context.tenant.tenantId

    override val settings: KeyProviderSettings? get() = iteratingStore.settings

    override fun keyVisibility(): KeyVisibility = iteratingStore.keyVisibility()

    /**
     * In [PERSISTENT] mode, served entirely from the key reference database (no provider iteration).
     * In [ITERATING] mode, delegates to the iterating store which aggregates from all providers.
     */
    override suspend fun listKeys(): Array<ManagedKeyReference> =
        when (effectiveMode) {
            PERSISTENT -> {
                keyReferenceStore
                    .findAll(tenantId)
                    .getOrElse { error ->
                        throw IllegalStateException("Failed to list keys from persistent store: ${error.message}")
                    }.map { it.toKeyReference() }
                    .toTypedArray()
            }

            // AUTO is resolved to PERSISTENT or ITERATING by effectiveMode; this branch is for exhaustiveness
            ITERATING, AUTO -> {
                iteratingStore.listKeys()
            }
        }

    /**
     * In [PERSISTENT] mode, the filter is pushed down to the database query.
     * In [ITERATING] mode, keys are listed from all providers and filtered in-memory.
     */
    override suspend fun listKeys(filter: ManagedKeyReferenceFilter): Array<ManagedKeyReference> =
        when (effectiveMode) {
            PERSISTENT -> {
                keyReferenceStore
                    .findAll(tenantId, filter)
                    .getOrElse { error ->
                        throw IllegalStateException("Failed to list keys from persistent store: ${error.message}")
                    }.map { it.toKeyReference() }
                    .toTypedArray()
            }

            // Default implementation filters in-memory for iterating mode
            ITERATING, AUTO -> {
                super.listKeys(filter)
            }
        }

    override suspend fun findRegisteredKeyReference(
        aliasOrKid: String,
        providerId: String?,
    ): ManagedKeyReference? {
        if (!keyReferenceStore.isAvailable) return null
        val byAlias =
            keyReferenceStore
                .findByAlias(tenantId, aliasOrKid, providerId)
                .getOrElse { error ->
                    throw IllegalStateException("Failed to resolve registered key alias: ${error.message}")
                }
        val record =
            byAlias
                ?: keyReferenceStore
                    .findByKid(tenantId, aliasOrKid, providerId)
                    .getOrElse { error ->
                        throw IllegalStateException("Failed to resolve registered key kid: ${error.message}")
                    }
        return record?.toKeyReference()
    }

    /** Always resolves through providers regardless of mode. */
    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> = iteratingStore.getKey(keyInfo)

    /** Delegates to the provider, then indexes the result in the reference store if available. */
    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        val result = iteratingStore.storeKey(keyInfo, providerId, alias, certChain)
        // A provider that wrote the authoritative index row itself is not indexed again here. The
        // repeat lands under the provider id the returned key reports, which for a provider with
        // more than one address is a second row for one key.
        if (!iteratingStore.maintainsKeyReferenceIndex(providerId)) {
            registrar.indexManagedKey(result)
        }
        return result
    }

    /**
     * Deletes according to the persisted ownership decision when one exists.
     * External references are soft-deleted locally and never reach the provider.
     * Platform-managed references are deleted provider-first and removed locally only after success.
     */
    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        requireDurableHistory()
        val authorities = findOwnershipAuthorities(keyInfo)
        if (authorities.size > 1) {
            val identifier = keyInfo.alias ?: keyInfo.kid.orEmpty()
            throw KeyReferenceResolutionException(
                code = KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE,
                message = "More than one key reference authority matches '$identifier' for this tenant",
            )
        }

        val reference = authorities.singleOrNull()
        if (reference?.deletedAt != null) {
            // A repeated DELETE for an externally managed reference is idempotent. The persisted
            // ownership decision is still authoritative after a new session or process restart;
            // never fall through to a provider delete merely because the active row is gone.
            return reference.controlMode == ResourceControlMode.EXTERNALLY_MANAGED
        }
        if (reference == null && keyInfo.providerId == null) {
            return false
        }

        if (reference == null) {
            return deleteFromProvider(keyInfo)
        }
        if (reference.controlMode == ResourceControlMode.EXTERNALLY_MANAGED) {
            return keyReferenceStore
                .delete(tenantId, reference.alias, reference.providerId)
                .getOrElse { error -> throw PKIException("Failed to delete key reference: ${error.code}") }
        }
        return deleteFromProvider(reference.toProviderKeyInfo())
    }

    private suspend fun deleteFromProvider(keyInfo: KeyInfoType<*>): Boolean {
        val deleted = iteratingStore.deleteKey(keyInfo)
        if (deleted) {
            registrar
                .removeKeyReference(keyInfo)
                .getOrElse { error -> throw PKIException("Failed to remove key reference: ${error.code}") }
        }
        return deleted
    }

    private suspend fun findOwnershipAuthorities(keyInfo: KeyInfoType<*>): List<KeyReferenceRecord> {
        val providerId = keyInfo.providerId
        val identifier = keyInfo.alias ?: keyInfo.kid ?: return emptyList()
        val history =
            keyReferenceStore
                .findAllByAliasIncludingDeleted(tenantId, identifier, providerId)
                .getOrElse { error -> throwHistoryLookupFailure("alias", error.code) } +
                keyReferenceStore
                    .findAllByKidIncludingDeleted(tenantId, identifier, providerId)
                    .getOrElse { error -> throwHistoryLookupFailure("kid", error.code) }

        return history
            .distinctBy { it.id }
            .groupBy { it.providerId }
            .values
            .map { providerHistory ->
                val active = providerHistory.filter { it.deletedAt == null }
                if (active.size > 1) {
                    throw KeyReferenceResolutionException(
                        code = KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE,
                        message = "More than one active key reference matches '$identifier' for this tenant and provider",
                    )
                }
                active.singleOrNull()
                    ?: providerHistory.maxWith(
                        compareBy<KeyReferenceRecord> { it.deletedAt }
                            .thenBy { it.updatedAt }
                            .thenBy { it.createdAt }
                            .thenBy { it.id },
                    )
            }
    }

    private fun throwHistoryLookupFailure(kind: String, code: String): Nothing {
        if (code == KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED) {
            throw KeyReferenceResolutionException(
                code = code,
                message = "Durable key reference ownership history is required for safe deletion",
            )
        }
        throw PKIException("Failed to resolve key history by $kind: $code")
    }

    private fun requireDurableHistory() {
        if (!keyReferenceStore.isAvailable ||
            keyReferenceStore.ownershipHistoryCapability != KeyReferenceHistoryCapability.DURABLE
        ) {
            throw KeyReferenceResolutionException(
                code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
                message = "Durable key reference ownership history is required for safe deletion",
            )
        }
    }

    private fun KeyReferenceRecord.toProviderKeyInfo(): KeyInfoType<Jwk> =
        if (kid != null) {
            KeyInfo(kid = kid, providerId = providerId)
        } else {
            KeyInfo(alias = alias, providerId = providerId)
        }

    /**
     * Replaces the default [ManagedKeyStoreWithProviderLookups.DefaultManagedKeyStoreModule]
     * to provide config-based mode selection when the persistence module is on the classpath.
     */
    @ContributesTo(SessionScope::class, replaces = [ManagedKeyStoreWithProviderLookups.DefaultManagedKeyStoreModule::class])
    interface PersistentManagedKeyStoreModule {
        companion object {
            @Provides
            fun provideManagedKeyStoreService(selector: ManagedKeyStoreSelector): ManagedKeyStoreService = selector
        }
    }
}
