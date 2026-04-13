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

import com.sphereon.ui.prompt.core.PromptId
import com.sphereon.ui.prompt.core.PromptRequest
import com.sphereon.ui.prompt.core.PromptState
import com.sphereon.ui.prompt.presenter.PromptPresenter
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Callback for when a prompt is released (completed, cancelled, or timed out).
 */
typealias OnReleaseCallback = (PromptId, PromptState) -> Unit

/**
 * Result of scheduling a prompt.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScheduleResult", exact = true)
sealed interface ScheduleResult {
    /**
     * Prompt was scheduled for immediate presentation.
     */
    data object Immediate : ScheduleResult

    /**
     * Prompt was deferred (e.g., app is in background).
     */
    data object Deferred : ScheduleResult
}

/**
 * A prompt that has been deferred for later presentation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeferredPrompt", exact = true)
data class DeferredPrompt(
    val request: PromptRequest,
    val deferredAt: Instant
)

/**
 * Scheduler for managing prompt queue with priority ordering.
 *
 * The scheduler:
 * - Determines if prompts should be presented immediately or deferred
 * - Maintains a priority queue of deferred prompts
 * - Notifies via onRelease callback when prompts are released
 * - Releases deferred prompts when app comes to foreground
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptScheduler", exact = true)
interface PromptScheduler {
    /**
     * Flow of prompts that are ready to be presented.
     * UI layer should collect this to display prompts.
     */
    val activePrompts: SharedFlow<PromptRequest>

    /**
     * Schedules a prompt for presentation.
     *
     * @param request The prompt request to schedule.
     * @return ScheduleResult indicating if prompt was scheduled immediately or deferred.
     */
    suspend fun schedule(request: PromptRequest): ScheduleResult

    /**
     * Releases a prompt (removes from active state).
     * Should be called when a prompt is completed, cancelled, or times out.
     */
    suspend fun release(id: PromptId, state: PromptState)

    /**
     * Sets the release callback invoked when deferred prompts are released.
     */
    fun setOnRelease(callback: OnReleaseCallback?)

    /**
     * Tries to present the next pending prompt if possible.
     */
    suspend fun tryPresentNext()

    /**
     * Gets the current queue size (deferred prompts).
     */
    val queueSize: Int

    /**
     * Gets the currently active prompt ID, if any.
     */
    val currentPromptId: PromptId?
}

/**
 * Default implementation of PromptScheduler.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptSchedulerImpl", exact = true)
class PromptSchedulerImpl(
    private val presenter: PromptPresenter
) : PromptScheduler {

    private val mutex = Mutex()

    // Priority queues of deferred prompts, one per priority level
    private val deferredQueues = mutableMapOf<Int, ArrayDeque<DeferredPrompt>>()

    // Currently active prompt ID
    private var _currentPromptId: PromptId? = null

    // Release callback
    private var onReleaseCallback: OnReleaseCallback? = null

    // Flow for active prompts
    private val _activePrompts = MutableSharedFlow<PromptRequest>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val activePrompts: SharedFlow<PromptRequest> = _activePrompts.asSharedFlow()

    override val queueSize: Int
        get() = deferredQueues.values.sumOf { it.size }

    override val currentPromptId: PromptId?
        get() = _currentPromptId

    override suspend fun schedule(request: PromptRequest): ScheduleResult = mutex.withLock {
        // Check if we should defer this prompt
        if (presenter.shouldDeferPrompt(request)) {
            // Add to deferred queue, organized by priority
            val priorityOrder = request.presentationHint.priority.order
            val queue = deferredQueues.getOrPut(priorityOrder) { ArrayDeque() }
            queue.addLast(DeferredPrompt(request, Clock.System.now()))
            return@withLock ScheduleResult.Deferred
        }

        // Check if there's a current prompt with lower priority
        val currentId = _currentPromptId
        if (currentId != null) {
            // There's already an active prompt - check if we should preempt
            // For now, queue the new one if there's already an active prompt
            val priorityOrder = request.presentationHint.priority.order
            val queue = deferredQueues.getOrPut(priorityOrder) { ArrayDeque() }
            queue.addLast(DeferredPrompt(request, Clock.System.now()))
            return@withLock ScheduleResult.Deferred
        }

        // Present immediately
        _currentPromptId = request.id
        _activePrompts.emit(request)
        ScheduleResult.Immediate
    }

    override suspend fun release(id: PromptId, state: PromptState) {
        mutex.withLock {
            // Remove from current if it matches
            if (_currentPromptId == id) {
                _currentPromptId = null
                onReleaseCallback?.invoke(id, state)

                // Try to present the next deferred prompt
                presentNextDeferred()
            } else {
                // Check deferred queues
                for (queue in deferredQueues.values) {
                    val removed = queue.removeAll { it.request.id == id }
                    if (removed) {
                        onReleaseCallback?.invoke(id, state)
                        break
                    }
                }
            }
        }
    }

    override fun setOnRelease(callback: OnReleaseCallback?) {
        onReleaseCallback = callback
    }

    override suspend fun tryPresentNext() {
        mutex.withLock {
            if (_currentPromptId == null) {
                presentNextDeferred()
            }
        }
    }

    /**
     * Presents the next deferred prompt in priority order.
     * Must be called while holding the mutex.
     */
    private suspend fun presentNextDeferred() {
        // Get priorities sorted by order (lowest order = highest priority)
        val sortedPriorities = deferredQueues.keys.sorted()

        for (priority in sortedPriorities) {
            val queue = deferredQueues[priority] ?: continue
            val iterator = queue.iterator()

            while (iterator.hasNext()) {
                val deferred = iterator.next()

                // Check if this prompt can now be presented
                if (!presenter.shouldDeferPrompt(deferred.request)) {
                    iterator.remove()
                    _currentPromptId = deferred.request.id
                    _activePrompts.emit(deferred.request)
                    return
                }
            }
        }
    }
}
