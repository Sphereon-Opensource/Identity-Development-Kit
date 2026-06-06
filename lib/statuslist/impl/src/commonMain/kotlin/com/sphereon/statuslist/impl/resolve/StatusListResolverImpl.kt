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

package com.sphereon.statuslist.impl.resolve

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.impl.codec.StatusListCodec
import com.sphereon.statuslist.impl.envelope.BitstringStatusListEnvelope
import com.sphereon.statuslist.impl.envelope.TokenStatusListEnvelope
import com.sphereon.statuslist.spi.StatusListResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonObject

/**
 * Default [StatusListResolver]: fetches the hosted token, verifies its JWS signature via the shared
 * [JwtService], decodes the bit at the requested index, and reports the status. CWT verification is
 * not yet wired. (Caching honouring `ttl`/`exp` is a follow-up.)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListResolver>())
class StatusListResolverImpl(
    private val httpClientFactory: HttpClientFactory,
    private val jwtService: JwtService,
) : StatusListResolver {
    // Create the HTTP client once per resolver instance and reuse its connection pool across calls
    // rather than allocating a new one per resolveStatus.
    private val client by lazy { httpClientFactory.createClient(HttpClientOptions.createDefault()) }

    override suspend fun resolveStatus(args: ResolveStatusArgs): IdkResult<ResolvedStatus, IdkError> {
        val (token, contentType) =
            try {
                val response = client.get(args.uri)
                response.bodyAsText().trim() to (response.headers[HttpHeaders.ContentType] ?: "")
            } catch (e: Exception) {
                return Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "fetch failed", e))
            }

        // Reject unsupported envelopes up front, before spending a JWS verification.
        if (contentType.contains("cwt", ignoreCase = true)) {
            return Err(StatusListErrors.verificationFailed(args.uri, "CWT status lists are not yet supported"))
        }

        val spec = args.expectedSpec ?: inferSpec(contentType)
        val valid =
            jwtService
                .verifyJws(VerifyJwsArgs(jws = JwsCompact(token)))
                .getOrElse { return Err(it) }
                .isValid
        if (!valid) return Err(StatusListErrors.verificationFailed(args.uri, "signature invalid"))

        return try {
            val payload =
                decodeJwtPayload(token)
                    ?: return Err(StatusListErrors.verificationFailed(args.uri, "malformed JWT payload"))
            val (encodedList, bits, purpose) =
                when (spec) {
                    StatusListSpec.TOKEN_STATUS_LIST -> {
                        val content = TokenStatusListEnvelope.parse(payload)
                        Triple(content.encodedList, content.bitsPerStatus, null)
                    }

                    StatusListSpec.BITSTRING_STATUS_LIST -> {
                        val content = BitstringStatusListEnvelope.parse(payload)
                        Triple(content.encodedList, content.statusSize, StatusPurpose.fromValue(content.statusPurpose))
                    }
                }
            val bitset = StatusListCodec.decode(encodedList, bits, spec)
            val value = bitset.get(args.index)
            Ok(
                ResolvedStatus(
                    value = value,
                    purpose = purpose,
                    valid = value == StatusValues.VALID,
                    statusListUri = args.uri,
                ),
            )
        } catch (e: IllegalArgumentException) {
            Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "decode failed", e))
        }
    }

    private fun inferSpec(contentType: String): StatusListSpec =
        when {
            contentType.contains(StatusListContentTypes.VC_JWT, ignoreCase = true) ||
                contentType.contains("vc+jwt", ignoreCase = true) -> StatusListSpec.BITSTRING_STATUS_LIST

            else -> StatusListSpec.TOKEN_STATUS_LIST
        }

    private fun decodeJwtPayload(jwt: String): JsonObject? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            JwsUtils.decodeBase64UrlToJson(parts[1])
        } catch (_: Exception) {
            null
        }
    }
}
