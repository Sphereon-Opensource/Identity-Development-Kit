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

package com.sphereon.mdoc.transfer

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.engagement.MdocEngagementState
import com.sphereon.mdoc.engagement.MdocEngagementStateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
 * Core abstract base class for all mdoc transfer implementations.
 * Provides common state management, logging, and lifecycle operations.
 */
sealed class AbstractMdocTransfer<OpenResult : Any>(
    override val connectionMethod: ConnectionMethod,
    val execution: SessionExecution
) : IMdocTransfer<OpenResult> {
    protected val _state = MutableStateFlow(MdocEngagementState.INIT)
    override val engagementState: StateFlow<MdocEngagementState> = _state.asStateFlow()
    abstract override val role: MdocRole

    val log = execution.log.logManager.withTag("MdocTransfer-${this.hashCode()}")
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    protected val mutex: Mutex = Mutex()
    protected var currentJob: Job? = null

    override fun getCurrentState(): MdocEngagementStateType = _state.value

    override fun close() {
        log.info("Closing mdoc transfer")
        currentJob?.cancel()

        scope.launch {
            mutex.withLock {
                if (getCurrentState().order >= MdocEngagementState.ERROR.order) {
                    return@launch
                }
                closeWithoutDelay()
            }
        }
    }

    private fun closeWithoutDelay() {
        check(mutex.isLocked) { "closeWithoutDelay called without holding lock" }
        _state.value = MdocEngagementState.DISCONNECTED
    }

    protected suspend fun assertConnected() {
        mutex.withLock {
            if (_state.value != MdocEngagementState.CONNECTED) {
                throw IllegalStateException("State is ${_state.value.name}, expected ${MdocEngagementState.CONNECTED.name}")
            }
        }
    }
}
