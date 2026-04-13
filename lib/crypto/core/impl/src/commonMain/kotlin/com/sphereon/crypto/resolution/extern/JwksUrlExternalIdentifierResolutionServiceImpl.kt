/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding

/**
 * Resolves a JWKS from a remote URL (for example an OpenID `jwks_uri`).
 *
 * This service exists to ensure key retrieval is uniform and reusable across the IDK:
 * callers should rely on identifier resolution (crypto core) rather than ad-hoc HTTP fetching.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwksUrlExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class JwksUrlExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.JwksUrl>(
    supportedIdentifierMethods = listOf(IdentifierMethodDefaults.JWKS_URL),
    execution = execution,
    commandId = COMMAND_ID
), JwksUrlExternalIdentifierResolutionService {

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult
    ): IdkResult<ExternalIdentifierResult.JwksUrl, IdkErrorType> {
        // Note: supports() validation is already performed by parent CommandAdapter.execute()
        val opts = asSupportedOpts(args).value
        val url = opts.identifier.trim()
        if (url.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "JWKS URL is blank").asErrorResult()
        }

        val httpClient = try {
            httpClientFactory.createClient(HttpClientOptions.createDefault())
        } catch (e: Exception) {
            return IdkError.UNKNOWN_ERROR(message = "Failed to create HTTP client: ${e.message}", exception = e).asErrorResult()
        }

        val jwksJson = try {
            val response = httpClient.get(url)
            if (response.status != HttpStatusCode.OK) {
                return IdkError.UNKNOWN_ERROR(message = "Failed to fetch JWKS from $url: HTTP ${response.status.value}").asErrorResult()
            }
            response.body<String>()
        } catch (e: Exception) {
            return IdkError.UNKNOWN_ERROR(message = "Failed to fetch JWKS from $url: ${e.message}", exception = e).asErrorResult()
        } finally {
            try {
                httpClient.close()
            } catch (_: Exception) {
                // best-effort
            }
        }

        val jwkSet = try {
            JwkSet.fromJsonString(jwksJson)
        } catch (e: Exception) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid JWKS JSON from $url: ${e.message}", throwable = e).asErrorResult()
        }

        @Suppress("UNCHECKED_CAST")
        val resolvedKeys = jwkSet.keys
            .map { ResolvedKeyInfo.fromKey(it) as com.sphereon.crypto.core.ResolvedKeyInfoType<JwkType> }
            .toTypedArray()
        if (resolvedKeys.isEmpty()) {
            return IdkError.NOT_FOUND_ERROR(message = "JWKS from $url contains no keys").asErrorResult()
        }

        val requestedKid = opts.lookup.kid
        val selectedKey: com.sphereon.crypto.core.ResolvedKeyInfoType<JwkType> = when {
            !requestedKid.isNullOrBlank() -> {
                resolvedKeys.firstOrNull { it.kid == requestedKid }
                    ?: return IdkError.NOT_FOUND_ERROR(message = "No key with kid '$requestedKid' found in JWKS from $url").asErrorResult()
            }

            resolvedKeys.isNotEmpty() -> resolvedKeys.first() // We simply take the first key
            else -> return IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "JWKS from $url contains no keys"
            ).asErrorResult()
        }

        return ExternalIdentifierResult.JwksUrl(
            identifierOpts = opts,
            jwks = resolvedKeys,
            keyInfo = selectedKey,
            jwksUrl = url,
            selectedKid = requestedKid
        ).asOkResult()
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        val methodSupported = externalArgs is ExternalIdentifierJwksUrlOpts ||
            externalArgs.method?.let { isSupportedIdentifierMethod(it) } == true
        return methodSupported && isSupportedIdentifier(externalArgs.identifier)
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // Do not try to infer the method from the URL shape; selection should be driven by opts.method (JWKS_URL).
        // We only require a valid-looking http(s) URL here.
        return identifier is String &&
            (identifier.startsWith("https://", ignoreCase = true) || identifier.startsWith("http://", ignoreCase = true)) &&
            identifier.contains("://")
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.JwksUrl, IdkErrorType> {
        return execute(opts)
    }

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierJwksUrlOpts, IdkErrorType> {
        return if (isSupportedOpts(opts)) (opts as ExternalIdentifierJwksUrlOpts).asOkResult()
        else IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
    }

    @ContributesTo(SessionScope::class)
    interface Component {
        val jwksUrlExternalIdentifierResolutionService: JwksUrlExternalIdentifierResolutionService
    }

    companion object {
        const val COMMAND_ID = "crypto.resolution.jwksurl"
    }
}
