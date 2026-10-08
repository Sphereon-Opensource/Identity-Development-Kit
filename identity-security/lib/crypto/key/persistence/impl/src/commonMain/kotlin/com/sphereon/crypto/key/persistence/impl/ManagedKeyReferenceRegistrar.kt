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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.key.persistence.ManagedKeyAuthorityRegistration
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

internal const val ALREADY_PLATFORM_MANAGED =
    "The key is already managed by this platform; registering it as an external reference would change its ownership"
internal const val DIFFERENT_KEY_ID =
    "The alias is already registered for a different key id or key version"
internal const val DIFFERENT_KEY_MATERIAL =
    "The alias is already registered with different public key material"
internal const val DIFFERENT_ALIAS =
    "The key is already registered under a different alias"

/**
 * Centralizes all key reference indexing logic.
 *
 * Called from [ManagedKeyStoreSelector.storeKey], [ManagedKeyStoreSelector.deleteKey],
 * key generation commands, and explicit onboarding endpoints.
 */
@Inject
@SingleIn(SessionScope::class)
class ManagedKeyReferenceRegistrar(
    private val keyReferenceStore: KeyReferenceStore,
    private val execution: SessionExecution,
    private val authorityRegistration: ManagedKeyAuthorityRegistration =
        ManagedKeyAuthorityRegistration { _, _ -> true },
) {
    private val tenantId: String
        get() = execution.sessionContext.context.tenant.tenantId

    // TODO: extract principalId from auth context when available
    private val principalId: String? get() = null

    /**
     * Index a managed key (created or stored via the platform) in the reference store.
     * Silently skips if no persistence is available.
     */
    suspend fun indexManagedKey(key: ManagedKeyInfoType<*>): IdkResult<KeyReferenceRecord?, IdkError> {
        if (!keyReferenceStore.isAvailable) {
            return Ok(null)
        }
        val record =
            KeyReferenceRecord.fromManagedKey(
                key = key,
                tenantId = tenantId,
                principalId = principalId,
            )
        return keyReferenceStore.upsert(record).map { it }
    }

    /**
     * Register an externally-owned provider key reference (onboarding).
     * Called from the `POST /keys/register` endpoint to bring an existing provider key
     * into the managed reference store.
     *
     * Returns an error if no persistence implementation is available.
     */
    suspend fun registerKeyReference(
        providerId: String,
        alias: String,
        kid: String? = null,
        keyType: com.sphereon.crypto.core.generic.KeyTypeMapping? = null,
        signatureAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm? = null,
        keyVisibility: com.sphereon.crypto.core.KeyVisibility? = null,
        keyEncoding: com.sphereon.crypto.core.KeyEncoding? = null,
        publicKeyJwk: String? = null,
    ): IdkResult<KeyReferenceRecord, IdkError> {
        if (!keyReferenceStore.isAvailable) {
            return Err(
                IdkError.UNKNOWN_ERROR(message = "No key reference store available. Add a persistence module (SQLite, PostgreSQL, or MySQL) to the classpath."),
            )
        }
        if (keyReferenceStore.ownershipHistoryCapability != KeyReferenceHistoryCapability.DURABLE) {
            return Err(
                IdkError.fromString(
                    code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
                    message = "External key registration requires a durable key reference ownership history store",
                ),
            )
        }
        val existing =
            keyReferenceStore
                .findByAlias(tenantId, alias, providerId)
                .getOrElse { error -> return Err(error) }

        // A registration never changes a known key: ownership is set once when the key becomes
        // known. Registering the same external key again returns it unchanged; anything else
        // about an already known key is a conflict.
        if (existing != null) {
            if (existing.controlMode != ResourceControlMode.EXTERNALLY_MANAGED) return Err(registrationConflict(ALREADY_PLATFORM_MANAGED))
            if (kid != null && existing.kid != kid) return Err(registrationConflict(DIFFERENT_KEY_ID))
            if (publicKeyJwk != null && existing.publicKeyJwk != publicKeyJwk) return Err(registrationConflict(DIFFERENT_KEY_MATERIAL))
            return Ok(existing)
        }

        if (kid != null) {
            val existingKid =
                keyReferenceStore
                    .findByKid(tenantId, kid, providerId)
                    .getOrElse { error -> return Err(error) }
            if (existingKid != null) {
                return Err(
                    registrationConflict(
                        if (existingKid.controlMode != ResourceControlMode.EXTERNALLY_MANAGED) ALREADY_PLATFORM_MANAGED else DIFFERENT_ALIAS,
                    ),
                )
            }
        }

        val now = Clock.System.now()
        val record =
            KeyReferenceRecord(
                id = generateId(),
                tenantId = tenantId,
                alias = alias,
                kid = kid,
                providerId = providerId,
                origin = Origin.EXTERNAL,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                keyType = keyType,
                signatureAlgorithm = signatureAlgorithm,
                keyVisibility = keyVisibility,
                keyEncoding = keyEncoding,
                publicKeyJwk = publicKeyJwk,
                createdAt = now,
                createdById = principalId,
                updatedAt = now,
                updatedById = principalId,
                deletedAt = null,
                deletedById = null,
            )
        val persisted = keyReferenceStore.upsert(record)
        if (persisted.isErr) return persisted
        if (!authorityRegistration.register(providerId, persisted.value.id)) {
            return Err(
                IdkError.UNKNOWN_ERROR(
                    message = "The key reference authority binding could not be registered",
                ),
            )
        }
        return Ok(persisted.value)
    }

    /**
     * Remove a key reference after provider deletion.
     * Silently skips if no persistence is available.
     */
    suspend fun removeKeyReference(keyInfo: KeyInfoType<*>): IdkResult<Boolean, IdkError> {
        if (!keyReferenceStore.isAvailable) {
            return Ok(false)
        }
        val alias = keyInfo.alias
        val kid = keyInfo.kid
        val providerId = keyInfo.providerId
        return when {
            alias != null && providerId != null -> keyReferenceStore.delete(tenantId, alias, providerId)
            kid != null -> keyReferenceStore.deleteByKid(tenantId, kid, providerId)
            else -> Ok(false)
        }
    }

    private fun registrationConflict(message: String): IdkError =
        IdkError.fromString(code = KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT, message = message)

    private fun generateId(): String =
        kotlin.uuid.Uuid
            .random()
            .toString()
}
