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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.random.NextBytesArgs
import com.sphereon.core.api.random.NextBytesCommand
import com.sphereon.core.api.service.ByteArrayResult
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
 * Default CSPRNG-backed implementation of [NextBytesCommand].
 *
 * Scope: [AppScope] — CSPRNG is stateless infrastructure.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NextBytesCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("NextBytesCommandImpl", exact = true)
class NextBytesCommandImpl :
    CommandAdapter<NextBytesArgs, ByteArrayResult, IdkError>(id = NextBytesCommand.COMMAND_ID),
    NextBytesCommand {
    override val id: String get() = NextBytesCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is NextBytesArgs

    override suspend fun doExecute(
        args: NextBytesArgs,
        applyDuring: (NextBytesArgs) -> NextBytesArgs,
    ): IdkResult<ByteArrayResult, IdkError> {
        val applied = applyDuring(args)
        require(applied.length >= 0) { "length must be >= 0, got ${applied.length}" }
        return Ok(ByteArrayResult(CryptographyRandom.Default.nextBytes(applied.length)))
    }
}
