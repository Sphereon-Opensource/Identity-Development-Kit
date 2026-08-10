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
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.crypto.core.x509.x509DerOrPemToPem
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scoped

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<X5cExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class X5cExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    val x509VerifyService: X509VerifyService,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.X5c>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.X5C),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    X5cExternalIdentifierResolutionService,
    Scoped {
    @Suppress("MagicNumber")
    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierResult.X5c, IdkErrorType> {
        log.debug("Resolving external X5C identifier: ${args.identifier.toString().take(100)}...")
        // Note: supports() validation is already performed by parent CommandAdapter.execute()
        val opts = asSupportedOpts(args).value
        // Strip ASCII whitespace inside each cert string before decoding. Real-world callers
        // (PEM-formatted pastes, JWKS pretty-printers) commonly carry line breaks/indentation
        // inside base64-DER cert strings; supports() already tolerates this and the decoder
        // must too.
        val pems =
            opts.identifier
                .map { it.replace(Regex("\\s+"), "") }
                .map { x509DerOrPemToPem(it) }
        if (pems.isEmpty()) {
            IdkError.Companion.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }
        val certificates = pems.map { certificateFromPem(it) }
        val verificationResult =
            x509VerifyService.verifyCertificateChain(
                X509VerificationRequest(
                    // X5C resolution also serves as public-key extraction. Callers that defer
                    // issuer policy to a Trust Domain can explicitly disable anchor validation
                    // while certificate parsing and the subsequent signature check still run.
                    enabled = opts.verify != false,
                    chainPEM = pems.toTypedArray(),
                    trustedCerts = opts.trustAnchors?.map { x509DerOrPemToPem(it) }?.toTypedArray(),
                    verificationTime = opts.verificationTime?.let { LocalDateTimeKMP.Companion.fromString(it) },
                ),
            )

        return ExternalIdentifierResult
            .X5c(
                identifierOpts = opts,
                x5c = opts.identifier,
                verificationResult = verificationResult,
                certificates = certificates,
                jwks = certificates.map { ResolvedKeyInfo.Companion.fromKey(it.getPublicKeyJwk()) }.toTypedArray(),
                keyInfo = ResolvedKeyInfo.Companion.fromKey(verificationResult.publicKey!!),
            ).asOkResult()
            .also { log.debug("Resolved external X5C identifier: ${args.identifier.toString().take(100)}") }
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        val methodSupported =
            externalArgs is ExternalIdentifierX5cOpts ||
                externalArgs.method?.let { isSupportedIdentifierMethod(it) } == true
        return methodSupported && isSupportedIdentifier(externalArgs.identifier)
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // A non-empty array/list of base64-encoded DER certificate strings (x5c).
        //
        // Strip ASCII whitespace before the regex check: RFC 7515 §4.1.6 expects single-line
        // base64-DER, but real-world callers (PEM-formatted pastes, JWKS pretty-printers,
        // conformance harnesses) commonly carry line breaks/indentation inside the cert
        // string. The downstream `x509DerOrPemToPem` decoder tolerates this; the supports()
        // gate must too, otherwise the resolver isn't selected and the chain is silently
        // rejected.
        val base64Regex = Regex("^[A-Za-z0-9+/]+={0,2}$")
        val strings: List<String>? =
            when (identifier) {
                is Array<*> -> identifier.filterIsInstance<String>().takeIf { it.isNotEmpty() }
                is List<*> -> identifier.filterIsInstance<String>().takeIf { it.isNotEmpty() }
                else -> null
            }
        return strings?.all { base64Regex.matches(it.replace(Regex("\\s+"), "")) } == true
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.X5c, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierX5cOpts, IdkErrorType> =
        if (isSupportedOpts(opts)) {
            (opts as ExternalIdentifierX5cOpts).asOkResult()
        } else {
            IdkError.Companion.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    companion object {
        const val COMMAND_ID = "crypto.resolution.x5c"
    }
}
