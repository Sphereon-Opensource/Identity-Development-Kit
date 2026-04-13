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

package com.sphereon.mdoc.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for MdocRetrievalState enum and MdocRetrievalStateType interface.
 */
class MdocRetrievalStateTypeTest {
    @Test
    fun testInitState() {
        val state = MdocRetrievalState.INIT
        assertEquals("INIT", state.state)
        assertEquals(110, state.order)
    }

    @Test
    fun testTransmissionTypeSelectedState() {
        val state = MdocRetrievalState.TRANSMISSION_TYPE_SELECTED
        assertEquals("TRANSMISSION_TYPE_SELECTED", state.state)
        assertEquals(120, state.order)
    }

    @Test
    fun testSessionEstablishmentReceivedState() {
        val state = MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED
        assertEquals("SESSION_ESTABLISHMENT_RECEIVED", state.state)
        assertEquals(130, state.order)
    }

    @Test
    fun testDocumentsSelectionProcessStartState() {
        val state = MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_START
        assertEquals("DOCUMENTS_SELECTION_PROCESS_START", state.state)
        assertEquals(150, state.order)
    }

    @Test
    fun testDocumentsSelectionProcessAcceptedState() {
        val state = MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_ACCEPTED
        assertEquals("DOCUMENTS_SELECTION_PROCESS_ACCEPTED", state.state)
        assertEquals(160, state.order)
    }

    @Test
    fun testSessionDataSendState() {
        val state = MdocRetrievalState.SESSION_DATA_SEND
        assertEquals("SESSION_DATA_SEND", state.state)
        assertEquals(170, state.order)
    }

    @Test
    fun testSessionDataReceivedState() {
        val state = MdocRetrievalState.SESSION_DATA_RECEIVED
        assertEquals("SESSION_DATA_RECEIVED", state.state)
        assertEquals(171, state.order)
    }

    @Test
    fun testDocumentsSelectionProcessDeclinedState() {
        val state = MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_DECLINED
        assertEquals("DOCUMENTS_SELECTION_PROCESS_DECLINED", state.state)
        assertEquals(280, state.order)
    }

    @Test
    fun testErrorState() {
        val state = MdocRetrievalState.ERROR
        assertEquals("ERROR", state.state)
        assertEquals(290, state.order)
    }

    @Test
    fun testTerminatedState() {
        val state = MdocRetrievalState.TERMINATED
        assertEquals("TERMINATED", state.state)
        assertEquals(300, state.order)
    }

    @Test
    fun testAllEntriesCount() {
        assertEquals(10, MdocRetrievalState.entries.size)
    }

    @Test
    fun testStateTypeCompanionEntries() {
        val entries = MdocRetrievalStateType.entries
        assertEquals(10, entries.size)
        assertTrue(entries.contains(MdocRetrievalState.INIT))
        assertTrue(entries.contains(MdocRetrievalState.TERMINATED))
    }

    @Test
    fun testAsEngagementState() {
        val stateType: MdocRetrievalStateType = MdocRetrievalState.INIT
        assertEquals(MdocRetrievalState.INIT, stateType.asEngagementState)
    }

    @Test
    fun testStateProgression() {
        // Verify typical happy path progression
        assertTrue(MdocRetrievalState.INIT.order < MdocRetrievalState.TRANSMISSION_TYPE_SELECTED.order)
        assertTrue(MdocRetrievalState.TRANSMISSION_TYPE_SELECTED.order < MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED.order)
        assertTrue(MdocRetrievalState.SESSION_ESTABLISHMENT_RECEIVED.order < MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_START.order)
        assertTrue(MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_START.order < MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_ACCEPTED.order)
        assertTrue(MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_ACCEPTED.order < MdocRetrievalState.SESSION_DATA_SEND.order)
    }

    @Test
    fun testTerminalStatesHaveHighestOrder() {
        val terminalStates =
            listOf(
                MdocRetrievalState.DOCUMENTS_SELECTION_PROCESS_DECLINED,
                MdocRetrievalState.ERROR,
                MdocRetrievalState.TERMINATED,
            )

        val nonTerminalStates = MdocRetrievalState.entries.filter { it !in terminalStates }

        for (terminal in terminalStates) {
            for (nonTerminal in nonTerminalStates) {
                assertTrue(
                    terminal.order >= nonTerminal.order,
                    "Terminal state ${terminal.state} should have order >= ${nonTerminal.state}",
                )
            }
        }
    }

    @Test
    fun testValueOfForAllStates() {
        MdocRetrievalState.entries.forEach { state ->
            val retrieved = MdocRetrievalState.valueOf(state.name)
            assertEquals(state, retrieved)
        }
    }

    @Test
    fun testStateNameIsUppercase() {
        MdocRetrievalState.entries.forEach { state ->
            assertEquals(state.name.uppercase(), state.state)
        }
    }

    @Test
    fun testAsEngagementStateForAllStates() {
        MdocRetrievalState.entries.forEach { state ->
            assertEquals(state, state.asEngagementState)
        }
    }
}
