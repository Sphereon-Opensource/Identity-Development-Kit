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

package com.sphereon.mdoc.engagement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for MdocEngagementState enum and MdocEngagementStateType interface.
 */
class MdocEngagementStateTest {

    @Test
    fun testInitState() {
        val state = MdocEngagementState.INIT
        assertEquals("INIT", state.state)
        assertEquals(10, state.order)
        assertEquals(MdocEngagementState.INIT, state.asEngagementState)
    }

    @Test
    fun testStartState() {
        val state = MdocEngagementState.START
        assertEquals("START", state.state)
        assertEquals(20, state.order)
    }

    @Test
    fun testBleScanningState() {
        val state = MdocEngagementState.BLE_SCANNING
        assertEquals("BLE_SCANNING", state.state)
        assertEquals(30, state.order)
    }

    @Test
    fun testBleAdvertisingState() {
        val state = MdocEngagementState.BLE_ADVERTISING
        assertEquals("BLE_ADVERTISING", state.state)
        assertEquals(30, state.order) // Same order as BLE_SCANNING
    }

    @Test
    fun testNfcEnabledState() {
        val state = MdocEngagementState.NFC_ENABLED
        assertEquals("NFC_ENABLED", state.state)
        assertEquals(30, state.order) // Same order as BLE states
    }

    @Test
    fun testConnectingState() {
        val state = MdocEngagementState.CONNECTING
        assertEquals("CONNECTING", state.state)
        assertEquals(50, state.order)
    }

    @Test
    fun testConnectedState() {
        val state = MdocEngagementState.CONNECTED
        assertEquals("CONNECTED", state.state)
        assertEquals(100, state.order)
    }

    @Test
    fun testDataState() {
        val state = MdocEngagementState.DATA
        assertEquals("DATA", state.state)
        assertEquals(110, state.order)
    }

    @Test
    fun testTransferState() {
        val state = MdocEngagementState.TRANSFER
        assertEquals("TRANSFER", state.state)
        assertEquals(150, state.order)
    }

    @Test
    fun testCanceledState() {
        val state = MdocEngagementState.CANCELED
        assertEquals("CANCELED", state.state)
        assertEquals(280, state.order)
    }

    @Test
    fun testErrorState() {
        val state = MdocEngagementState.ERROR
        assertEquals("ERROR", state.state)
        assertEquals(290, state.order)
    }

    @Test
    fun testDisconnectedState() {
        val state = MdocEngagementState.DISCONNECTED
        assertEquals("DISCONNECTED", state.state)
        assertEquals(300, state.order)
    }

    @Test
    fun testAllEntriesCount() {
        assertEquals(12, MdocEngagementState.entries.size)
    }

    @Test
    fun testStateTypeCompanionEntries() {
        val entries = MdocEngagementStateType.entries
        assertEquals(12, entries.size)
        assertTrue(entries.contains(MdocEngagementState.INIT))
        assertTrue(entries.contains(MdocEngagementState.DISCONNECTED))
    }

    @Test
    fun testStateProgressionFromInit() {
        // Verify typical happy path progression
        assertTrue(MdocEngagementState.INIT.order < MdocEngagementState.START.order)
        assertTrue(MdocEngagementState.START.order < MdocEngagementState.CONNECTING.order)
        assertTrue(MdocEngagementState.CONNECTING.order < MdocEngagementState.CONNECTED.order)
        assertTrue(MdocEngagementState.CONNECTED.order < MdocEngagementState.DATA.order)
        assertTrue(MdocEngagementState.DATA.order < MdocEngagementState.TRANSFER.order)
    }

    @Test
    fun testParallelStatesHaveSameOrder() {
        // BLE_SCANNING, BLE_ADVERTISING, and NFC_ENABLED are parallel states
        assertEquals(MdocEngagementState.BLE_SCANNING.order, MdocEngagementState.BLE_ADVERTISING.order)
        assertEquals(MdocEngagementState.BLE_SCANNING.order, MdocEngagementState.NFC_ENABLED.order)
    }

    @Test
    fun testTerminalStatesHaveHighestOrder() {
        val terminalStates = listOf(
            MdocEngagementState.CANCELED,
            MdocEngagementState.ERROR,
            MdocEngagementState.DISCONNECTED
        )
        val nonTerminalStates = MdocEngagementState.entries.filter { it !in terminalStates }

        for (terminal in terminalStates) {
            for (nonTerminal in nonTerminalStates) {
                assertTrue(terminal.order >= nonTerminal.order,
                    "Terminal state ${terminal.state} should have order >= ${nonTerminal.state}")
            }
        }
    }

    @Test
    fun testValueOfForAllStates() {
        MdocEngagementState.entries.forEach { state ->
            val retrieved = MdocEngagementState.valueOf(state.name)
            assertEquals(state, retrieved)
        }
    }

    @Test
    fun testAsEngagementStateForAllStates() {
        MdocEngagementState.entries.forEach { state ->
            assertEquals(state, state.asEngagementState)
        }
    }

    @Test
    fun testStateNameIsUppercase() {
        MdocEngagementState.entries.forEach { state ->
            assertEquals(state.name.uppercase(), state.state)
        }
    }
}
