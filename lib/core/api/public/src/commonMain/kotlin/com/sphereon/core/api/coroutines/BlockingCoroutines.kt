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

package com.sphereon.core.api.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Platform-specific blocking coroutine execution.
 *
 * On JVM/Native: Uses kotlinx.coroutines.runBlocking for full blocking support.
 * On JS/wasmJs: Uses [startCoroutine] to execute suspend functions that complete
 * synchronously (without actual suspension). Throws [UnsupportedOperationException]
 * if the coroutine truly suspends.
 *
 * This function should only be used for bridging synchronous APIs with suspend functions
 * where the suspend function is expected to complete without actual suspension on JS/wasmJs.
 */
internal expect fun <T> runBlockingCompat(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend () -> T,
): T
