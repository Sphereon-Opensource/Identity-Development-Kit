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

package com.sphereon.openid.oid4vp.common.impl.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ParseClientIdCommand
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.ResolveScopeCommand
import com.sphereon.openid.oid4vp.common.ValidateClientIdCommand
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataCommand
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationCommand
import com.sphereon.openid.oid4vp.common.impl.ParseClientIdCommandImpl
import com.sphereon.openid.oid4vp.common.impl.ParseTransactionDataCommandImpl
import com.sphereon.openid.oid4vp.common.impl.ResolveScopeCommandImpl
import com.sphereon.openid.oid4vp.common.impl.ValidateClientIdCommandImpl
import com.sphereon.openid.oid4vp.common.impl.VerifyTransactionDataCommandImpl
import com.sphereon.openid.oid4vp.common.impl.VerifyVerifierAttestationCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface Oid4vpCommonCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ParseClientIdCommand.COMMAND_ID)
    fun parseClientId(impl: ParseClientIdCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyTransactionDataCommand.COMMAND_ID)
    fun verifyTransactionData(impl: VerifyTransactionDataCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ParseTransactionDataCommand.COMMAND_ID)
    fun parseTransactionData(impl: ParseTransactionDataCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveScopeCommand.COMMAND_ID)
    fun resolveScope(impl: ResolveScopeCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(VerifyVerifierAttestationCommand.COMMAND_ID)
    fun verifyVerifierAttestation(impl: VerifyVerifierAttestationCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ValidateClientIdCommand.COMMAND_ID)
    fun validateClientId(impl: ValidateClientIdCommandImpl): ServiceCommand<*, *, *> = impl
}
