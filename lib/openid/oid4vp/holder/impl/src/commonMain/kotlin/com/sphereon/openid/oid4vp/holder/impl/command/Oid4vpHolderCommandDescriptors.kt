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

package com.sphereon.openid.oid4vp.holder.impl.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.command.ResolveClientMetadataCommand
import com.sphereon.openid.oid4vp.holder.impl.CreateAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.ParseAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.ResolveAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.SubmitAuthorizationResponseCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface Oid4vpHolderCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ResolveClientMetadataCommand.COMMAND_ID)
    fun resolveClientMetadata(impl: ResolveClientMetadataCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveAuthorizationRequestCommand.COMMAND_ID)
    fun resolveAuthorizationRequest(impl: ResolveAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(SubmitAuthorizationResponseCommand.COMMAND_ID)
    fun submitAuthorizationResponse(impl: SubmitAuthorizationResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateAuthorizationResponseCommand.COMMAND_ID)
    fun createAuthorizationResponse(impl: CreateAuthorizationResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ParseAuthorizationRequestCommand.COMMAND_ID)
    fun parseAuthorizationRequest(impl: ParseAuthorizationRequestCommandImpl): ServiceCommand<*, *> = impl
}
