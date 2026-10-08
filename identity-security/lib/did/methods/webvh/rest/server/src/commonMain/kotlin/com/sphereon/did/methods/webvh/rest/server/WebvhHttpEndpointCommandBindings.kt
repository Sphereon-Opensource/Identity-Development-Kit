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

package com.sphereon.did.methods.webvh.rest.server

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.ServiceCommandEndpoint
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.command.ValidateWebvhTrustServiceCommand
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

/**
 * Keyed, lazy HTTP wrappers for the WebVH service commands.
 *
 * The adapter depends only on [com.sphereon.core.api.http.command.HttpEndpointCommandRegistry].
 * Metro therefore constructs just the wrapper selected by the matched route.
 */
@ContributesTo(SessionScope::class)
interface WebvhHttpEndpointCommandBindings {
    @Provides
    @IntoMap
    @StringKey(CreateWebvhDidServiceCommand.COMMAND_ID)
    fun create(
        command: CreateWebvhDidServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, CreateWebvhDidServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(UpdateWebvhDidServiceCommand.COMMAND_ID)
    fun update(
        command: UpdateWebvhDidServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, UpdateWebvhDidServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(DeactivateWebvhDidServiceCommand.COMMAND_ID)
    fun deactivate(
        command: DeactivateWebvhDidServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, DeactivateWebvhDidServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(CreateWitnessProofServiceCommand.COMMAND_ID)
    fun createWitnessProof(
        command: CreateWitnessProofServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, CreateWitnessProofServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(UpdateWitnessFileServiceCommand.COMMAND_ID)
    fun updateWitnessFile(
        command: UpdateWitnessFileServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, UpdateWitnessFileServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(FetchWebvhLogServiceCommand.COMMAND_ID)
    fun fetchLog(
        command: FetchWebvhLogServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, FetchWebvhLogServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(ReplayWebvhLogServiceCommand.COMMAND_ID)
    fun replayLog(
        command: ReplayWebvhLogServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, ReplayWebvhLogServiceCommand.ENDPOINT, execution)

    @Provides
    @IntoMap
    @StringKey(ValidateWebvhTrustServiceCommand.COMMAND_ID)
    fun validateTrust(
        command: ValidateWebvhTrustServiceCommand,
        execution: SessionExecution,
    ): HttpEndpointCommand = ServiceCommandEndpoint(command, ValidateWebvhTrustServiceCommand.ENDPOINT, execution)
}
