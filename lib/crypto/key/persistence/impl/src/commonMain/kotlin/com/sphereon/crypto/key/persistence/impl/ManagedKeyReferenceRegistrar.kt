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
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceHistoryCapability
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

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

        if (existing != null && existing.kid != null && kid != null && existing.kid != kid) {
            return Err(
                IdkError.fromString(
                    code = KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT,
                    message = "The key reference identity conflicts with the existing registration",
                ),
            )
        }
        if (existing != null && existing.publicKeyJwk != null && publicKeyJwk != null && existing.publicKeyJwk != publicKeyJwk) {
            return Err(
                IdkError.fromString(
                    code = KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT,
                    message = "The key reference public material conflicts with the existing registration",
                ),
            )
        }

        if (kid != null) {
            val existingKid =
                keyReferenceStore
                    .findByKid(tenantId, kid, providerId)
                    .getOrElse { error -> return Err(error) }
            if (existingKid != null && existingKid.id != existing?.id) {
                return Err(
                    IdkError.fromString(
                        code = KeyReferenceStoreErrorCodes.EXTERNAL_KEY_REGISTRATION_CONFLICT,
                        message = "The canonical provider key identifier is already registered under another alias",
                    ),
                )
            }
        }

        val now = Clock.System.now()
        val record =
            KeyReferenceRecord(
                id = existing?.id ?: generateId(),
                tenantId = tenantId,
                alias = alias,
                kid = kid ?: existing?.kid,
                providerId = providerId,
                origin = Origin.EXTERNAL,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                keyType = keyType,
                signatureAlgorithm = signatureAlgorithm,
                keyVisibility = keyVisibility,
                keyEncoding = keyEncoding,
                publicKeyJwk = publicKeyJwk ?: existing?.publicKeyJwk,
                createdAt = existing?.createdAt ?: now,
                createdById = existing?.createdById ?: principalId,
                updatedAt = now,
                updatedById = principalId,
                deletedAt = null,
                deletedById = null,
            )
        return keyReferenceStore.upsert(record)
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

    private fun generateId(): String =
        kotlin.uuid.Uuid
            .random()
            .toString()
}
