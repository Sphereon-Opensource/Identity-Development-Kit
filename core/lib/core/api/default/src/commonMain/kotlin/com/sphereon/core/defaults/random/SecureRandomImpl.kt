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

package com.sphereon.core.defaults.random

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.GenerateTokenArgs
import com.sphereon.core.api.random.GenerateTokenCommand
import com.sphereon.core.api.random.NextBytesArgs
import com.sphereon.core.api.random.NextBytesCommand
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.ByteArrayResult
import com.sphereon.core.api.service.StringResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * AppScope implementation of [SecureRandom] that delegates to [GenerateTokenCommand]
 * and [NextBytesCommand]. A SessionScope variant can be added later if session-bound
 * interceptors are needed for CSPRNG operations.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<SecureRandom>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecureRandomImpl", exact = true)
class SecureRandomImpl(
    private val generateTokenCommand: GenerateTokenCommand,
    private val nextBytesCommand: NextBytesCommand,
) : SecureRandom {
    override suspend fun generateToken(args: GenerateTokenArgs): IdkResult<StringResult, IdkError> = generateTokenCommand.execute(args)

    override suspend fun nextBytes(args: NextBytesArgs): IdkResult<ByteArrayResult, IdkError> = nextBytesCommand.execute(args)

    override val commands: SecureRandom.Commands = CommandsImpl()

    private inner class CommandsImpl : SecureRandom.Commands {
        override val generateToken: GenerateTokenCommand = this@SecureRandomImpl.generateTokenCommand
        override val nextBytes: NextBytesCommand = this@SecureRandomImpl.nextBytesCommand
    }
}
