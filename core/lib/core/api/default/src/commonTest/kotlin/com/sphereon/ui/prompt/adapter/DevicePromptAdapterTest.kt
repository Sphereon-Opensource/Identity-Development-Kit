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

package com.sphereon.ui.prompt.adapter

import com.sphereon.ui.prompt.coordinator.PromptCoordinatorImpl
import com.sphereon.ui.prompt.core.NfcPromptRequest
import com.sphereon.ui.prompt.core.NfcPromptResponse
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.presenter.ForegroundState
import com.sphereon.ui.prompt.presenter.PromptPresenter
import com.sphereon.ui.prompt.presenter.PromptPresenterImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicePromptAdapterTest {
    private fun createTestPresenter() = PromptPresenterImpl()

    private fun createAdapter(): Pair<DefaultDevicePromptAdapter, PromptPresenterImpl> {
        val presenter = createTestPresenter()
        val coordinator = PromptCoordinatorImpl(presenter)
        val adapter = DefaultDevicePromptAdapter(coordinator, presenter)
        return adapter to presenter
    }

    @Test
    fun handleNfcTagDetectedCreatesPrompt() =
        runTest {
            val (adapter, _) = createAdapter()
            val event = DeviceEvent.NfcTagDetected(tagId = "test-tag-123")

            val handle = adapter.handleEvent(event)

            assertNotNull(handle)
            assertTrue(handle.isActive)
        }

    @Test
    fun handleNfcTagDetectedWithDataCreatesPrompt() =
        runTest {
            val (adapter, _) = createAdapter()
            val tagData = byteArrayOf(0x01, 0x02, 0x03)
            val event = DeviceEvent.NfcTagDetected(tagId = "test-tag", tagData = tagData)

            val handle = adapter.handleEvent(event)

            assertNotNull(handle)
            assertTrue(handle.isActive)
        }

    @Test
    fun handleBiometricRequiredReturnsNull() =
        runTest {
            val (adapter, _) = createAdapter()
            val event = DeviceEvent.BiometricRequired(reason = "Unlock wallet")

            val handle = adapter.handleEvent(event)

            assertNull(handle)
        }

    @Test
    fun handleBiometricRequiredWithKeyIdReturnsNull() =
        runTest {
            val (adapter, _) = createAdapter()
            val event = DeviceEvent.BiometricRequired(reason = "Sign document", keyId = "key-123")

            val handle = adapter.handleEvent(event)

            assertNull(handle)
        }

    @Test
    fun handleAppForegroundReturnsNullAndUpdatesPresenter() =
        runTest {
            val (adapter, presenter) = createAdapter()
            presenter.onBackground() // Start in background
            assertEquals(ForegroundState.BACKGROUND, presenter.foregroundState.value)

            val handle = adapter.handleEvent(DeviceEvent.AppForeground)

            assertNull(handle)
            assertEquals(ForegroundState.FOREGROUND, presenter.foregroundState.value)
        }

    @Test
    fun handleAppBackgroundReturnsNullAndUpdatesPresenter() =
        runTest {
            val (adapter, presenter) = createAdapter()
            presenter.onForeground() // Start in foreground
            assertEquals(ForegroundState.FOREGROUND, presenter.foregroundState.value)

            val handle = adapter.handleEvent(DeviceEvent.AppBackground)

            assertNull(handle)
            assertEquals(ForegroundState.BACKGROUND, presenter.foregroundState.value)
        }
}

class DeviceEventTest {
    // NfcTagDetected tests

    @Test
    fun nfcTagDetectedProperties() {
        val tagData = byteArrayOf(0x01, 0x02, 0x03)
        val event = DeviceEvent.NfcTagDetected(tagId = "tag-123", tagData = tagData)

        assertEquals("tag-123", event.tagId)
        assertTrue(event.tagData!!.contentEquals(tagData))
    }

    @Test
    fun nfcTagDetectedWithNullData() {
        val event = DeviceEvent.NfcTagDetected(tagId = "tag-456")

        assertEquals("tag-456", event.tagId)
        assertNull(event.tagData)
    }

    @Test
    fun nfcTagDetectedEquality() {
        val tagData = byteArrayOf(0x01, 0x02)
        val event1 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = tagData)
        val event2 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = byteArrayOf(0x01, 0x02))

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
    }

    @Test
    fun nfcTagDetectedInequalityDifferentTagId() {
        val event1 = DeviceEvent.NfcTagDetected(tagId = "tag-1")
        val event2 = DeviceEvent.NfcTagDetected(tagId = "tag-2")

        assertFalse(event1 == event2)
    }

    @Test
    fun nfcTagDetectedInequalityDifferentData() {
        val event1 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = byteArrayOf(0x01))
        val event2 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = byteArrayOf(0x02))

        assertFalse(event1 == event2)
    }

    @Test
    fun nfcTagDetectedInequalityNullVsData() {
        val event1 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = null)
        val event2 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = byteArrayOf(0x01))

        assertFalse(event1 == event2)
    }

    @Test
    fun nfcTagDetectedEqualityBothNull() {
        val event1 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = null)
        val event2 = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = null)

        assertEquals(event1, event2)
    }

    @Test
    fun nfcTagDetectedSelfEquality() {
        val event = DeviceEvent.NfcTagDetected(tagId = "tag", tagData = byteArrayOf(0x01))

        assertEquals(event, event)
    }

    @Test
    fun nfcTagDetectedNotEqualToNull() {
        val event = DeviceEvent.NfcTagDetected(tagId = "tag")

        assertFalse(event.equals(null))
    }

    @Test
    fun nfcTagDetectedNotEqualToDifferentType() {
        val event = DeviceEvent.NfcTagDetected(tagId = "tag")

        assertFalse(event.equals("not an event"))
    }

    // BiometricRequired tests

    @Test
    fun biometricRequiredProperties() {
        val event = DeviceEvent.BiometricRequired(reason = "Unlock", keyId = "key-123")

        assertEquals("Unlock", event.reason)
        assertEquals("key-123", event.keyId)
    }

    @Test
    fun biometricRequiredWithNullKeyId() {
        val event = DeviceEvent.BiometricRequired(reason = "Auth")

        assertEquals("Auth", event.reason)
        assertNull(event.keyId)
    }

    @Test
    fun biometricRequiredEquality() {
        val event1 = DeviceEvent.BiometricRequired(reason = "Unlock", keyId = "key")
        val event2 = DeviceEvent.BiometricRequired(reason = "Unlock", keyId = "key")

        assertEquals(event1, event2)
    }

    // AppForeground and AppBackground tests

    @Test
    fun appForegroundIsSingleton() {
        val event1 = DeviceEvent.AppForeground
        val event2 = DeviceEvent.AppForeground

        assertTrue(event1 === event2)
    }

    @Test
    fun appBackgroundIsSingleton() {
        val event1 = DeviceEvent.AppBackground
        val event2 = DeviceEvent.AppBackground

        assertTrue(event1 === event2)
    }

    @Test
    fun allEventsAreDeviceEvents() {
        val events: List<DeviceEvent> =
            listOf(
                DeviceEvent.NfcTagDetected(tagId = "tag"),
                DeviceEvent.BiometricRequired(reason = "test"),
                DeviceEvent.AppForeground,
                DeviceEvent.AppBackground,
            )

        assertEquals(4, events.size)
    }
}
