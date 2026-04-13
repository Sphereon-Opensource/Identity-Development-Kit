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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommand
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommandImpl
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface OAuth2CommonCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ValidateIdTokenCommand.COMMAND_ID)
    fun validateIdToken(impl: ValidateIdTokenCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyJarmResponseCommand.COMMAND_ID)
    fun verifyJarmResponse(impl: VerifyJarmResponseCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CreateJarmResponseCommand.COMMAND_ID)
    fun createJarmResponse(impl: CreateJarmResponseCommandImpl): ServiceCommand<*, *> = impl
}
