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

import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Base interface for all prompt requests.
 * Requests are data-only containers - NO lambdas or callbacks.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptRequest", exact = true)
interface PromptRequest {
    /**
     * Unique identifier for this prompt request.
     */
    val id: PromptId

    /**
     * Presentation hints for scheduling and display.
     */
    val presentationHint: PromptPresentationHint
}

/**
 * Base interface for single-screen prompt requests.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("SinglePromptRequest", exact = true)
interface SinglePromptRequest : PromptRequest {
    /**
     * Whether to show the prompt UI.
     */
    val show: Boolean get() = true

    /**
     * Title to display in the prompt.
     */
    val title: String

    /**
     * Optional subtitle for additional context.
     */
    val subtitle: String? get() = null
}

/**
 * NFC prompt request - data only, no lambda.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("NfcPromptRequest", exact = true)
data class NfcPromptRequest(
    override val id: PromptId = PromptId.random(),
    override val title: String,
    override val subtitle: String? = null,
    override val show: Boolean = true,
    override val presentationHint: PromptPresentationHint = PromptPresentationHint.DEFAULT,
    val initialMessage: String,
    val successMessage: String? = null,
    val errorMessage: String? = null
) : SinglePromptRequest

/**
 * Biometric authentication prompt request.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("BiometricPromptRequest", exact = true)
data class BiometricPromptRequest(
    override val id: PromptId = PromptId.random(),
    override val title: String,
    override val subtitle: String? = null,
    override val show: Boolean = true,
    override val presentationHint: PromptPresentationHint = PromptPresentationHint(priority = PromptPriority.HIGH),
    val reason: String,
    val fallbackToPasscode: Boolean = true
) : SinglePromptRequest

/**
 * Biometric unlock request for unlocking a key in the secure element.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("BiometricUnlockPromptRequest", exact = true)
data class BiometricUnlockPromptRequest(
    override val id: PromptId = PromptId.random(),
    override val title: String,
    override val subtitle: String? = null,
    override val show: Boolean = true,
    override val presentationHint: PromptPresentationHint = PromptPresentationHint(priority = PromptPriority.HIGH),
    val keyId: String,
    val reason: String,
    val fallbackToPasscode: Boolean = true
) : SinglePromptRequest

/**
 * Passphrase/PIN entry prompt request.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PassphrasePromptRequest", exact = true)
data class PassphrasePromptRequest(
    override val id: PromptId = PromptId.random(),
    override val title: String,
    override val subtitle: String? = null,
    override val show: Boolean = true,
    override val presentationHint: PromptPresentationHint = PromptPresentationHint(priority = PromptPriority.HIGH),
    val purpose: String,
    val minLength: Int = 4,
    val maxLength: Int? = null,
    val isNumericOnly: Boolean = false
) : SinglePromptRequest

/**
 * Confirmation dialog prompt request.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("ConfirmationPromptRequest", exact = true)
data class ConfirmationPromptRequest(
    override val id: PromptId = PromptId.random(),
    override val title: String,
    override val subtitle: String? = null,
    override val show: Boolean = true,
    override val presentationHint: PromptPresentationHint = PromptPresentationHint.DEFAULT,
    val message: String,
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel",
    val isDestructive: Boolean = false
) : SinglePromptRequest
