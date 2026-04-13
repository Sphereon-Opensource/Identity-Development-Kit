/*
 * Copyright 2024-2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.resource.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2ResourceServerCommandBindings {
    @Provides
    fun verifyJwt(registry: SessionScopedCommandRegistry): VerifyJwtCommand =
        registry.get(VerifyJwtCommand.COMMAND_ID) as? VerifyJwtCommand
            ?: error("No binding for ${VerifyJwtCommand.COMMAND_ID}")

    @Provides
    fun validateAccessToken(registry: SessionScopedCommandRegistry): ValidateAccessTokenCommand =
        registry.get(ValidateAccessTokenCommand.COMMAND_ID) as? ValidateAccessTokenCommand
            ?: error("No binding for ${ValidateAccessTokenCommand.COMMAND_ID}")

    @Provides
    fun resourceServerIntrospectToken(registry: SessionScopedCommandRegistry): IntrospectTokenCommand =
        registry.get(IntrospectTokenCommand.COMMAND_ID) as? IntrospectTokenCommand
            ?: error("No binding for ${IntrospectTokenCommand.COMMAND_ID}")

    @Provides
    fun resourceServerVerifyDpopProof(registry: SessionScopedCommandRegistry): VerifyDpopProofCommand =
        registry.get(VerifyDpopProofCommand.COMMAND_ID) as? VerifyDpopProofCommand
            ?: error("No binding for ${VerifyDpopProofCommand.COMMAND_ID}")
}
