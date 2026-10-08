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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.ClientApplicationResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [ClientApplicationResolver]: no client-to-application mapping exists, every
 * resolution returns `Ok(null)` and the AS runs in application-agnostic mode. Richer runtimes
 * contribute their own resolver with
 * `@ContributesBinding(SessionScope::class, replaces = [NoneClientApplicationResolver::class])`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ClientApplicationResolver>())
open class NoneClientApplicationResolver : ClientApplicationResolver {
    override suspend fun resolveApplicationId(
        clientId: String,
        requestHost: String?,
    ): IdkResult<String?, IdkError> = Ok(null)
}
