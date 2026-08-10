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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BuildIssuerMetadataCommand>())
class BuildIssuerMetadataCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<BuildIssuerMetadataArgs, CredentialIssuerMetadata, IdkError>(
        commandId = BuildIssuerMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BuildIssuerMetadataArgs>(),
        outputTypeToken = typeToken<CredentialIssuerMetadata>(),
    ),
    BuildIssuerMetadataCommand {
    override val commandId: String get() = BuildIssuerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildIssuerMetadataArgs

    override suspend fun doExecute(
        args: BuildIssuerMetadataArgs,
        applyDuring: (BuildIssuerMetadataArgs) -> BuildIssuerMetadataArgs,
    ): IdkResult<CredentialIssuerMetadata, IdkError> {
        val applied = applyDuring(args)

        val issuerUrl = applied.issuerIdentifier.trimEnd('/')
        if (!isAllowedIssuerUrl(issuerUrl)) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "credential_issuer must be an HTTPS URL (or HTTP for localhost/private networks): $issuerUrl",
                ),
            )
        }

        val baseUrl = applied.baseUrl.trimEnd('/')

        // OID4VCI 1.1 §13.2.4: credential-level display requires a non-blank name.
        // Validate both the top-level display list and credentialMetadata.display on each config.
        for ((configId, config) in applied.credentialConfigurations) {
            val displayLists = listOfNotNull(config.display, config.credentialMetadata?.display)
            for (displayList in displayLists) {
                val blankNames = displayList.filter { it.name.isBlank() }
                if (blankNames.isNotEmpty()) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Credential configuration '$configId' has display entries with a blank name, which is REQUIRED per OID4VCI 1.1 §13.2.4",
                        ),
                    )
                }
            }
        }

        return Ok(
            CredentialIssuerMetadata(
                credentialIssuer = applied.issuerIdentifier,
                authorizationServers = applied.authorizationServers,
                credentialEndpoint = "$baseUrl/credential",
                deferredCredentialEndpoint = "$baseUrl/deferredCredential",
                notificationEndpoint = "$baseUrl/notification",
                nonceEndpoint = "$baseUrl/nonce",
                credentialConfigurationsSupported = applied.credentialConfigurations,
                // The top-level Credential Issuer metadata display object is narrower than the
                // credential-configuration display object. Keep richer branding in production
                // configuration/testing-console models, but do not leak extension-only fields
                // (description/background/text/background image) into the OID4VCI metadata.
                display = applied.display?.map { it.toIssuerMetadataDisplay() },
                credentialResponseEncryption = applied.credentialResponseEncryption,
                credentialRequestEncryption = applied.credentialRequestEncryption,
                batchCredentialIssuance = applied.batchCredentialIssuance,
                preferredKeyStorageStatusPeriod = applied.preferredKeyStorageStatusPeriodSeconds,
            ),
        )
    }

    companion object {
        private fun DisplayProperties.toIssuerMetadataDisplay() =
            DisplayProperties(
                name = name,
                locale = locale,
                logo = logo,
            )

        private val privateNetworkPrefixes =
            listOf(
                "http://localhost",
                "http://127.0.0.1",
                "http://[::1]",
                "http://10.",
                "http://172.16.",
                "http://172.17.",
                "http://172.18.",
                "http://172.19.",
                "http://172.20.",
                "http://172.21.",
                "http://172.22.",
                "http://172.23.",
                "http://172.24.",
                "http://172.25.",
                "http://172.26.",
                "http://172.27.",
                "http://172.28.",
                "http://172.29.",
                "http://172.30.",
                "http://172.31.",
                "http://192.168.",
            )

        private fun isAllowedIssuerUrl(url: String): Boolean {
            if (url.startsWith("https://")) {
                return true
            }
            if (privateNetworkPrefixes.any { url.startsWith(it) }) {
                return true
            }
            // RFC 6761: the `.localhost` special-use TLD (including any `*.localhost`
            // subdomain, e.g. `acme.localhost`) is reserved for loopback, so plain HTTP
            // is acceptable for it just like bare `localhost`.
            val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':')
            return host == "localhost" || host.endsWith(".localhost")
        }
    }
}
