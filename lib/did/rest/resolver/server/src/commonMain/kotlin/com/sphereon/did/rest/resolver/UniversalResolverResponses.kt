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

package com.sphereon.did.rest.resolver

import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidDocument
import com.sphereon.did.resolver.DidResolutionResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DIF Universal Resolver response models.
 *
 * @see <a href="https://github.com/decentralized-identity/universal-resolver/blob/main/openapi/openapi.yaml">DIF Universal Resolver OpenAPI</a>
 */

/**
 * Successful DID resolution response.
 *
 * Follows the DIF Universal Resolver response format.
 */
@Serializable
data class DidResolutionResponse(
    @SerialName("@context")
    val context: String = "https://w3id.org/did-resolution/v1",
    val didDocument: DidDocument?,
    val didResolutionMetadata: DidResolutionMetadataResponse,
    val didDocumentMetadata: DidDocumentMetadataResponse,
)

/**
 * DID resolution metadata in response format.
 */
@Serializable
data class DidResolutionMetadataResponse(
    val contentType: String? = "application/did+ld+json",
    val error: String? = null,
    val message: String? = null,
)

/**
 * DID document metadata in response format.
 */
@Serializable
data class DidDocumentMetadataResponse(
    val created: String? = null,
    val updated: String? = null,
    val deactivated: Boolean? = null,
    val versionId: String? = null,
    val nextVersionId: String? = null,
    val nextUpdate: String? = null,
    val equivalentId: List<String>? = null,
    val canonicalId: String? = null,
)

/**
 * Error response for DID resolution failures.
 */
@Serializable
data class DidResolutionErrorResponse(
    @SerialName("@context")
    val context: String = "https://w3id.org/did-resolution/v1",
    val didDocument: DidDocument? = null,
    val didResolutionMetadata: DidResolutionMetadataResponse,
    val didDocumentMetadata: DidDocumentMetadataResponse? = null,
)

/**
 * Resolver properties response.
 */
@Serializable
data class ResolverPropertiesResponse(
    val methods: List<String>,
    val methodCapabilities: Map<String, DidMethodCapabilities> = emptyMap(),
)

/**
 * Extension function to convert DidResolutionResult to response format.
 */
fun DidResolutionResult.toResponse(): DidResolutionResponse =
    DidResolutionResponse(
        didDocument = didDocument,
        didResolutionMetadata =
            DidResolutionMetadataResponse(
                contentType = didResolutionMetadata.contentType,
                error = didResolutionMetadata.error,
                message = didResolutionMetadata.message,
            ),
        didDocumentMetadata =
            DidDocumentMetadataResponse(
                created = didDocumentMetadata.created?.toString(),
                updated = didDocumentMetadata.updated?.toString(),
                deactivated = didDocumentMetadata.deactivated,
                versionId = didDocumentMetadata.versionId,
                nextVersionId = didDocumentMetadata.nextVersionId,
                nextUpdate = didDocumentMetadata.nextUpdate?.toString(),
                equivalentId = didDocumentMetadata.equivalentId,
                canonicalId = didDocumentMetadata.canonicalId,
            ),
    )
