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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.createJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.jsonObject

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<BuildSignedIssuerMetadataCommand>())
class BuildSignedIssuerMetadataCommandImpl(
    execution: SessionExecution,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
) : TypedServiceCommandAdapter<BuildSignedIssuerMetadataArgs, JwtCompactResult>(
        commandId = BuildSignedIssuerMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BuildSignedIssuerMetadataArgs>(),
        outputTypeToken = typeToken<JwtCompactResult>(),
    ),
    BuildSignedIssuerMetadataCommand {
    override val commandId: String get() = BuildSignedIssuerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildSignedIssuerMetadataArgs

    override suspend fun doExecute(
        args: BuildSignedIssuerMetadataArgs,
        applyDuring: (BuildSignedIssuerMetadataArgs) -> BuildSignedIssuerMetadataArgs,
    ): IdkResult<JwtCompactResult, IdkError> {
        val applied = applyDuring(args)

        val metadataPayload =
            Oid4vciJson.lenientNoDefaults
                .encodeToJsonElement(
                    CredentialIssuerMetadata.serializer(),
                    applied.metadata,
                ).jsonObject

        val jwsArgs =
            createJwsArgs {
                issuer(applied.signingKey)
                payload(metadataPayload)
                mode(applied.identifierMode)
                options {
                    protectedHeader {
                        typ("openidvci-issuer-metadata+jwt")
                    }
                    noIssPayloadUpdate()
                }
            }

        return createJwsCompactCommand.execute(jwsArgs)
    }
}
