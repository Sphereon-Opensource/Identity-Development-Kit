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
 * Base interface for all prompt responses.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("PromptResponse", exact = true)
interface PromptResponse {
    /**
     * The ID of the prompt this response is for.
     */
    val promptId: PromptId

    /**
     * The resulting state after the response.
     */
    val state: PromptState
}

/**
 * Response for NFC prompts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcPromptResponse", exact = true)
sealed interface NfcPromptResponse : PromptResponse {
    data class Success(
        override val promptId: PromptId,
        val tagData: String? = null
    ) : NfcPromptResponse {
        override val state: PromptState = PromptState.SUCCESS
    }

    data class Error(
        override val promptId: PromptId,
        val message: String,
        val errorCode: String? = null
    ) : NfcPromptResponse {
        override val state: PromptState = PromptState.ERROR
    }

    data class Cancelled(
        override val promptId: PromptId
    ) : NfcPromptResponse {
        override val state: PromptState = PromptState.CANCELLED
    }
}

/**
 * Response for biometric prompts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BiometricPromptResponse", exact = true)
sealed interface BiometricPromptResponse : PromptResponse {
    data class Success(
        override val promptId: PromptId
    ) : BiometricPromptResponse {
        override val state: PromptState = PromptState.SUCCESS
    }

    data class Error(
        override val promptId: PromptId,
        val message: String,
        val errorCode: String? = null
    ) : BiometricPromptResponse {
        override val state: PromptState = PromptState.ERROR
    }

    data class Cancelled(
        override val promptId: PromptId
    ) : BiometricPromptResponse {
        override val state: PromptState = PromptState.CANCELLED
    }
}

/**
 * Response for biometric unlock prompts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BiometricUnlockPromptResponse", exact = true)
sealed interface BiometricUnlockPromptResponse : PromptResponse {
    data class Success(
        override val promptId: PromptId,
        val keyHandle: String
    ) : BiometricUnlockPromptResponse {
        override val state: PromptState = PromptState.SUCCESS
    }

    data class Error(
        override val promptId: PromptId,
        val message: String,
        val errorCode: String? = null
    ) : BiometricUnlockPromptResponse {
        override val state: PromptState = PromptState.ERROR
    }

    data class Cancelled(
        override val promptId: PromptId
    ) : BiometricUnlockPromptResponse {
        override val state: PromptState = PromptState.CANCELLED
    }
}

/**
 * Response for passphrase prompts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PassphrasePromptResponse", exact = true)
sealed interface PassphrasePromptResponse : PromptResponse {
    data class Success(
        override val promptId: PromptId,
        val passphrase: String
    ) : PassphrasePromptResponse {
        override val state: PromptState = PromptState.SUCCESS
    }

    data class Error(
        override val promptId: PromptId,
        val message: String
    ) : PassphrasePromptResponse {
        override val state: PromptState = PromptState.ERROR
    }

    data class Cancelled(
        override val promptId: PromptId
    ) : PassphrasePromptResponse {
        override val state: PromptState = PromptState.CANCELLED
    }
}

/**
 * Response for confirmation prompts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfirmationPromptResponse", exact = true)
sealed interface ConfirmationPromptResponse : PromptResponse {
    data class Confirmed(
        override val promptId: PromptId
    ) : ConfirmationPromptResponse {
        override val state: PromptState = PromptState.SUCCESS
    }

    data class Declined(
        override val promptId: PromptId
    ) : ConfirmationPromptResponse {
        override val state: PromptState = PromptState.CANCELLED
    }
}
