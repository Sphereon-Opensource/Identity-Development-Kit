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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.ResolveCredentialOfferArgs
import com.sphereon.openid.oid4vci.holder.ResolveCredentialOfferCommand
import com.sphereon.openid.oid4vci.holder.ResolveIssuerMetadataArgs
import com.sphereon.openid.oid4vci.holder.ResolveIssuerMetadataCommand
import com.sphereon.openid.oid4vci.holder.ResolvedCredentialOffer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveCredentialOfferCommand>())
class ResolveCredentialOfferCommandImpl(
    execution: SessionExecution,
    private val resolveIssuerMetadataCommand: ResolveIssuerMetadataCommand,
) : TypedServiceCommandAdapter<ResolveCredentialOfferArgs, ResolvedCredentialOffer>(
        commandId = ResolveCredentialOfferCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveCredentialOfferArgs>(),
        outputTypeToken = typeToken<ResolvedCredentialOffer>(),
    ),
    ResolveCredentialOfferCommand {
    override val commandId: String get() = ResolveCredentialOfferCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveCredentialOfferArgs

    override suspend fun doExecute(
        args: ResolveCredentialOfferArgs,
        applyDuring: (ResolveCredentialOfferArgs) -> ResolveCredentialOfferArgs,
    ): IdkResult<ResolvedCredentialOffer, IdkError> {
        val applied = applyDuring(args)
        val offer = applied.offer

        log.debug("Resolving credential offer from issuer: ${offer.credentialIssuer}")

        val metadata =
            resolveIssuerMetadataCommand
                .execute(
                    ResolveIssuerMetadataArgs(issuerUrl = offer.credentialIssuer),
                ).getOrElse { return Err(it) }

        // Validate that all credential_configuration_ids in the offer exist in the metadata
        val supportedIds = metadata.credentialConfigurationsSupported.keys
        val unknownIds = offer.credentialConfigurationIds.filter { it !in supportedIds }
        if (unknownIds.isNotEmpty()) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Offer references credential_configuration_ids not present in issuer metadata: $unknownIds",
                ),
            )
        }

        log.debug("Successfully resolved credential offer for issuer: ${offer.credentialIssuer}")

        val preferredAs =
            offer.grants?.authorizationCode?.authorizationServer
                ?: offer.grants?.preAuthorizedCode?.authorizationServer

        return Ok(
            ResolvedCredentialOffer(
                offer = offer,
                issuerMetadata = metadata,
                preferredAuthorizationServer = preferredAs,
            ),
        )
    }
}
