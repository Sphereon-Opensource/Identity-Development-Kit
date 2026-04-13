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

package com.sphereon.ui.prompt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptRequestResponseTest {

    // NfcPromptRequest Tests

    @Test
    fun nfcPromptRequestDefaultValues() {
        val request = NfcPromptRequest(
            title = "NFC",
            initialMessage = "Tap card"
        )

        assertNotNull(request.id)
        assertEquals("NFC", request.title)
        assertNull(request.subtitle)
        assertTrue(request.show)
        assertEquals(PromptPresentationHint.DEFAULT, request.presentationHint)
        assertEquals("Tap card", request.initialMessage)
        assertNull(request.successMessage)
        assertNull(request.errorMessage)
    }

    @Test
    fun nfcPromptRequestWithAllValues() {
        val id = PromptId.random()
        val hint = PromptPresentationHint(priority = PromptPriority.HIGH)
        val request = NfcPromptRequest(
            id = id,
            title = "NFC",
            subtitle = "Subtitle",
            show = false,
            presentationHint = hint,
            initialMessage = "Tap card",
            successMessage = "Success!",
            errorMessage = "Error!"
        )

        assertEquals(id, request.id)
        assertEquals("NFC", request.title)
        assertEquals("Subtitle", request.subtitle)
        assertFalse(request.show)
        assertEquals(hint, request.presentationHint)
        assertEquals("Tap card", request.initialMessage)
        assertEquals("Success!", request.successMessage)
        assertEquals("Error!", request.errorMessage)
    }

    // NfcPromptResponse Tests

    @Test
    fun nfcPromptResponseSuccessState() {
        val promptId = PromptId.random()
        val response = NfcPromptResponse.Success(promptId, "tag-data")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.SUCCESS, response.state)
        assertEquals("tag-data", response.tagData)
    }

    @Test
    fun nfcPromptResponseSuccessWithNullData() {
        val promptId = PromptId.random()
        val response = NfcPromptResponse.Success(promptId)

        assertNull(response.tagData)
    }

    @Test
    fun nfcPromptResponseErrorState() {
        val promptId = PromptId.random()
        val response = NfcPromptResponse.Error(promptId, "Connection lost", "NFC_001")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.ERROR, response.state)
        assertEquals("Connection lost", response.message)
        assertEquals("NFC_001", response.errorCode)
    }

    @Test
    fun nfcPromptResponseCancelledState() {
        val promptId = PromptId.random()
        val response = NfcPromptResponse.Cancelled(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.CANCELLED, response.state)
    }

    // BiometricPromptRequest Tests

    @Test
    fun biometricPromptRequestDefaultValues() {
        val request = BiometricPromptRequest(
            title = "Authenticate",
            reason = "Confirm identity"
        )

        assertNotNull(request.id)
        assertEquals("Authenticate", request.title)
        assertEquals("Confirm identity", request.reason)
        assertTrue(request.fallbackToPasscode)
        assertEquals(PromptPriority.HIGH, request.presentationHint.priority)
    }

    @Test
    fun biometricPromptRequestWithCustomValues() {
        val request = BiometricPromptRequest(
            title = "Authenticate",
            reason = "Confirm identity",
            fallbackToPasscode = false
        )

        assertFalse(request.fallbackToPasscode)
    }

    // BiometricPromptResponse Tests

    @Test
    fun biometricPromptResponseSuccessState() {
        val promptId = PromptId.random()
        val response = BiometricPromptResponse.Success(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.SUCCESS, response.state)
    }

    @Test
    fun biometricPromptResponseErrorState() {
        val promptId = PromptId.random()
        val response = BiometricPromptResponse.Error(promptId, "Biometric failed", "BIO_001")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.ERROR, response.state)
        assertEquals("Biometric failed", response.message)
        assertEquals("BIO_001", response.errorCode)
    }

    @Test
    fun biometricPromptResponseCancelledState() {
        val promptId = PromptId.random()
        val response = BiometricPromptResponse.Cancelled(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.CANCELLED, response.state)
    }

    // BiometricUnlockPromptRequest Tests

    @Test
    fun biometricUnlockPromptRequestDefaultValues() {
        val request = BiometricUnlockPromptRequest(
            title = "Unlock Key",
            keyId = "key-123",
            reason = "Sign document"
        )

        assertNotNull(request.id)
        assertEquals("Unlock Key", request.title)
        assertEquals("key-123", request.keyId)
        assertEquals("Sign document", request.reason)
        assertTrue(request.fallbackToPasscode)
    }

    // BiometricUnlockPromptResponse Tests

    @Test
    fun biometricUnlockPromptResponseSuccessState() {
        val promptId = PromptId.random()
        val response = BiometricUnlockPromptResponse.Success(promptId, "key-handle-456")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.SUCCESS, response.state)
        assertEquals("key-handle-456", response.keyHandle)
    }

    @Test
    fun biometricUnlockPromptResponseErrorState() {
        val promptId = PromptId.random()
        val response = BiometricUnlockPromptResponse.Error(promptId, "Key not found")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.ERROR, response.state)
        assertEquals("Key not found", response.message)
    }

    @Test
    fun biometricUnlockPromptResponseCancelledState() {
        val promptId = PromptId.random()
        val response = BiometricUnlockPromptResponse.Cancelled(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.CANCELLED, response.state)
    }

    // PassphrasePromptRequest Tests

    @Test
    fun passphrasePromptRequestDefaultValues() {
        val request = PassphrasePromptRequest(
            title = "Enter PIN",
            purpose = "Unlock wallet"
        )

        assertNotNull(request.id)
        assertEquals("Enter PIN", request.title)
        assertEquals("Unlock wallet", request.purpose)
        assertEquals(4, request.minLength)
        assertNull(request.maxLength)
        assertFalse(request.isNumericOnly)
    }

    @Test
    fun passphrasePromptRequestWithCustomValues() {
        val request = PassphrasePromptRequest(
            title = "Enter PIN",
            purpose = "Unlock",
            minLength = 6,
            maxLength = 8,
            isNumericOnly = true
        )

        assertEquals(6, request.minLength)
        assertEquals(8, request.maxLength)
        assertTrue(request.isNumericOnly)
    }

    // PassphrasePromptResponse Tests

    @Test
    fun passphrasePromptResponseSuccessState() {
        val promptId = PromptId.random()
        val response = PassphrasePromptResponse.Success(promptId, "1234")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.SUCCESS, response.state)
        assertEquals("1234", response.passphrase)
    }

    @Test
    fun passphrasePromptResponseErrorState() {
        val promptId = PromptId.random()
        val response = PassphrasePromptResponse.Error(promptId, "Too many attempts")

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.ERROR, response.state)
        assertEquals("Too many attempts", response.message)
    }

    @Test
    fun passphrasePromptResponseCancelledState() {
        val promptId = PromptId.random()
        val response = PassphrasePromptResponse.Cancelled(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.CANCELLED, response.state)
    }

    // ConfirmationPromptRequest Tests

    @Test
    fun confirmationPromptRequestDefaultValues() {
        val request = ConfirmationPromptRequest(
            title = "Confirm",
            message = "Are you sure?"
        )

        assertNotNull(request.id)
        assertEquals("Confirm", request.title)
        assertEquals("Are you sure?", request.message)
        assertEquals("Confirm", request.confirmText)
        assertEquals("Cancel", request.cancelText)
        assertFalse(request.isDestructive)
    }

    @Test
    fun confirmationPromptRequestWithCustomValues() {
        val request = ConfirmationPromptRequest(
            title = "Delete",
            message = "Delete this item?",
            confirmText = "Delete",
            cancelText = "Keep",
            isDestructive = true
        )

        assertEquals("Delete", request.confirmText)
        assertEquals("Keep", request.cancelText)
        assertTrue(request.isDestructive)
    }

    // ConfirmationPromptResponse Tests

    @Test
    fun confirmationPromptResponseConfirmedState() {
        val promptId = PromptId.random()
        val response = ConfirmationPromptResponse.Confirmed(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.SUCCESS, response.state)
    }

    @Test
    fun confirmationPromptResponseDeclinedState() {
        val promptId = PromptId.random()
        val response = ConfirmationPromptResponse.Declined(promptId)

        assertEquals(promptId, response.promptId)
        assertEquals(PromptState.CANCELLED, response.state)
    }

    // Polymorphism Tests

    @Test
    fun requestsImplementPromptRequest() {
        val requests: List<PromptRequest> = listOf(
            NfcPromptRequest(title = "NFC", initialMessage = "Tap"),
            BiometricPromptRequest(title = "Bio", reason = "Auth"),
            BiometricUnlockPromptRequest(title = "Unlock", keyId = "key", reason = "Sign"),
            PassphrasePromptRequest(title = "PIN", purpose = "Unlock"),
            ConfirmationPromptRequest(title = "Confirm", message = "Sure?")
        )

        assertEquals(5, requests.size)
        requests.forEach { request ->
            assertNotNull(request.id)
            assertNotNull(request.presentationHint)
        }
    }

    @Test
    fun responsesImplementPromptResponse() {
        val promptId = PromptId.random()
        val responses: List<PromptResponse> = listOf(
            NfcPromptResponse.Success(promptId),
            NfcPromptResponse.Error(promptId, "error"),
            NfcPromptResponse.Cancelled(promptId),
            BiometricPromptResponse.Success(promptId),
            BiometricPromptResponse.Error(promptId, "error"),
            BiometricPromptResponse.Cancelled(promptId),
            BiometricUnlockPromptResponse.Success(promptId, "handle"),
            BiometricUnlockPromptResponse.Error(promptId, "error"),
            BiometricUnlockPromptResponse.Cancelled(promptId),
            PassphrasePromptResponse.Success(promptId, "1234"),
            PassphrasePromptResponse.Error(promptId, "error"),
            PassphrasePromptResponse.Cancelled(promptId),
            ConfirmationPromptResponse.Confirmed(promptId),
            ConfirmationPromptResponse.Declined(promptId)
        )

        assertEquals(14, responses.size)
        responses.forEach { response ->
            assertEquals(promptId, response.promptId)
            assertNotNull(response.state)
        }
    }
}
