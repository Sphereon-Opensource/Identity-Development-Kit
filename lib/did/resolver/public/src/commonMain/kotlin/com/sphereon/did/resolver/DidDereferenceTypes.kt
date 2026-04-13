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
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Options for dereferencing a DID URL.
 *
 * @property accept Preferred content type for the response
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDereferenceOptions", exact = true)
@JsExportCompat
@Serializable
data class DidDereferenceOptions(
    val accept: String? = null,
)

/**
 * Metadata returned with DID dereferencing results.
 *
 * @property contentType The MIME type of the dereferenced content
 * @property error Error code if dereferencing failed
 * @property message Human-readable error message
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDereferencingMetadata", exact = true)
@JsExportCompat
@Serializable
data class DidDereferencingMetadata(
    val contentType: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    companion object {
        /**
         * Creates metadata for successful dereferencing.
         */
        fun success(contentType: String = "application/did+json"): DidDereferencingMetadata = DidDereferencingMetadata(contentType = contentType)

        /**
         * Creates metadata for a not-found error.
         */
        fun notFound(message: String? = null): DidDereferencingMetadata = DidDereferencingMetadata(error = "notFound", message = message)
    }

    /**
     * Checks if the dereferencing was successful.
     */
    fun isSuccess(): Boolean = error == null
}

/**
 * Result of dereferencing a DID URL.
 *
 * A DID URL can dereference to different types of resources:
 * - A verification method (when fragment points to a key)
 * - A service (when fragment or query points to a service)
 * - The full DID document (when no fragment or specific query)
 *
 * @property contentType MIME type of the content
 * @property verificationMethod The dereferenced verification method (if applicable)
 * @property service The dereferenced service (if applicable)
 * @property didDocument The full DID document (if applicable)
 * @property dereferencingMetadata Metadata about the dereferencing process
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidDereferenceResult", exact = true)
@JsExportCompat
@Serializable
data class DidDereferenceResult(
    val contentType: String,
    val verificationMethod: VerificationMethod? = null,
    val service: DidService? = null,
    val didDocument: DidDocument? = null,
    val dereferencingMetadata: DidDereferencingMetadata,
) {
    companion object {
        /**
         * Creates a result for a dereferenced verification method.
         */
        fun verificationMethod(vm: VerificationMethod): DidDereferenceResult =
            DidDereferenceResult(
                contentType = "application/did+json",
                verificationMethod = vm,
                dereferencingMetadata = DidDereferencingMetadata.success(),
            )

        /**
         * Creates a result for a dereferenced service.
         */
        fun service(svc: DidService): DidDereferenceResult =
            DidDereferenceResult(
                contentType = "application/did+json",
                service = svc,
                dereferencingMetadata = DidDereferencingMetadata.success(),
            )

        /**
         * Creates a result for a full DID document.
         */
        fun document(doc: DidDocument): DidDereferenceResult =
            DidDereferenceResult(
                contentType = "application/did+json",
                didDocument = doc,
                dereferencingMetadata = DidDereferencingMetadata.success(),
            )

        /**
         * Creates a not-found error result.
         */
        fun notFound(message: String? = null): DidDereferenceResult =
            DidDereferenceResult(
                contentType = "application/did+json",
                dereferencingMetadata = DidDereferencingMetadata.notFound(message),
            )
    }

    /**
     * Checks if the dereferencing was successful.
     */
    fun isSuccess(): Boolean = dereferencingMetadata.isSuccess()

    /**
     * Checks if the result contains a verification method.
     */
    fun hasVerificationMethod(): Boolean = verificationMethod != null

    /**
     * Checks if the result contains a service.
     */
    fun hasService(): Boolean = service != null

    /**
     * Checks if the result contains a full document.
     */
    fun hasDocument(): Boolean = didDocument != null
}
