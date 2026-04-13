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

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of JweService that delegates to command implementations
 *
 * This implementation aggregates all JWE command implementations and provides
 * a unified service interface. It uses dependency injection to obtain command
 * instances and delegates all operations to them.
 *
 * The service is scoped to the session, meaning a single instance is shared
 * within a session context.
 */
@dev.zacsweers.metro.Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JweService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweServiceImpl", exact = true)
class JweServiceImpl(
    private val prepareJweCommand: PrepareJweCommand,
    private val createJweCompactCommand: CreateJweCompactCommand,
    private val createJweJsonFlattenedCommand: CreateJweJsonFlattenedCommand,
    private val createJweJsonGeneralCommand: CreateJweJsonGeneralCommand,
    private val decryptJweCommand: DecryptJweCommand
) : JweService {

    /**
     * Component interface for DI graph integration
     * Allows other components to access the JweService
     */
    @dev.zacsweers.metro.ContributesTo(scope = SessionScope::class)
    interface Component {
        val jweService: JweService
    }

    /**
     * Implementation of Commands that exposes the injected command instances
     */
    inner class CommandsImpl : JweService.Commands {
        override val prepareJwe: PrepareJweCommand = this@JweServiceImpl.prepareJweCommand
        override val createJweCompact: CreateJweCompactCommand = this@JweServiceImpl.createJweCompactCommand
        override val createJweJsonFlattened: CreateJweJsonFlattenedCommand = this@JweServiceImpl.createJweJsonFlattenedCommand
        override val createJweJsonGeneral: CreateJweJsonGeneralCommand = this@JweServiceImpl.createJweJsonGeneralCommand
        override val decryptJwe: DecryptJweCommand = this@JweServiceImpl.decryptJweCommand
    }

    override val commands: JweService.Commands = CommandsImpl()

    // Delegate service methods to commands

    override suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError> =
        prepareJweCommand.execute(args)

    override suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError> =
        createJweCompactCommand.execute(args)

    override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError> =
        createJweJsonFlattenedCommand.execute(args)

    override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError> =
        createJweJsonGeneralCommand.execute(args)

    override suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> =
        decryptJweCommand.execute(args)
}
