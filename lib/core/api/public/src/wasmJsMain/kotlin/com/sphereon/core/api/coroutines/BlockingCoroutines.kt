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

package com.sphereon.core.api.coroutines

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * wasmJs implementation - executes a suspend function that completes synchronously.
 *
 * Uses [startCoroutine] to run the block. If the coroutine completes without
 * actually suspending (i.e., all operations are synchronous), the result is
 * returned immediately. If the coroutine truly suspends, an error is thrown.
 *
 * This enables property interpolation on wasmJs where the interpolate() function
 * is marked suspend but completes synchronously for non-secret references.
 */
internal actual fun <T> runBlockingCompat(
    context: CoroutineContext,
    block: suspend () -> T
): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(context) { outcome ->
        result = outcome
    })
    return result?.getOrThrow()
        ?: throw UnsupportedOperationException(
            "runBlockingCompat: coroutine did not complete synchronously. " +
                "Use suspend functions directly for operations that require actual suspension."
        )
}
