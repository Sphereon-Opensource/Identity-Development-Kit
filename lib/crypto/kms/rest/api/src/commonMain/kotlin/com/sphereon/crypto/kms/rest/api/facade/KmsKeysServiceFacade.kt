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

package com.sphereon.crypto.kms.rest.api.facade

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceFacade
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyOutput
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKey
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKeyResponse
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service facade for KMS Keys operations.
 *
 * Provides a convenient API for key management operations while delegating
 * to individual ServiceCommand implementations for transport routing,
 * policy enforcement, and audit logging.
 *
 * **Usage:**
 * ```kotlin
 * // Inject the facade
 * @Inject
 * class MyService(private val kmsFacade: KmsKeysServiceFacade) {
 *     suspend fun getKey(aliasOrKid: String): KeyInfo? {
 *         return kmsFacade.getKey(aliasOrKid).getOrNull()?.keyInfo
 *     }
 * }
 * ```
 *
 * **Benefits over direct service injection:**
 * - Policy enforcement via command execution pipeline
 * - Audit logging for all operations
 * - Transport-agnostic (local, HTTP, gRPC)
 * - Consistent error handling via IdkResult
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsKeysServiceFacade", exact = true)
interface KmsKeysServiceFacade : ServiceFacade {
    override val serviceId: String get() = "kms.keys"

    /**
     * Gets a key by alias or kid.
     *
     * @param aliasOrKid Key alias or kid
     * @param providerId Optional provider ID to scope the lookup
     * @return The key info wrapped in GetKeyResponse
     */
    suspend fun getKey(
        aliasOrKid: String,
        providerId: String? = null,
    ): IdkResult<GetKeyResponse, IdkError>

    /**
     * Lists all keys.
     *
     * @param providerId Optional provider ID to filter keys
     * @return List of key infos wrapped in ListKeysResponse
     */
    suspend fun listKeys(providerId: String? = null): IdkResult<ListKeysResponse, IdkError>

    /**
     * Stores a key.
     *
     * @param storeKey Key storage request
     * @return The stored key info wrapped in StoreKeyResponse
     */
    suspend fun storeKey(storeKey: StoreKey): IdkResult<StoreKeyResponse, IdkError>

    /**
     * Generates a new key.
     *
     * @param generateKey Key generation request
     * @return The generated key pair wrapped in GenerateKeyResponse
     */
    suspend fun generateKey(generateKey: GenerateKeyGlobal): IdkResult<GenerateKeyResponse, IdkError>

    /**
     * Deletes a key.
     *
     * @param aliasOrKid Key alias or kid to delete
     * @param providerId Optional provider ID to scope the deletion
     * @return Deletion result
     */
    suspend fun deleteKey(
        aliasOrKid: String,
        providerId: String? = null,
    ): IdkResult<DeleteKeyOutput, IdkError>

    /**
     * Registers an existing provider key for platform use.
     *
     * @param providerId The provider that holds the key
     * @param alias Alias to register the key under
     * @param kid Optional kid for the key
     * @return Registration result
     */
    suspend fun registerKeyReference(
        providerId: String,
        alias: String,
        kid: String? = null,
    ): IdkResult<RegisterKeyReferenceResponse, IdkError>
}
