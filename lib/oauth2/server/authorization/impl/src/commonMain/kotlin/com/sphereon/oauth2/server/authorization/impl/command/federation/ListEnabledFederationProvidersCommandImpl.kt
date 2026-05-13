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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.federation.EnabledFederationProviders
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersCommand
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation of [ListEnabledFederationProvidersCommand]: returns the registry's enabled
 * provider list. Injecting [FederationProviderRegistry] directly matches the registry-based
 * pattern used by every other federation ServiceCommand.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListEnabledFederationProvidersCommand>())
class ListEnabledFederationProvidersCommandImpl(
    execution: SessionExecution,
    private val providerRegistry: FederationProviderRegistry,
) : TypedServiceCommandAdapter<ListEnabledFederationProvidersArgs, EnabledFederationProviders, AuthenticationError>(
        commandId = ListEnabledFederationProvidersCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListEnabledFederationProvidersArgs>(),
        outputTypeToken = typeToken<EnabledFederationProviders>(),
    ),
    ListEnabledFederationProvidersCommand {
    override val commandId: String get() = ListEnabledFederationProvidersCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ListEnabledFederationProvidersArgs

    override suspend fun doExecute(
        args: ListEnabledFederationProvidersArgs,
        applyDuring: (ListEnabledFederationProvidersArgs) -> ListEnabledFederationProvidersArgs,
    ): IdkResult<EnabledFederationProviders, AuthenticationError> = Ok(EnabledFederationProviders(providers = providerRegistry.enabled()))
}
