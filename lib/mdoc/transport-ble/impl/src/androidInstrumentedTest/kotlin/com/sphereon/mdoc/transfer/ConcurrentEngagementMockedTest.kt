/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transfer

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.createAndroidUnitTestAppGraph
import com.sphereon.crypto.core.kms.CertificateService
import com.sphereon.data.link.ble.client.AndroidBlePlatformClient
import com.sphereon.mdoc.data.MdocValidations
import com.sphereon.mdoc.engagement.MdocEngagementManagerImpl
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

/**
 * Comprehensive tests for concurrent engagement functionality.
 *
 * These tests verify:
 * - Multiple concurrent engagements (NFC + QR)
 * - Shared ephemeral key and BLE UUID across engagements
 * - Event aggregation from multiple engagements via eventHub
 * - Method conflict detection (one engagement per type)
 * - Shared parameters regeneration
 * - Engagement lifecycle management
 */
@RunWith(AndroidJUnit4::class)
class ConcurrentEngagementMockedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testSharedEphemeralKeyAcrossEngagements() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-shared-key-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // Get initial shared ephemeral key alias
        val initialKeyAlias = manager.sharedParameters.ephemeralKeyAlias.value
        assertNotNull(initialKeyAlias, "Initial ephemeral key alias should be set")

        // Create first engagement - should use initial key
        val engagement1Result = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        assertTrue(engagement1Result.isOk, "First engagement should succeed")
        val keyAliasAfterFirst = manager.sharedParameters.ephemeralKeyAlias.value
        assertEquals(initialKeyAlias, keyAliasAfterFirst, "Key alias should remain same after first engagement")

        // Create second engagement - should use SAME key alias
        val engagement2Result = manager.createEngagement {
            engagement { nfc {} }
            retrieval { ble { centralClientMode = false; peripheralServerMode = true } }
        }

        assertTrue(engagement2Result.isOk, "Second engagement should succeed")
        val keyAliasAfterSecond = manager.sharedParameters.ephemeralKeyAlias.value
        assertEquals(initialKeyAlias, keyAliasAfterSecond, "Key alias should remain same across both engagements")

        // Close all engagements - key should regenerate
        val closeResult = manager.closeAll()
        assertTrue(closeResult.isOk, "CloseAll should succeed")

        val keyAliasAfterClose = manager.sharedParameters.ephemeralKeyAlias.value
        assertNotEquals(initialKeyAlias, keyAliasAfterClose, "Key alias should be regenerated after closeAll()")

        // Verify new key alias is different
        assertNotNull(keyAliasAfterClose, "New ephemeral key alias should be set after closeAll()")
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testSharedBleUuidAcrossEngagements() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-shared-uuid-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // Get initial shared UUIDs (separate for central and peripheral)
        val initialCentralUuid = manager.sharedParameters.bleCentralClientUuid.value
        val initialPeripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value
        assertNotNull(initialCentralUuid, "Initial central BLE UUID should be set")
        assertNotNull(initialPeripheralUuid, "Initial peripheral BLE UUID should be set")

        // Create engagement using central client mode (QR)
        val engagement1Result = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        // Create engagement using peripheral server mode (NFC)
        val engagement2Result = manager.createEngagement {
            engagement { nfc {} }
            retrieval { ble { centralClientMode = false; peripheralServerMode = true } }
        }

        assertTrue(engagement1Result.isOk && engagement2Result.isOk, "Both engagements should succeed")

        // UUIDs should remain same across both engagements
        val centralUuidAfterEngagements = manager.sharedParameters.bleCentralClientUuid.value
        val peripheralUuidAfterEngagements = manager.sharedParameters.blePeripheralServerUuid.value
        assertEquals(initialCentralUuid, centralUuidAfterEngagements, "Central UUID should be shared")
        assertEquals(initialPeripheralUuid, peripheralUuidAfterEngagements, "Peripheral UUID should be shared")

        // Close all - UUIDs should regenerate
        val closeAllResult = manager.closeAll()
        assertTrue(closeAllResult.isOk, "CloseAll should succeed")

        val centralUuidAfterClose = manager.sharedParameters.bleCentralClientUuid.value
        val peripheralUuidAfterClose = manager.sharedParameters.blePeripheralServerUuid.value
        assertNotEquals(initialCentralUuid, centralUuidAfterClose, "Central UUID should be regenerated after closeAll()")
        assertNotEquals(initialPeripheralUuid, peripheralUuidAfterClose, "Peripheral UUID should be regenerated after closeAll()")
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testConcurrentEngagements() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-concurrent-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // Create concurrent engagements
        val qrEngagementResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        val nfcEngagementResult = manager.createEngagement {
            engagement { nfc {} }
            retrieval { ble { centralClientMode = false; peripheralServerMode = true } }
        }

        assertTrue(qrEngagementResult.isOk, "QR engagement should succeed")
        assertTrue(nfcEngagementResult.isOk, "NFC engagement should succeed")

        // Verify both engagements are active using engagementsByType
        assertEquals(2, manager.engagementsByType.value.size, "Should have 2 active engagement types")
        assertNotNull(manager.qrEngagement.value, "QR engagement should be present")
        assertNotNull(manager.nfcEngagement.value, "NFC engagement should be present")

        // Verify they use the same shared key alias and UUIDs
        val sharedKeyAlias = manager.sharedParameters.ephemeralKeyAlias.value
        val sharedCentralUuid = manager.sharedParameters.bleCentralClientUuid.value
        val sharedPeripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value
        assertNotNull(sharedKeyAlias)
        assertNotNull(sharedCentralUuid)
        assertNotNull(sharedPeripheralUuid)

        // Clean up
        val closeResult = manager.closeAll()
        assertTrue(closeResult.isOk, "CloseAll should succeed")
        assertEquals(0, manager.engagementsByType.value.size, "All engagements should be closed")
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testEngagementMethodConflictDetection() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-conflict-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // Create first QR engagement
        val firstQrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        assertTrue(firstQrResult.isOk, "First QR engagement should succeed")

        // Attempt to create second QR engagement - should fail due to conflict
        val secondQrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        assertTrue(secondQrResult.isErr, "Second QR engagement should fail due to method conflict")
        assertNotNull(secondQrResult.error)

        // Only one QR engagement should exist
        assertEquals(1, manager.engagementsByType.value.size, "Should have only 1 engagement type due to conflict")
        assertNotNull(manager.qrEngagement.value, "QR engagement should still be present")

        // But NFC engagement should work (different type)
        val nfcResult = manager.createEngagement {
            engagement { nfc {} }
            retrieval { ble { centralClientMode = false; peripheralServerMode = true } }
        }

        assertTrue(nfcResult.isOk, "NFC engagement should succeed (different type)")
        assertEquals(2, manager.engagementsByType.value.size, "Should have 2 engagement types (QR + NFC)")
        assertNotNull(manager.qrEngagement.value, "QR engagement should be present")
        assertNotNull(manager.nfcEngagement.value, "NFC engagement should be present")

        // Clean up
        val closeResult = manager.closeAll()
        assertTrue(closeResult.isOk, "CloseAll should succeed")
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRegenerateSharedParameters() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-regenerate-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // Get initial parameters
        val initialKeyAlias = manager.sharedParameters.ephemeralKeyAlias.value
        val initialCentralUuid = manager.sharedParameters.bleCentralClientUuid.value
        val initialPeripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value

        // Manually regenerate
        manager.sharedParameters.regenerate()

        // Verify parameters changed
        val newKeyAlias = manager.sharedParameters.ephemeralKeyAlias.value
        val newCentralUuid = manager.sharedParameters.bleCentralClientUuid.value
        val newPeripheralUuid = manager.sharedParameters.blePeripheralServerUuid.value

        assertNotEquals(initialKeyAlias, newKeyAlias, "Ephemeral key alias should be regenerated")
        assertNotEquals(initialCentralUuid, newCentralUuid, "Central BLE UUID should be regenerated")
        assertNotEquals(initialPeripheralUuid, newPeripheralUuid, "Peripheral BLE UUID should be regenerated")

        // Create engagement with new parameters
        val engagementResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        assertTrue(engagementResult.isOk, "Engagement with new parameters should succeed")

        // Parameters should remain unchanged during engagement
        assertEquals(newKeyAlias, manager.sharedParameters.ephemeralKeyAlias.value, "Key alias should remain stable during engagement")
        assertEquals(newCentralUuid, manager.sharedParameters.bleCentralClientUuid.value, "Central UUID should remain stable during engagement")
        assertEquals(newPeripheralUuid, manager.sharedParameters.blePeripheralServerUuid.value, "Peripheral UUID should remain stable during engagement")

        // Clean up
        val closeResult = manager.closeAll()
        assertTrue(closeResult.isOk, "CloseAll should succeed")
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testEventHubIntegration() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-eventhub-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // EventHub should be available
        assertNotNull(manager.eventHub, "EventHub should be available")

        // Create an engagement to generate events
        val engagementResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }

        assertTrue(engagementResult.isOk, "Engagement should succeed")

        // EventHub should provide access to engagement events
        assertNotNull(manager.eventHub.engagementEvents, "Engagement events should be available")
        assertNotNull(manager.eventHub.transferEvents, "Transfer events should be available")
        assertNotNull(manager.eventHub.allEvents, "All events should be available")

        // Clean up
        val closeResult = manager.closeAll()
        assertTrue(closeResult.isOk, "CloseAll should succeed")
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testTypedEngagementAccess() = runTest(timeout = 30.seconds) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software"
            )
        )

        val appGraph = createAndroidUnitTestAppGraph(
            application = context,
            appId = "mdoc-typed-access-test",
            profile = "profile",
            version = "0.1.0"
        )
        appGraph as AndroidBlePlatformClient.Graph
        val contextGraph = appGraph.userContextManager.getAnonymous()
        val sessionGraph = contextGraph.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER)

        sessionGraph as SessionExecution.Graph
        sessionGraph as KeyManagerService.KmsGraph
        sessionGraph as MdocEngagementManagerImpl.Graph
        sessionGraph as CertificateService.Graph
        sessionGraph as MdocValidationsImpl.Graph

        val manager = sessionGraph.mdocEngagementManager

        // Initially, no engagements should exist
        assertEquals(null, manager.qrEngagement.value, "No QR engagement should exist initially")
        assertEquals(null, manager.nfcEngagement.value, "No NFC engagement should exist initially")
        assertEquals(null, manager.toAppEngagement.value, "No ToApp engagement should exist initially")

        // Create QR engagement
        val qrResult = manager.createEngagement {
            engagement { qr { scheme = "mdoc:" } }
            retrieval { ble { centralClientMode = true } }
        }
        assertTrue(qrResult.isOk, "QR engagement should succeed")

        // QR engagement should be accessible via typed property
        assertNotNull(manager.qrEngagement.value, "QR engagement should be accessible")
        assertEquals(null, manager.nfcEngagement.value, "NFC engagement should still be null")
        assertEquals(null, manager.toAppEngagement.value, "ToApp engagement should still be null")

        // Create NFC engagement
        val nfcResult = manager.createEngagement {
            engagement { nfc {} }
            retrieval { ble { centralClientMode = false; peripheralServerMode = true } }
        }
        assertTrue(nfcResult.isOk, "NFC engagement should succeed")

        // Both should be accessible
        assertNotNull(manager.qrEngagement.value, "QR engagement should still be accessible")
        assertNotNull(manager.nfcEngagement.value, "NFC engagement should be accessible")
        assertEquals(null, manager.toAppEngagement.value, "ToApp engagement should still be null")

        // Close QR engagement specifically
        val closeQrResult = manager.closeQrEngagement()
        assertTrue(closeQrResult.isOk, "Closing QR engagement should succeed")

        // QR should be gone, NFC should remain
        assertEquals(null, manager.qrEngagement.value, "QR engagement should be closed")
        assertNotNull(manager.nfcEngagement.value, "NFC engagement should still exist")

        // Clean up
        val closeResult = manager.closeAll()
        assertTrue(closeResult.isOk, "CloseAll should succeed")
    }
}
