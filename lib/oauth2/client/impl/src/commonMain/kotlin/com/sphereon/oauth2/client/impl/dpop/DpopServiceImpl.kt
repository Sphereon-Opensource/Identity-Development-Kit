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

package com.sphereon.oauth2.client.impl.dpop

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.service.DpopService
import com.sphereon.oauth2.common.command.CreateDpopProofArgs
import com.sphereon.oauth2.common.command.CreateDpopProofCommand
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.DpopProofResult
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of DpopService that delegates to command implementations
 *
 * Follows the Command/Service pattern:
 * - Commands are injected and stored
 * - Service methods delegate to commands for execution
 * - Commands are exposed via the `commands` property
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DpopService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DpopServiceImpl", exact = true)
class DpopServiceImpl(
    private val createDpopProofCommand: CreateDpopProofCommand,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
) : DpopService {
    /**
     * Implementation of Commands that exposes the injected command instances
     */
    inner class CommandsImpl : DpopService.Commands {
        override val createDpopProof = this@DpopServiceImpl.createDpopProofCommand
        override val verifyDpopProof = this@DpopServiceImpl.verifyDpopProofCommand
    }

    override val commands: DpopService.Commands = CommandsImpl()

    override suspend fun createDpopProof(
        options: CreateDpopProofOptions,
        publicJwk: Jwk,
    ): IdkResult<DpopProofResult, IdkError> = createDpopProofCommand.execute(CreateDpopProofArgs(options, publicJwk))

    override suspend fun verifyDpopProof(options: VerifyDpopProofOptions): IdkResult<VerifyDpopProofResult, IdkError> = verifyDpopProofCommand.execute(options)

    @ContributesTo(scope = SessionScope::class)
    interface Graph {
        val dpopService: DpopService
    }
}
