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

@file:OptIn(ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.provider.FederatedIdentityLinker
import com.sphereon.oauth2.server.authorization.provider.LinkFederatedSessionRequest
import com.sphereon.oauth2.server.authorization.provider.LinkedFederatedSession
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.ExperimentalTime

/**
 * Pure-IDK fallback. Returns the upstream `sub` as the local identity id and writes no session
 * record. EDK contributes a real linker (`DefaultFederatedIdentityLinker`) at the same scope
 * with `replaces = [NoOpFederatedIdentityLinker::class]`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<FederatedIdentityLinker>())
class NoOpFederatedIdentityLinker : FederatedIdentityLinker {
    override suspend fun linkFederatedSession(request: LinkFederatedSessionRequest,): IdkResult<LinkedFederatedSession, IdkError> = Ok(LinkedFederatedSession(localIdentityId = request.upstreamSub))
}
