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

package com.sphereon.oauth2.client.impl.metadata

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.validation.ValidationErrorDetail
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.JwksUrlExternalIdentifierResolutionService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.FetchJwksArgs
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.client.util.isSecureUrl
import com.sphereon.oauth2.common.error.MetadataError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of FetchJwksCommand
 *
 * Fetches JWK Set (JSON Web Key Set) from a jwks_uri endpoint.
 * Supports both application/jwk-set+json and application/json content types.
 */
@Inject
@SingleIn(SessionScope::class)
class FetchJwksCommandImpl(
    execution: SessionExecution,
    private val jwksUrlResolver: JwksUrlExternalIdentifierResolutionService,
) : TypedServiceCommandAdapter<FetchJwksArgs, JwkSet, IdkError>(
        commandId = FetchJwksCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FetchJwksArgs>(),
        outputTypeToken = typeToken<JwkSet>(),
    ),
    FetchJwksCommand {
    override val commandId: String get() = FetchJwksCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FetchJwksArgs

    override suspend fun doExecute(
        args: FetchJwksArgs,
        applyDuring: (FetchJwksArgs) -> FetchJwksArgs,
    ): IdkResult<JwkSet, IdkError> {
        val applied = applyDuring(args)
        return fetchJwksInternal(applied.jwksUri).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun fetchJwksInternal(jwksUri: String): IdkResult<JwkSet, MetadataError> {
        // Validate jwks_uri format (HTTPS required, HTTP allowed for localhost)
        if (!isSecureUrl(jwksUri)) {
            return Err(
                MetadataError.InvalidUrl(
                    url = jwksUri,
                    reason = "jwks_uri must be an HTTPS URL (HTTP only allowed for localhost)",
                ),
            )
        }

        // Delegate the JWKS fetch to the IDK identifier-resolution system (which owns the HTTP
        // fetch + parse, a CacheManager-backed JWKS cache, and a bounded transient retry).
        val jwksResult = jwksUrlResolver.resolve(ExternalIdentifierJwksUrlOpts(identifier = jwksUri))
        if (jwksResult.isErr) {
            return Err(
                MetadataError.FetchFailed(
                    url = jwksUri,
                    reason = jwksResult.error.message.defaultMessage ?: jwksResult.error.code,
                ),
            )
        }

        // Bridge the resolved keys back to a raw JwkSet (lossless via Jwk.from). No kid is passed
        // to the resolver, so the FULL key set is returned — callers do their own kid/issuer
        // selection, exactly as before.
        val jwkSet =
            JwkSet(
                jwksResult.value.jwks
                    .map { Jwk.from(it.key) }
                    .toTypedArray()
            )
        if (jwkSet.keys.isEmpty()) {
            return Err(
                MetadataError.ValidationFailed(
                    url = jwksUri,
                    details =
                        listOf(
                            ValidationErrorDetail(
                                path = "keys",
                                message = "JWK Set must contain at least one key",
                            ),
                        ),
                ),
            )
        }
        return Ok(jwkSet)
    }
}
