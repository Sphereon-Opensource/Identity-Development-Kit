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

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scoped

/**
 * Service for resolving external JWK identifiers.
 *
 * This service handles ExternalIdentifierJwkOpts, which represents an already-resolved JWK
 * that should be used directly for signature verification without further resolution.
 *
 * This is commonly used for KB-JWT (Key Binding JWT) verification in SD-JWT, where the
 * holder's public key is provided in the CNF claim and must be used to verify the KB-JWT signature.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JwkExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class JwkExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.Jwk>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.JWK),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    JwkExternalIdentifierResolutionService,
    Scoped {
    @Suppress("MagicNumber")
    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierResult.Jwk, IdkErrorType> {
        log.debug("Resolving external JWK identifier: ${args.identifier.toString().take(100)}...")

        // Note: supports() validation is already performed by parent CommandAdapter.execute()
        val opts = asSupportedOpts(args).value
        val jwk = opts.identifier

        // Create resolved key info from the JWK
        val keyInfo = ResolvedKeyInfo.fromKey(jwk)

        // Handle optional X5C (certificate chain) if present
        val x5cResult =
            opts.x5c?.let { x5cOpts ->
                // If X5C is provided, we should verify it and ensure it matches the JWK
                // For now, we'll just pass it through - full X5C validation would be done by X5cExternalIdentifierResolutionService
                null // TODO: Optionally resolve X5C if provided
            }

        return ExternalIdentifierResult
            .Jwk(
                identifierOpts = opts,
                jwks = arrayOf(keyInfo),
                keyInfo = keyInfo,
                x5c = x5cResult,
            ).asOkResult()
            .also {
                log.debug("Resolved external JWK identifier: ${args.identifier.toString().take(100)}")
            }
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        val methodSupported =
            externalArgs is ExternalIdentifierJwkOpts ||
                externalArgs.method?.let { isSupportedIdentifierMethod(it) } == true
        return methodSupported && isSupportedIdentifier(externalArgs.identifier)
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // Check if the identifier is a JWK
        return identifier is JwkType
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.Jwk, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierJwkOpts, IdkErrorType> =
        if (isSupportedOpts(opts)) {
            (opts as ExternalIdentifierJwkOpts).asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    companion object {
        const val COMMAND_ID = "crypto.resolution.jwk"
    }
}
