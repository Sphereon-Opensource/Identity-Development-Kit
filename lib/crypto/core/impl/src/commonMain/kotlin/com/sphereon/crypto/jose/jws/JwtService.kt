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

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.command.*
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of JwtService that delegates to command implementations
 */
@dev.zacsweers.metro.Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwtService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwtServiceImpl", exact = true)
class JwtServiceImpl(
    private val prepareJwsCommand: PrepareJwsCommand,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
    private val createJwsJsonFlattenedCommand: CreateJwsJsonFlattenedCommand,
    private val createJwsJsonGeneralCommand: CreateJwsJsonGeneralCommand,
    private val verifyJwsCommand: VerifyJwsCommand
) : JwtService {

    @dev.zacsweers.metro.ContributesTo(scope = SessionScope::class)
    interface Component {
        val jwtService: JwtService
    }

    /**
     * Implementation of Commands that exposes the injected command instances
     */
    inner class CommandsImpl : JwtService.Commands {
        override val prepareJws: PrepareJwsCommand = this@JwtServiceImpl.prepareJwsCommand
        override val createJwsCompact: CreateJwsCompactCommand = this@JwtServiceImpl.createJwsCompactCommand
        override val createJwsJsonFlattened: CreateJwsJsonFlattenedCommand = this@JwtServiceImpl.createJwsJsonFlattenedCommand
        override val createJwsJsonGeneral: CreateJwsJsonGeneralCommand = this@JwtServiceImpl.createJwsJsonGeneralCommand
        override val verifyJws: VerifyJwsCommand = this@JwtServiceImpl.verifyJwsCommand
    }

    override fun assembleJwsGeneral(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonGeneral =
        prepared.assembleGeneral(signatureBytes)

    override fun assembleJwsFlattened(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonFlattened =
        prepared.assembleFlattened(signatureBytes)

    override fun assembleJwsCompact(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwtCompactResult =
        prepared.assembleCompact(signatureBytes)

    override val commands: JwtService.Commands = CommandsImpl()

    // Delegate service methods to commands
    override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> =
        prepareJwsCommand.execute(args)

    override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> =
        createJwsCompactCommand.execute(args)

    override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> =
        createJwsJsonFlattenedCommand.execute(args)

    override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> =
        createJwsJsonGeneralCommand.execute(args)

    override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> =
        verifyJwsCommand.execute(args)
}
