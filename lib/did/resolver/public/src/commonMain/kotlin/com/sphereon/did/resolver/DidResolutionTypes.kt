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

package com.sphereon.did.resolver

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationPurpose
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Resolution metadata returned with DID resolution results.
 *
 * Contains information about the resolution process itself.
 *
 * @property contentType The MIME type of the resolved representation
 * @property error Error code if resolution failed (e.g., "notFound", "invalidDid")
 * @property message Human-readable error message
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidResolutionMetadata", exact = true)
@JsExportCompat
@Serializable
data class DidResolutionMetadata(
    val contentType: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    companion object {
        /**
         * Error code for DID not found.
         */
        const val ERROR_NOT_FOUND: String = "notFound"

        /**
         * Error code for invalid DID format.
         */
        const val ERROR_INVALID_DID: String = "invalidDid"

        /**
         * Error code for unsupported method.
         */
        const val ERROR_METHOD_NOT_SUPPORTED: String = "methodNotSupported"

        /**
         * Error code for internal resolver error.
         */
        const val ERROR_INTERNAL_ERROR: String = "internalError"

        /**
         * Creates metadata for successful resolution.
         */
        fun success(contentType: String = "application/did+json"): DidResolutionMetadata = DidResolutionMetadata(contentType = contentType)

        /**
         * Creates metadata for a not-found error.
         */
        fun notFound(message: String? = null): DidResolutionMetadata = DidResolutionMetadata(error = ERROR_NOT_FOUND, message = message)

        /**
         * Creates metadata for an invalid DID error.
         */
        fun invalidDid(message: String? = null): DidResolutionMetadata = DidResolutionMetadata(error = ERROR_INVALID_DID, message = message)
    }

    /**
     * Checks if the resolution was successful.
     */
    fun isSuccess(): Boolean = error == null
}

/**
 * Document metadata returned with resolved DID documents.
 *
 * Contains information about the DID document itself.
 *
 * @property created ISO 8601 timestamp when the document was created
 * @property updated ISO 8601 timestamp when the document was last updated
 * @property deactivated Whether the DID has been deactivated
 * @property versionId The version identifier of this document
 * @property nextVersionId The version identifier of the next document version (if known)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDocumentMetadata", exact = true)
@JsExportCompat
@Serializable
data class DidDocumentMetadata(
    val created: String? = null,
    val updated: String? = null,
    val deactivated: Boolean? = null,
    val versionId: String? = null,
    val nextVersionId: String? = null,
) {
    /**
     * Checks if the DID is active (not deactivated).
     */
    fun isActive(): Boolean = deactivated != true
}

