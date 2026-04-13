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

package com.sphereon.mdoc

import com.sphereon.core.compat.LocalDateTimeKMP
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val FINAL_STATE_ORDER_THRESHOLD = 200

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("representing", exact = true)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEvent", exact = true)
 * Super interface representing events in the mdoc interaction lifecycle.
 *
 * This interface acts as a common base for both [EngagementEvent] and [MdocRetrievalEvent],
 * which represent different phases of the mdoc interaction process. Events typically occur
 * in a predictable order:
 *
 * 1. Engagement Phase: Establishing connection between holder and reader
 * 2. Retrieval Phase: Exchanging device requests and responses
 *
 * All events share common properties such as timestamp and state information.
 */
interface MdocEvent {
    /**
     * A timestamp representing the date and time when the event occurred.
     */
    val time: LocalDateTimeKMP

    /**
     * Represents the current state of the mdoc interaction process.
     * The state indicates the progression of the event, from initialization
     * through connection, data exchange, and termination phases.
     */
    val state: MdocState

    val isFinal: Boolean get() = state.order >= FINAL_STATE_ORDER_THRESHOLD
}
