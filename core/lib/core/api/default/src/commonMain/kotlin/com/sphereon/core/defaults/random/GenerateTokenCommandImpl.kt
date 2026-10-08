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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.GenerateTokenArgs
import com.sphereon.core.api.random.GenerateTokenCommand
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.session.CommandAdapter
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default CSPRNG-backed implementation of [GenerateTokenCommand].
 *
 * Scope: [AppScope] — CSPRNG is stateless infrastructure; a single instance serves
 * the whole application.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<GenerateTokenCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("GenerateTokenCommandImpl", exact = true)
class GenerateTokenCommandImpl :
    CommandAdapter<GenerateTokenArgs, StringResult, IdkError>(id = GenerateTokenCommand.COMMAND_ID),
    GenerateTokenCommand {
    override val id: String get() = GenerateTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GenerateTokenArgs

    override suspend fun doExecute(
        args: GenerateTokenArgs,
        applyDuring: (GenerateTokenArgs) -> GenerateTokenArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        require(applied.lengthBytes >= 0) { "lengthBytes must be >= 0, got ${applied.lengthBytes}" }
        val bytes = CryptographyRandom.Default.nextBytes(applied.lengthBytes)
        return Ok(StringResult(bytes.encodeTo(applied.encoding)))
    }
}
