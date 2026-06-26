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
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.data.store.credential.design.PublicDesignAssetPaths
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.SdJwtVcSpecProfile
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Serves SD-JWT VC type metadata ("VCT" documents, draft-ietf-oauth-sd-jwt-vc §6) for the
 * credential types this issuer advertises.
 *
 * `GET /public/schema/vct/{vctId}` — wallets dereference the `vct` URL the issuer publishes (e.g.
 * `${baseUrl}/public/schema/vct/TestCredential`). Served from the public, unauthenticated `/public/schema/vct`
 * hosting adapter (decoupled from the `/oid4vci` protocol surface, like `/public/statuslists`), so
 * the endpoint pattern is `/{vctId}` relative to that base path.
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
                pathPattern = "/{vctId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getVctTypeMetadata",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer", "sd-jwt-vc", "metadata"),
                summary = "SD-JWT VC type metadata (VCT) for a credential type",
                authPolicy = EndpointAuthPolicy.PUBLIC,
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetVctTypeMetadataEndpointCommand>())
class GetVctTypeMetadataEndpointCommandImpl(
    execution: SessionExecution,
    private val vctTypeMetadataProvider: VctTypeMetadataProvider,
    private val configProvider: Oid4vciIssuerConfigProvider,
    private val restConfigProvider: Oid4vciRestConfigProvider,
    private val publicUrlResolver: Oid4vciIssuerPublicUrlResolver,
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

        val resolved =
            vctTypeMetadataProvider.resolve(vctId)
                ?: return Ok(
                    // SD-JWT VC §6.3 leaves the resolution mechanism to the deployment; a missing
                    // type follows standard HTTP semantics (404).
                    jsonResponse(
                        statusCode = 404,
                        body = """{"error":"not_found","error_description":"No type metadata for vct '$vctId'"}""",
                    ),
                )

        val profile = configProvider.specProfile
        if (profile.sdJwtVcSpec != SdJwtVcSpecProfile.DRAFT_11) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported SD-JWT VC type metadata profile '${profile.sdJwtVcSpec.draft}' for hosted VCT '$vctId'",
                ),
            )
        }

        // Resolve design-asset logo URIs in the VCT `rendering` to ABSOLUTE
        // per-tenant URLs using the SAME base the issuer advertises for `credential_issuer` /
        // `vct` (publicUrls.endpointBaseUrl). Asset URIs are stored RELATIVE so a multi-tenant
        // gateway emits each tenant's own host. Resolution failure is non-fatal: fall back to the
        // un-rewritten (relative) document rather than 500 the public VCT surface.
        val publicUrls =
            publicUrlResolver
                .resolve(request, configProvider, restConfigProvider)
                .let { if (it.isOk) it.value else null }
        val metadata =
            resolved
                .withHostedVctUrl(publicUrls?.issuerIdentifier)
                .withAbsoluteAssetUris(publicUrls?.endpointBaseUrl)

        return Ok(jsonResponse(200, protocolJson.encodeToString(SdJwtVcTypeMetadata.serializer(), metadata)))
    }
}

internal fun SdJwtVcTypeMetadata.withHostedVctUrl(externalBaseUrl: String?): SdJwtVcTypeMetadata = copy(vct = vct.toHostedVctUrl(externalBaseUrl))

/**
 * Returns a copy of this [SdJwtVcTypeMetadata] with each display's `rendering.simple` logo /
 * URI resolved to an ABSOLUTE per-tenant URL using [externalBaseUrl]. Stored design
 * assets are RELATIVE (content-addressed under [PublicDesignAssetPaths.BASE_PATH]); already-absolute
 * or non-design URIs are left untouched (see [PublicDesignAssetPaths.toAbsolute]).
 *
 * `internal` so it is directly unit-testable without wiring the full HTTP command.
 */
internal fun SdJwtVcTypeMetadata.withAbsoluteAssetUris(externalBaseUrl: String?): SdJwtVcTypeMetadata =
    copy(
        display =
            display?.map { info ->
                val simple = info.rendering?.simple ?: return@map info
                val newSimple =
                    simple.copy(
                        logo =
                            simple.logo?.let { logo ->
                                PublicDesignAssetPaths
                                    .toAbsolute(logo.uri, externalBaseUrl)
                                    ?.let { logo.copy(uri = it) } ?: logo
                            },
                    )
                info.copy(rendering = info.rendering?.copy(simple = newSimple))
            },
    )
