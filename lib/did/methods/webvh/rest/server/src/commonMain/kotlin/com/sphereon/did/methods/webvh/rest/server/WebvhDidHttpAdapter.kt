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
 *
 */

package com.sphereon.did.methods.webvh.rest.server

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.PublicApiHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.webvh.command.CreateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.CreateWitnessProofServiceCommand
import com.sphereon.did.methods.webvh.command.DeactivateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.FetchWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.ReplayWebvhLogServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWebvhDidServiceCommand
import com.sphereon.did.methods.webvh.command.UpdateWitnessFileServiceCommand
import com.sphereon.did.methods.webvh.command.ValidateWebvhTrustServiceCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Bundle of the `did:webvh` service commands the REST adapter dispatches
 * to. Constructor-injected so [WebvhDidHttpAdapter] keeps its parameter list
 * within the IDK convention (≤ 7 params; we use 2 with this bundle).
 */
@Inject
@SingleIn(SessionScope::class)
data class WebvhCommands(
    val create: CreateWebvhDidServiceCommand,
    val update: UpdateWebvhDidServiceCommand,
    val deactivate: DeactivateWebvhDidServiceCommand,
    val createWitnessProof: CreateWitnessProofServiceCommand,
    val updateWitnessFile: UpdateWitnessFileServiceCommand,
    val fetchLog: FetchWebvhLogServiceCommand,
    val replayLog: ReplayWebvhLogServiceCommand,
    val validateTrust: ValidateWebvhTrustServiceCommand,
)

/**
 * Exposes the `did:webvh` lifecycle service commands at `/api/v1/did/webvh`.
 *
 * Resolution is handled separately via the existing
 * `UniversalResolverHttpAdapter` (`/1.0/identifiers/...`); this adapter is
 * only needed by deployments that host the lifecycle (controller-side
 * create / update / deactivate / update-witness-file) or the witness-side
 * `create-witness-proof` over REST. Verifier-only deployments omit this
 * module entirely so they do not pull in ktor-server transitively.
 */
@Inject
@SingleIn(SessionScope::class)
@Named(WebvhDidHttpAdapter.ID)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class WebvhDidHttpAdapter(
    execution: SessionExecution,
    commands: WebvhCommands,
) : PublicApiHttpAdapter(
        id = ID,
        sessionExecution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = BASE_PATH,
            ),
    ) {
    override val serviceCommands: List<ServiceCommand<*, *, *>> =
        listOf(
            commands.create,
            commands.update,
            commands.deactivate,
            commands.createWitnessProof,
            commands.updateWitnessFile,
            commands.fetchLog,
            commands.replayLog,
            commands.validateTrust,
        )

    companion object {
        const val ID = "did.webvh.http"
        const val BASE_PATH = "/api/v1/did/webvh"
    }
}
