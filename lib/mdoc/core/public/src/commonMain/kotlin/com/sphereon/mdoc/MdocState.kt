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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("representing", exact = true)
 * Super interface representing states in the mdoc interaction lifecycle.
 *
 * This interface acts as a common base for both engagement and retrieval states,
 * which represent different phases of the mdoc interaction process. States typically
 * progress in a predictable order, indicated by the `order` property.
 *
 * Implementations should ensure that states do not regress (i.e., move from a higher
 * order value to a lower order value) during the lifecycle.
 */
interface MdocState {
    /**
     * Represents the current state as a string.
     * This property stores the name of the current state,
     * which can be used to track or identify the specific phase or status.
     */
    val state: String

    /**
     * Represents the order of the state in the session lifecycle.
     * Used to determine the relative progression of states within
     * the mdoc interaction process. States with higher order values
     * represent later phases in the lifecycle.
     */
    val order: Int
}
