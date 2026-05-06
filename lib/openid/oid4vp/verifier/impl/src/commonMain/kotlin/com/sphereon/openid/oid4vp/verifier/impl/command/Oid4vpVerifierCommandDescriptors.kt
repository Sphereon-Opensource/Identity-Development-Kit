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

package com.sphereon.openid.oid4vp.verifier.impl.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.openid.oid4vp.verifier.impl.BuildAuthorizationRequestUriCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.CreateAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.CreateSignedAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.HandleDirectPostResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.ParseAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.RetrieveAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.ValidateAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.VerifyHolderBindingCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface Oid4vpVerifierCommandDescriptors {
    @Provides @IntoMap
    @StringKey(CreateSignedAuthorizationRequestCommand.COMMAND_ID)
    fun createSignedAuthorizationRequest(impl: CreateSignedAuthorizationRequestCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BuildAuthorizationRequestUriCommand.COMMAND_ID)
    fun buildAuthorizationRequestUri(impl: BuildAuthorizationRequestUriCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RetrieveAuthorizationResponseCommand.COMMAND_ID)
    fun retrieveAuthorizationResponse(impl: RetrieveAuthorizationResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(HandleDirectPostResponseCommand.COMMAND_ID)
    fun handleDirectPostResponse(impl: HandleDirectPostResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyHolderBindingCommand.COMMAND_ID)
    fun verifyHolderBinding(impl: VerifyHolderBindingCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ValidateAuthorizationResponseCommand.COMMAND_ID)
    fun validateAuthorizationResponse(impl: ValidateAuthorizationResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseAuthorizationResponseCommand.COMMAND_ID)
    fun oid4vpParseAuthorizationResponse(impl: ParseAuthorizationResponseCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationRequestCommand.COMMAND_ID)
    fun createAuthorizationRequest(impl: CreateAuthorizationRequestCommandImpl): ServiceCommand<*, *, *> = impl
}
