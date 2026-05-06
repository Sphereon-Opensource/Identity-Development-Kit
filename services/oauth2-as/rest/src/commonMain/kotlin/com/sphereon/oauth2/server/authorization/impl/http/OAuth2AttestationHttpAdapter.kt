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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceResolver
import com.sphereon.oauth2.server.authorization.command.attestation.AttestationChallengeHttpEndpointCommand
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * OAuth 2.0 Attestation-Based Client Authentication (draft-ietf-oauth-attestation-based-client-auth)
 * surface. Hosts the challenge endpoint advertised in discovery as `challenge_endpoint` when
 * `attestation` + `attestationChallengeRequired` are enabled at the server level.
 *
 * Routes:
 * - `GET /attestation-challenge`
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class OAuth2AttestationHttpAdapter(
    execution: SessionExecution,
    asInstanceResolver: OAuth2ServerInstanceResolver,
    asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    private val attestationChallengeEndpointCommand: AttestationChallengeHttpEndpointCommand,
) : AbstractOAuth2HttpAdapter(
        id = ID,
        execution = execution,
        mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/"),
        asInstanceResolver = asInstanceResolver,
        asInstanceIdProvider = asInstanceIdProvider,
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    ) {
    companion object {
        const val ID: String = "OAUTH2_AS_ATTESTATION"
    }

    override val endpointCommands: List<HttpEndpointCommand> = listOf(attestationChallengeEndpointCommand)
}
