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

package com.sphereon.sdjwt.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommandImpl
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommandImpl
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommandImpl
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface SdJwtCommandDescriptors {
    @Provides @IntoMap
    @StringKey(VerifySdJwtVcCommand.COMMAND_ID)
    fun verifySdJwtVc(impl: VerifySdJwtVcCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifySdJwtVcPresentationCommand.COMMAND_ID)
    fun verifySdJwtVcPresentation(impl: VerifySdJwtVcPresentationCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveTypeMetadataCommand.COMMAND_ID)
    fun resolveTypeMetadata(impl: ResolveTypeMetadataCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveIssuerMetadataCommand.COMMAND_ID)
    fun resolveIssuerMetadata(impl: ResolveIssuerMetadataCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(IssueSdJwtCommand.COMMAND_ID)
    fun issueSdJwt(impl: IssueSdJwtCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(PresentSdJwtCommand.COMMAND_ID)
    fun presentSdJwt(impl: PresentSdJwtCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(VerifySdJwtCommand.COMMAND_ID)
    fun verifySdJwt(impl: VerifySdJwtCommandImpl): ServiceCommand<*, *> = impl
}
