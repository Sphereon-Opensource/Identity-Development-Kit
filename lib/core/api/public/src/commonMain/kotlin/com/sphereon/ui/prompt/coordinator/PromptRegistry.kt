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

package com.sphereon.ui.prompt.coordinator

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.ui.prompt.core.BiometricPromptRequest
import com.sphereon.ui.prompt.core.BiometricPromptResponse
import com.sphereon.ui.prompt.core.BiometricUnlockPromptRequest
import com.sphereon.ui.prompt.core.BiometricUnlockPromptResponse
import com.sphereon.ui.prompt.core.ConfirmationPromptRequest
import com.sphereon.ui.prompt.core.ConfirmationPromptResponse
import com.sphereon.ui.prompt.core.NfcPromptRequest
import com.sphereon.ui.prompt.core.NfcPromptResponse
import com.sphereon.ui.prompt.core.PassphrasePromptRequest
import com.sphereon.ui.prompt.core.PassphrasePromptResponse
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptResponse
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Registry for validating type-safe request/response pairings.
 *
 * This ensures that responses are only accepted for compatible request types.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptRegistry", exact = true)
interface PromptRegistry {
    /**
     * Validates that a response type is valid for a given request type.
     *
     * @param request The prompt request.
     * @param response The prompt response.
     * @return null if valid, or an error message if invalid.
     */
    fun validatePairing(
        request: PromptRequest,
        response: PromptResponse,
    ): String?

    /**
     * Checks if a request type is registered.
     */
    fun isRequestTypeRegistered(request: PromptRequest): Boolean

    /**
     * Gets the expected response type for a request type.
     */
    fun getExpectedResponseType(request: PromptRequest): String?
}

/**
 * Default implementation of PromptRegistry with built-in type pairings.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPromptRegistry", exact = true)
class DefaultPromptRegistry : PromptRegistry {
    /**
     * Map of request type names to their valid response type names.
     */
    private val pairings: Map<String, Set<String>> =
        mapOf(
            NfcPromptRequest::class.simpleName!! to
                setOf(
                    NfcPromptResponse.Success::class.simpleName!!,
                    NfcPromptResponse.Error::class.simpleName!!,
                    NfcPromptResponse.Cancelled::class.simpleName!!,
                    "NfcPromptResponse",
                ),
            BiometricPromptRequest::class.simpleName!! to
                setOf(
                    BiometricPromptResponse.Success::class.simpleName!!,
                    BiometricPromptResponse.Error::class.simpleName!!,
                    BiometricPromptResponse.Cancelled::class.simpleName!!,
                    "BiometricPromptResponse",
                ),
            BiometricUnlockPromptRequest::class.simpleName!! to
                setOf(
                    BiometricUnlockPromptResponse.Success::class.simpleName!!,
                    BiometricUnlockPromptResponse.Error::class.simpleName!!,
                    BiometricUnlockPromptResponse.Cancelled::class.simpleName!!,
                    "BiometricUnlockPromptResponse",
                ),
            PassphrasePromptRequest::class.simpleName!! to
                setOf(
                    PassphrasePromptResponse.Success::class.simpleName!!,
                    PassphrasePromptResponse.Error::class.simpleName!!,
                    PassphrasePromptResponse.Cancelled::class.simpleName!!,
                    "PassphrasePromptResponse",
                ),
            ConfirmationPromptRequest::class.simpleName!! to
                setOf(
                    ConfirmationPromptResponse.Confirmed::class.simpleName!!,
                    ConfirmationPromptResponse.Declined::class.simpleName!!,
                    "ConfirmationPromptResponse",
                ),
        )

    override fun validatePairing(
        request: PromptRequest,
        response: PromptResponse,
    ): String? {
        // Verify prompt IDs match
        if (request.id != response.promptId) {
            return "Prompt ID mismatch: request=${request.id}, response=${response.promptId}"
        }

        val requestTypeName = request::class.simpleName ?: return "Unknown request type"
        val responseTypeName = response::class.simpleName ?: return "Unknown response type"

        val validResponses =
            pairings[requestTypeName]
                ?: return "Unregistered request type: $requestTypeName"

        return if (isValidResponseType(response, requestTypeName, validResponses)) {
            null
        } else {
            "Invalid response type '$responseTypeName' for request type '$requestTypeName'. " +
                "Expected one of: ${validResponses.joinToString()}"
        }
    }

    private fun isValidResponseType(
        response: PromptResponse,
        requestTypeName: String,
        validResponses: Set<String>,
    ): Boolean {
        // Check by request-response type matching first to ensure correct interface type
        // This prevents "Success" from BiometricPromptResponse matching for NfcPromptRequest
        val isCorrectType =
            when {
                requestTypeName == "NfcPromptRequest" && response is NfcPromptResponse -> true
                requestTypeName == "BiometricPromptRequest" && response is BiometricPromptResponse -> true
                requestTypeName == "BiometricUnlockPromptRequest" && response is BiometricUnlockPromptResponse -> true
                requestTypeName == "PassphrasePromptRequest" && response is PassphrasePromptResponse -> true
                requestTypeName == "ConfirmationPromptRequest" && response is ConfirmationPromptResponse -> true
                else -> false
            }

        if (!isCorrectType) {
            return false
        }

        // Now check for valid response variant (Success, Error, etc.)
        val responseTypeName = response::class.simpleName ?: return false
        return responseTypeName in validResponses
    }

    override fun isRequestTypeRegistered(request: PromptRequest): Boolean {
        val requestTypeName = request::class.simpleName ?: return false
        return requestTypeName in pairings
    }

    override fun getExpectedResponseType(request: PromptRequest): String? {
        val requestTypeName = request::class.simpleName ?: return null
        return when (requestTypeName) {
            "NfcPromptRequest" -> "NfcPromptResponse"
            "BiometricPromptRequest" -> "BiometricPromptResponse"
            "BiometricUnlockPromptRequest" -> "BiometricUnlockPromptResponse"
            "PassphrasePromptRequest" -> "PassphrasePromptResponse"
            "ConfirmationPromptRequest" -> "ConfirmationPromptResponse"
            else -> null
        }
    }

    companion object {
        /**
         * Singleton instance of the default registry.
         */
        val INSTANCE = DefaultPromptRegistry()
    }
}
