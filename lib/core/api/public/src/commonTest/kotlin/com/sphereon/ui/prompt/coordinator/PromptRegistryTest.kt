/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptPresentationHint
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptResponse
import com.sphereon.ui.prompt.core.PromptState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptRegistryTest {

    private val registry = DefaultPromptRegistry()

    // NFC request/response pairing tests

    @Test
    fun validatePairingAcceptsNfcSuccess() {
        val promptId = PromptId.random()
        val request = NfcPromptRequest(
            id = promptId,
            title = "Test",
            initialMessage = "Testing"
        )
        val response = NfcPromptResponse.Success(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept NFC success response")
    }

    @Test
    fun validatePairingAcceptsNfcError() {
        val promptId = PromptId.random()
        val request = NfcPromptRequest(
            id = promptId,
            title = "Test",
            initialMessage = "Testing"
        )
        val response = NfcPromptResponse.Error(promptId = promptId, message = "Error")

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept NFC error response")
    }

    @Test
    fun validatePairingAcceptsNfcCancelled() {
        val promptId = PromptId.random()
        val request = NfcPromptRequest(
            id = promptId,
            title = "Test",
            initialMessage = "Testing"
        )
        val response = NfcPromptResponse.Cancelled(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept NFC cancelled response")
    }

    @Test
    fun validatePairingRejectsMismatchedTypes() {
        val promptId = PromptId.random()
        val request = NfcPromptRequest(
            id = promptId,
            title = "Test",
            initialMessage = "Testing"
        )
        val response = BiometricPromptResponse.Success(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNotNull(error, "Should reject mismatched request/response types")
        assertTrue(error.contains("Invalid response type"))
    }

    @Test
    fun validatePairingRejectsIdMismatch() {
        val request = NfcPromptRequest(
            id = PromptId.random(),
            title = "Test",
            initialMessage = "Testing"
        )
        val response = NfcPromptResponse.Success(promptId = PromptId.random())

        val error = registry.validatePairing(request, response)

        assertNotNull(error, "Should reject ID mismatch")
        assertTrue(error.contains("Prompt ID mismatch"))
    }

    // Biometric request/response pairing tests

    @Test
    fun validatePairingAcceptsBiometricSuccess() {
        val promptId = PromptId.random()
        val request = BiometricPromptRequest(
            id = promptId,
            title = "Test",
            reason = "Testing"
        )
        val response = BiometricPromptResponse.Success(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept biometric success response")
    }

    @Test
    fun validatePairingAcceptsBiometricError() {
        val promptId = PromptId.random()
        val request = BiometricPromptRequest(
            id = promptId,
            title = "Test",
            reason = "Testing"
        )
        val response = BiometricPromptResponse.Error(promptId = promptId, message = "Failed")

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept biometric error response")
    }

    @Test
    fun validatePairingAcceptsBiometricCancelled() {
        val promptId = PromptId.random()
        val request = BiometricPromptRequest(
            id = promptId,
            title = "Test",
            reason = "Testing"
        )
        val response = BiometricPromptResponse.Cancelled(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept biometric cancelled response")
    }

    // BiometricUnlock request/response pairing tests

    @Test
    fun validatePairingAcceptsBiometricUnlockSuccess() {
        val promptId = PromptId.random()
        val request = BiometricUnlockPromptRequest(
            id = promptId,
            title = "Test",
            keyId = "key-123",
            reason = "Testing"
        )
        val response = BiometricUnlockPromptResponse.Success(promptId = promptId, keyHandle = "handle")

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept biometric unlock success response")
    }

    @Test
    fun validatePairingAcceptsBiometricUnlockError() {
        val promptId = PromptId.random()
        val request = BiometricUnlockPromptRequest(
            id = promptId,
            title = "Test",
            keyId = "key-123",
            reason = "Testing"
        )
        val response = BiometricUnlockPromptResponse.Error(promptId = promptId, message = "Failed")

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept biometric unlock error response")
    }

    @Test
    fun validatePairingAcceptsBiometricUnlockCancelled() {
        val promptId = PromptId.random()
        val request = BiometricUnlockPromptRequest(
            id = promptId,
            title = "Test",
            keyId = "key-123",
            reason = "Testing"
        )
        val response = BiometricUnlockPromptResponse.Cancelled(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept biometric unlock cancelled response")
    }

    // Passphrase request/response pairing tests

    @Test
    fun validatePairingAcceptsPassphraseSuccess() {
        val promptId = PromptId.random()
        val request = PassphrasePromptRequest(
            id = promptId,
            title = "Test",
            purpose = "Testing"
        )
        val response = PassphrasePromptResponse.Success(promptId = promptId, passphrase = "1234")

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept passphrase success response")
    }

    @Test
    fun validatePairingAcceptsPassphraseError() {
        val promptId = PromptId.random()
        val request = PassphrasePromptRequest(
            id = promptId,
            title = "Test",
            purpose = "Testing"
        )
        val response = PassphrasePromptResponse.Error(promptId = promptId, message = "Wrong")

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept passphrase error response")
    }

    @Test
    fun validatePairingAcceptsPassphraseCancelled() {
        val promptId = PromptId.random()
        val request = PassphrasePromptRequest(
            id = promptId,
            title = "Test",
            purpose = "Testing"
        )
        val response = PassphrasePromptResponse.Cancelled(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept passphrase cancelled response")
    }

    // Confirmation request/response pairing tests

    @Test
    fun validatePairingAcceptsConfirmationConfirmed() {
        val promptId = PromptId.random()
        val request = ConfirmationPromptRequest(
            id = promptId,
            title = "Test",
            message = "Testing"
        )
        val response = ConfirmationPromptResponse.Confirmed(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept confirmation confirmed response")
    }

    @Test
    fun validatePairingAcceptsConfirmationDeclined() {
        val promptId = PromptId.random()
        val request = ConfirmationPromptRequest(
            id = promptId,
            title = "Test",
            message = "Testing"
        )
        val response = ConfirmationPromptResponse.Declined(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNull(error, "Should accept confirmation declined response")
    }

    // isRequestTypeRegistered tests

    @Test
    fun isRequestTypeRegisteredReturnsTrueForNfc() {
        val request = NfcPromptRequest(title = "Test", initialMessage = "Testing")
        assertTrue(registry.isRequestTypeRegistered(request))
    }

    @Test
    fun isRequestTypeRegisteredReturnsTrueForBiometric() {
        val request = BiometricPromptRequest(title = "Test", reason = "Testing")
        assertTrue(registry.isRequestTypeRegistered(request))
    }

    @Test
    fun isRequestTypeRegisteredReturnsTrueForBiometricUnlock() {
        val request = BiometricUnlockPromptRequest(title = "Test", keyId = "key", reason = "Testing")
        assertTrue(registry.isRequestTypeRegistered(request))
    }

    @Test
    fun isRequestTypeRegisteredReturnsTrueForPassphrase() {
        val request = PassphrasePromptRequest(title = "Test", purpose = "Testing")
        assertTrue(registry.isRequestTypeRegistered(request))
    }

    @Test
    fun isRequestTypeRegisteredReturnsTrueForConfirmation() {
        val request = ConfirmationPromptRequest(title = "Test", message = "Testing")
        assertTrue(registry.isRequestTypeRegistered(request))
    }

    @Test
    fun isRequestTypeRegisteredReturnsFalseForUnknownType() {
        val request = object : PromptRequest {
            override val id = PromptId.random()
            override val presentationHint = PromptPresentationHint.DEFAULT
        }
        assertFalse(registry.isRequestTypeRegistered(request))
    }

    // getExpectedResponseType tests

    @Test
    fun getExpectedResponseTypeReturnsNfcPromptResponse() {
        val request = NfcPromptRequest(title = "Test", initialMessage = "Testing")
        assertEquals("NfcPromptResponse", registry.getExpectedResponseType(request))
    }

    @Test
    fun getExpectedResponseTypeReturnsBiometricPromptResponse() {
        val request = BiometricPromptRequest(title = "Test", reason = "Testing")
        assertEquals("BiometricPromptResponse", registry.getExpectedResponseType(request))
    }

    @Test
    fun getExpectedResponseTypeReturnsBiometricUnlockPromptResponse() {
        val request = BiometricUnlockPromptRequest(title = "Test", keyId = "key", reason = "Testing")
        assertEquals("BiometricUnlockPromptResponse", registry.getExpectedResponseType(request))
    }

    @Test
    fun getExpectedResponseTypeReturnsPassphrasePromptResponse() {
        val request = PassphrasePromptRequest(title = "Test", purpose = "Testing")
        assertEquals("PassphrasePromptResponse", registry.getExpectedResponseType(request))
    }

    @Test
    fun getExpectedResponseTypeReturnsConfirmationPromptResponse() {
        val request = ConfirmationPromptRequest(title = "Test", message = "Testing")
        assertEquals("ConfirmationPromptResponse", registry.getExpectedResponseType(request))
    }

    @Test
    fun getExpectedResponseTypeReturnsNullForUnknownType() {
        val request = object : PromptRequest {
            override val id = PromptId.random()
            override val presentationHint = PromptPresentationHint.DEFAULT
        }
        assertNull(registry.getExpectedResponseType(request))
    }

    // Singleton instance tests

    @Test
    fun singletonInstanceExists() {
        assertNotNull(DefaultPromptRegistry.INSTANCE)
    }

    @Test
    fun singletonInstanceWorks() {
        val promptId = PromptId.random()
        val request = NfcPromptRequest(id = promptId, title = "Test", initialMessage = "Testing")
        val response = NfcPromptResponse.Success(promptId = promptId)

        val error = DefaultPromptRegistry.INSTANCE.validatePairing(request, response)

        assertNull(error)
    }

    // Cross-type rejection tests

    @Test
    fun rejectsBiometricUnlockResponseForBiometricRequest() {
        val promptId = PromptId.random()
        val request = BiometricPromptRequest(id = promptId, title = "Test", reason = "Testing")
        val response = BiometricUnlockPromptResponse.Success(promptId = promptId, keyHandle = "handle")

        val error = registry.validatePairing(request, response)

        assertNotNull(error)
    }

    @Test
    fun rejectsPassphraseResponseForNfcRequest() {
        val promptId = PromptId.random()
        val request = NfcPromptRequest(id = promptId, title = "Test", initialMessage = "Testing")
        val response = PassphrasePromptResponse.Success(promptId = promptId, passphrase = "1234")

        val error = registry.validatePairing(request, response)

        assertNotNull(error)
    }

    @Test
    fun rejectsConfirmationResponseForPassphraseRequest() {
        val promptId = PromptId.random()
        val request = PassphrasePromptRequest(id = promptId, title = "Test", purpose = "Testing")
        val response = ConfirmationPromptResponse.Confirmed(promptId = promptId)

        val error = registry.validatePairing(request, response)

        assertNotNull(error)
    }
}
