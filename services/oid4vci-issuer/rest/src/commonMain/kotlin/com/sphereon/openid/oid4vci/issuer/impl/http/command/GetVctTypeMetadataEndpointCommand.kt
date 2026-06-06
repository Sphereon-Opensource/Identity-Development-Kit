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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Serves SD-JWT VC type metadata ("VCT" documents, draft-ietf-oauth-sd-jwt-vc §6) for the
 * credential types this issuer advertises.
 *
 * `GET /vct/{vctId}` — wallets dereference the `vct` URL the issuer publishes (e.g.
 * `${issuer}/oid4vci/vct/TestCredential`); the reverse proxy strips the issuer path prefix so the
 * issuer sees `/vct/{vctId}`.
 *
 * The body is produced by the bound [VctTypeMetadataProvider]. IDK's config-driven provider derives
 * it from the same `oid4vci.issuer` credential configuration that drives the OID4VCI metadata, so a
 * single config authors both. The feature is OPTIONAL: when no provider knows the requested type
 * (or none is bound), the endpoint returns 404 and a deployment is free to host VCTs statically or
 * not issue SD-JWT VC at all.
 */
interface GetVctTypeMetadataEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.sdjwtvc.type-metadata"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/vct/{vctId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getVctTypeMetadata",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer", "sd-jwt-vc", "metadata"),
                summary = "SD-JWT VC type metadata (VCT) for a credential type",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetVctTypeMetadataEndpointCommand>())
class GetVctTypeMetadataEndpointCommandImpl(
    execution: SessionExecution,
    private val vctTypeMetadataProvider: VctTypeMetadataProvider,
) : HttpEndpointCommandAdapter(
        id = GetVctTypeMetadataEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetVctTypeMetadataEndpointCommand.ENDPOINT,
    ),
    GetVctTypeMetadataEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args).withExtractedParams(GetVctTypeMetadataEndpointCommand.ENDPOINT.pathPattern)
        val vctId = request.requirePathParam("vctId").getOrElse { return Err(it) }

        val metadata =
            vctTypeMetadataProvider.resolve(vctId)
                ?: return Ok(
                    // SD-JWT VC §6.3 leaves the resolution mechanism to the deployment; a missing
                    // type follows standard HTTP semantics (404).
                    jsonResponse(
                        statusCode = 404,
                        body = """{"error":"not_found","error_description":"No type metadata for vct '$vctId'"}""",
                    ),
                )

        return Ok(jsonResponse(200, protocolJson.encodeToString(SdJwtVcTypeMetadata.serializer(), metadata)))
    }
}
