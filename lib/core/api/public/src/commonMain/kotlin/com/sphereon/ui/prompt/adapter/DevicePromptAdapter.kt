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

package com.sphereon.ui.prompt.adapter

import com.sphereon.ui.prompt.coordinator.PromptCoordinator
import com.sphereon.ui.prompt.core.NfcPromptRequest
import com.sphereon.ui.prompt.core.NfcPromptResponse
import com.sphereon.ui.prompt.core.PromptHandle
import com.sphereon.ui.prompt.core.PromptOutcome
import com.sphereon.ui.prompt.core.PromptPresentationHint
import com.sphereon.ui.prompt.presenter.PromptPresenter
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Represents device events that can trigger prompts.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEvent", exact = true)
sealed interface DeviceEvent {
    /**
     * NFC tag was detected.
     */
    data class NfcTagDetected(
        val tagId: String,
        val tagData: ByteArray? = null
    ) : DeviceEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as NfcTagDetected
            if (tagId != other.tagId) return false
            if (tagData != null) {
                if (other.tagData == null) return false
                if (!tagData.contentEquals(other.tagData)) return false
            } else if (other.tagData != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = tagId.hashCode()
            result = 31 * result + (tagData?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Biometric authentication is required.
     */
    data class BiometricRequired(
        val reason: String,
        val keyId: String? = null
    ) : DeviceEvent

    /**
     * App entered foreground.
     */
    data object AppForeground : DeviceEvent

    /**
     * App entered background.
     */
    data object AppBackground : DeviceEvent
}

/**
 * Adapter that listens to device events and creates appropriate prompts.
 *
 * This provides a clean separation between device-level events and the prompt system.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DevicePromptAdapter", exact = true)
interface DevicePromptAdapter {
    /**
     * Handles a device event and optionally creates a prompt.
     *
     * @param event The device event.
     * @return A prompt handle if a prompt was created, null otherwise.
     */
    suspend fun handleEvent(event: DeviceEvent): PromptHandle<*, *>?
}

/**
 * Default implementation of DevicePromptAdapter.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultDevicePromptAdapter", exact = true)
class DefaultDevicePromptAdapter(
    private val coordinator: PromptCoordinator,
    private val presenter: PromptPresenter
) : DevicePromptAdapter {

    override suspend fun handleEvent(event: DeviceEvent): PromptHandle<*, *>? {
        return when (event) {
            is DeviceEvent.NfcTagDetected -> handleNfcTag(event)
            is DeviceEvent.BiometricRequired -> null // Platform-specific
            is DeviceEvent.AppForeground -> {
                presenter.onForeground()
                null
            }
            is DeviceEvent.AppBackground -> {
                presenter.onBackground()
                null
            }
        }
    }

    private suspend fun handleNfcTag(event: DeviceEvent.NfcTagDetected): PromptHandle<NfcPromptRequest, NfcPromptResponse> {
        val request = NfcPromptRequest(
            title = "NFC Tag Detected",
            initialMessage = "Reading tag...",
            presentationHint = PromptPresentationHint.CRITICAL
        )

        return coordinator.request(request)
    }
}
