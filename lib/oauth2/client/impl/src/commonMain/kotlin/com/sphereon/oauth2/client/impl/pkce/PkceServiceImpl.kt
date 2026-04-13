/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.pkce

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.client.service.PkceService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of PkceService that delegates to command implementations (RFC 7636)
 *
 * This service follows the Command/Service pattern for consistency with other IDK services
 * like SdJwtService and JwtService.
 *
 * @property createPkceCommand Command for creating PKCE challenge/verifier pairs
 * @property verifyPkceCommand Command for verifying PKCE during token exchange
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<PkceService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("PkceServiceImpl", exact = true)
class PkceServiceImpl(
    private val createPkceCommand: CreatePkceCommand,
    private val verifyPkceCommand: VerifyPkceCommand,
) : PkceService {
    /**
     * Implementation of Commands that exposes the injected command instances
     */
    inner class CommandsImpl : PkceService.Commands {
        override val createPkce: CreatePkceCommand = this@PkceServiceImpl.createPkceCommand
        override val verifyPkce: VerifyPkceCommand = this@PkceServiceImpl.verifyPkceCommand
    }

    override val commands: PkceService.Commands = CommandsImpl()

    // Delegate service methods to commands

    override suspend fun createPkce(args: CreatePkceArgs): IdkResult<PkceData, IdkError> = createPkceCommand.execute(args)

    override suspend fun verifyPkce(args: VerifyPkceArgs): IdkResult<EmptyResult, IdkError> = verifyPkceCommand.execute(args)

    @ContributesTo(scope = SessionScope::class)
    interface Graph {
        val pkceService: PkceService
    }
}
