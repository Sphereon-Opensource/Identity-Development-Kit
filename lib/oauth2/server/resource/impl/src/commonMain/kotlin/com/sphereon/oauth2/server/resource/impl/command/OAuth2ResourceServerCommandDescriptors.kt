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

package com.sphereon.oauth2.server.resource.impl.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.resource.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofCommand
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface OAuth2ResourceServerCommandDescriptors {
    @Provides @IntoMap
    @StringKey(IntrospectTokenCommand.COMMAND_ID)
    fun resourceServerIntrospectToken(impl: ResourceServerIntrospectTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ValidateAccessTokenCommand.COMMAND_ID)
    fun validateAccessToken(impl: ValidateAccessTokenCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyDpopProofCommand.COMMAND_ID)
    fun resourceServerVerifyDpopProof(impl: ResourceServerVerifyDpopProofCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyJwtCommand.COMMAND_ID)
    fun verifyJwt(impl: VerifyJwtCommandImpl): ServiceCommand<*, *, *> = impl
}
