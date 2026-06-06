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

package com.sphereon.crypto.kms.rest.server.facade

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyOutput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceInput
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceResponse
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.command.StoreKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.facade.KmsKeysServiceFacade
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKey
import com.sphereon.crypto.kms.rest.api.generated.models.StoreKeyResponse
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [KmsKeysServiceFacade].
 *
 * Delegates all operations to individual ServiceCommand implementations,
 * ensuring consistent policy enforcement, audit logging, and transport handling.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KmsKeysServiceFacade>())
class KmsKeysServiceFacadeImpl(
    private val getKeyCommand: GetKeyServiceCommand,
    private val listKeysCommand: ListKeysServiceCommand,
    private val storeKeyCommand: StoreKeyServiceCommand,
    private val generateKeyCommand: GenerateKeyServiceCommand,
    private val deleteKeyCommand: DeleteKeyServiceCommand,
    private val registerKeyReferenceCommand: RegisterKeyReferenceServiceCommand,
) : KmsKeysServiceFacade {
    override suspend fun getKey(
        aliasOrKid: String,
        providerId: String?,
    ): IdkResult<GetKeyResponse, IdkError> = getKeyCommand.execute(GetKeyInput(aliasOrKid = aliasOrKid, providerId = providerId))

    override suspend fun listKeys(providerId: String?): IdkResult<ListKeysResponse, IdkError> = listKeysCommand.execute(ListKeysInput(providerId = providerId))

    override suspend fun storeKey(storeKey: StoreKey): IdkResult<StoreKeyResponse, IdkError> = storeKeyCommand.execute(storeKey)

    override suspend fun generateKey(generateKey: GenerateKeyGlobal): IdkResult<GenerateKeyResponse, IdkError> = generateKeyCommand.execute(generateKey)

    override suspend fun deleteKey(
        aliasOrKid: String,
        providerId: String?,
    ): IdkResult<DeleteKeyOutput, IdkError> = deleteKeyCommand.execute(DeleteKeyInput(aliasOrKid = aliasOrKid, providerId = providerId))

    override suspend fun registerKeyReference(
        providerId: String,
        alias: String,
        kid: String?,
    ): IdkResult<RegisterKeyReferenceResponse, IdkError> =
        registerKeyReferenceCommand.execute(
            RegisterKeyReferenceInput(providerId = providerId, alias = alias, kid = kid),
        )

    /**
     * This @ContributesTo interface makes the facade accessible as a property
     * in the kotlin-inject SessionGraph.
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val kmsKeysServiceFacade: KmsKeysServiceFacade
    }
}
