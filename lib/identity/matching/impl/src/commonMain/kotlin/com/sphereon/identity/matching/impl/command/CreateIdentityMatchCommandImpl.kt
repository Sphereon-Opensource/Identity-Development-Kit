/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.identity.matching.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.command.CreateIdentityMatchCommand
import com.sphereon.identity.matching.error.IdentityMatchingError
import com.sphereon.identity.matching.model.CreateIdentityMatchArgs
import com.sphereon.identity.matching.model.IdentityMatch
import com.sphereon.identity.matching.store.IdentityMatchStore
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateIdentityMatchCommandImpl", exact = true)
class CreateIdentityMatchCommandImpl(
    execution: SessionExecution,
    private val store: IdentityMatchStore
) : TypedServiceCommandAdapter<CreateIdentityMatchArgs, IdentityMatch>(
    commandId = CreateIdentityMatchCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateIdentityMatchArgs>(),
    outputTypeToken = typeToken<IdentityMatch>(),
), CreateIdentityMatchCommand {

    override val commandId: String get() = CreateIdentityMatchCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateIdentityMatchArgs

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun doExecute(
        args: CreateIdentityMatchArgs,
        applyDuring: (CreateIdentityMatchArgs) -> CreateIdentityMatchArgs
    ): IdkResult<IdentityMatch, IdkError> {
        val applied = applyDuring(args)

        // Check for duplicate
        val existing = store.findByIdentifierHash(applied.tenantId, applied.identifierHash, applied.identifierType)
        if (existing != null) {
            return Err(IdkError.fromDTO(IdentityMatchingError.DuplicateMatch(identifierHash = applied.identifierHash)))
        }

        val now = Clock.System.now()
        val match = IdentityMatch(
            id = Uuid.random().toString(),
            identifierHash = applied.identifierHash,
            identifierType = applied.identifierType,
            internalIdentityId = applied.internalIdentityId,
            tenantId = applied.tenantId,
            metadata = applied.metadata,
            hashKeyVersion = applied.hashKeyVersion,
            createdAt = now,
            lastUsedAt = now
        )
        return Ok(store.create(match))
    }
}