/**
 * Complete result of resolving a DID.
 *
 * Follows the W3C DID Resolution specification structure.
 *
 * @property didDocument The resolved DID Document (null if resolution failed)
 * @property didResolutionMetadata Metadata about the resolution process
 * @property didDocumentMetadata Metadata about the DID document
 * @property verificationMethodsByPurpose Pre-indexed verification methods by purpose for easy lookup
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidResolutionResult", exact = true)
@JsExportCompat
@Serializable
data class DidResolutionResult(
    val didDocument: DidDocument?,
    val didResolutionMetadata: DidResolutionMetadata,
    val didDocumentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
    val verificationMethodsByPurpose: Map<VerificationPurpose, List<VerificationMethod>> = emptyMap(),
) {
    companion object {
        /**
         * Creates a successful resolution result.
         */
        fun success(
            document: DidDocument,
            metadata: DidDocumentMetadata = DidDocumentMetadata(),
        ): DidResolutionResult {
            val vmByPurpose = document.getVerificationMethodsByPurpose()
            return DidResolutionResult(
                didDocument = document,
                didResolutionMetadata = DidResolutionMetadata.success(),
                didDocumentMetadata = metadata,
                verificationMethodsByPurpose = vmByPurpose,
            )
        }

        /**
         * Creates a not-found error result.
         */
        fun notFound(did: String): DidResolutionResult =
            DidResolutionResult(
                didDocument = null,
                didResolutionMetadata = DidResolutionMetadata.notFound("DID not found: $did"),
            )

        /**
         * Creates an error result.
         */
        fun error(
            errorCode: String,
            message: String? = null,
        ): DidResolutionResult =
            DidResolutionResult(
                didDocument = null,
                didResolutionMetadata = DidResolutionMetadata(error = errorCode, message = message),
            )
    }

    /**
     * Checks if the resolution was successful.
     */
    fun isSuccess(): Boolean = didResolutionMetadata.isSuccess() && didDocument != null

    /**
     * Gets all authentication verification methods.
     */
    fun getAuthenticationMethods(): List<VerificationMethod> = verificationMethodsByPurpose[VerificationPurpose.AUTHENTICATION] ?: emptyList()

    /**
     * Gets all assertion method verification methods (for signing credentials).
     */
    fun getAssertionMethods(): List<VerificationMethod> = verificationMethodsByPurpose[VerificationPurpose.ASSERTION_METHOD] ?: emptyList()

    /**
     * Gets all key agreement verification methods (for encryption).
     */
    fun getKeyAgreementMethods(): List<VerificationMethod> = verificationMethodsByPurpose[VerificationPurpose.KEY_AGREEMENT] ?: emptyList()

    /**
     * Finds a verification method by kid (key ID / fragment).
     */
    fun getVerificationMethodByKid(kid: String): VerificationMethod? = didDocument?.getVerificationMethodById(kid)

    /**
     * Finds a service by ID.
     */
    fun getServiceById(serviceId: String): DidService? = didDocument?.getServiceById(serviceId)
}

/**
 * Resolution options.
 *
 * @property accept Preferred content type for the response
 * @property noCache If true, bypass any caching and resolve fresh
 * @property filter Filter to apply to the resolution result
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidResolutionOptions", exact = true)
@JsExportCompat
@Serializable
data class DidResolutionOptions(
    val accept: String? = null,
    val noCache: Boolean = false,
    val filter: DidResolutionFilter? = null,
)

/**
 * DSL for filtering resolution results.
 *
 * Can be applied before resolution (to method) or after (on result).
 *
 * @property purposes Filter by verification method purpose
 * @property keyTypes Filter by key type (e.g., "OKP", "EC")
 * @property verificationMethodTypes Filter by VM type
 * @property serviceTypes Filter services by type
 * @property kid Specific kid to resolve
 * @property serviceId Specific service ID to resolve
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidResolutionFilter", exact = true)
@JsExportCompat
@Serializable
data class DidResolutionFilter(
    val purposes: List<VerificationPurpose>? = null,
    val keyTypes: List<String>? = null,
    val verificationMethodTypes: List<String>? = null,
    val serviceTypes: List<String>? = null,
    val kid: String? = null,
    val serviceId: String? = null,
) {
    companion object {
        /**
         * Creates a filter for authentication methods.
         */
        fun forAuthentication(): DidResolutionFilter = DidResolutionFilter(purposes = listOf(VerificationPurpose.AUTHENTICATION))

        /**
         * Creates a filter for assertion methods (signing credentials).
         */
        fun forAssertionMethod(): DidResolutionFilter = DidResolutionFilter(purposes = listOf(VerificationPurpose.ASSERTION_METHOD))

        /**
         * Creates a filter for key agreement methods (encryption).
         */
        fun forKeyAgreement(): DidResolutionFilter = DidResolutionFilter(purposes = listOf(VerificationPurpose.KEY_AGREEMENT))

        /**
         * Creates a filter for a specific kid.
         */
        fun forKid(kid: String): DidResolutionFilter = DidResolutionFilter(kid = kid)

        /**
         * Creates a filter for a specific service.
         */
        fun forService(serviceId: String): DidResolutionFilter = DidResolutionFilter(serviceId = serviceId)
    }
}
