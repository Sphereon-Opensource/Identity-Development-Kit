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

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.core.api.log.LogService
import kotlin.time.TimeSource

/**
 * Per-request stage timing for the token endpoint.
 *
 * A `POST /token` that takes seconds currently arrives as one duration with nothing inside it: the
 * authorization server resolves its client registry, verifies a client credential, resolves a
 * signing key, and signs a JWT, and any one of those can be the cost. Distributed tracing is not
 * available at this layer, so attribution has to come from the command itself.
 *
 * Each recorder emits exactly one line per request naming every stage it measured, which keeps the
 * volume proportional to token requests rather than to internal steps. Stage names are fixed
 * literals and durations are integers, so nothing request-specific or credential-derived is logged.
 */
internal class TokenPathStageTimings(
    private val operation: String,
) {
    private val stages = mutableListOf<Pair<String, Long>>()

    /** Runs [block], recording its wall-clock cost against [stage] whether it succeeds or fails. */
    suspend fun <T> record(
        stage: String,
        block: suspend () -> T,
    ): T {
        val started = TimeSource.Monotonic.markNow()
        return try {
            block()
        } finally {
            stages += stage to started.elapsedNow().inWholeMilliseconds
        }
    }

    fun report(
        log: LogService,
        outcome: String,
    ) {
        format(outcome)?.let { line -> log.info(line) }
    }

    /** Null when nothing was measured, so a short-circuited request emits no line at all. */
    fun format(outcome: String): String? {
        if (stages.isEmpty()) return null
        val measured = stages.sumOf { (_, millis) -> millis }
        return "VDX_OAUTH2_TOKEN_PATH_TIMING operation=$operation outcome=$outcome measuredMs=$measured " +
            stages.joinToString(separator = " ") { (stage, millis) -> "$stage=$millis" }
    }
}

internal object TokenPathStage {
    const val PARSE_REQUEST: String = "parse-request"
    const val DPOP_PROOF_VERIFICATION: String = "dpop-proof-verification"
    const val CLIENT_AUTHENTICATION_COMMAND_RESOLUTION: String = "client-authentication-command-resolution"
    const val CLIENT_AUTHENTICATION: String = "client-authentication"
    const val GRANT_DISPATCH: String = "grant-dispatch"

    const val CLIENT_REGISTRY_VIEW: String = "client-registry-view"
    const val CLIENT_LOOKUP: String = "client-lookup"
    const val CREDENTIAL_VERIFICATION: String = "credential-verification"

    const val SIGNING_IDENTIFIER_RESOLUTION: String = "signing-identifier-resolution"
    const val AS_CONFIG_RESOLUTION: String = "as-config-resolution"
    const val SIGNING_TENANT_DERIVATION: String = "signing-tenant-derivation"
    const val ACTIVE_SIGNING_KEY_RESOLUTION: String = "active-signing-key-resolution"
    const val JWS_SIGNING: String = "jws-signing"
    const val TOKEN_STORAGE: String = "token-storage"
}
