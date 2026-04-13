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

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusServiceCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface Oid4vpUniversalCommandDescriptors {
    @Provides @IntoMap
    @StringKey(CreateAuthRequestServiceCommand.COMMAND_ID)
    fun createAuthRequest(impl: CreateAuthRequestServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetAuthRequestStatusServiceCommand.COMMAND_ID)
    fun getAuthRequestStatus(impl: GetAuthRequestStatusServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteAuthRequestServiceCommand.COMMAND_ID)
    fun deleteAuthRequest(impl: DeleteAuthRequestServiceCommandImpl): ServiceCommand<*, *> = impl
}
