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

package com.sphereon.ui.prompt.event

import com.sphereon.core.compat.JsExportCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Bridge for emitting and subscribing to prompt lifecycle events.
 *
 * This can be integrated with the core EventHub for unified event handling.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PromptEventBridge", exact = true)
interface PromptEventBridge {
    /**
     * Flow of all prompt events.
     */
    val events: SharedFlow<PromptEvent>

    /**
     * Emits a prompt event.
     */
    suspend fun emit(event: PromptEvent)
}

/**
 * Default implementation of PromptEventBridge.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPromptEventBridge", exact = true)
class DefaultPromptEventBridge : PromptEventBridge {
    private val _events =
        MutableSharedFlow<PromptEvent>(
            replay = 0,
            extraBufferCapacity = 64,
        )

    override val events: SharedFlow<PromptEvent> = _events.asSharedFlow()

    override suspend fun emit(event: PromptEvent) {
        _events.emit(event)
    }

    companion object {
        /**
         * Shared instance for simple use cases.
         */
        val INSTANCE = DefaultPromptEventBridge()
    }
}
