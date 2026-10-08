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
 */

package com.sphereon.core.api.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import kotlinx.coroutines.flow.Flow

/**
 * The streaming mode a command supports.
 */
@JsExportCompat
enum class StreamingMode {
    /** Server sends a stream of responses for a single request. */
    SERVER_STREAM,

    /** Client and server exchange streams of messages bidirectionally. */
    BIDI_STREAM,
}

/**
 * A service command that returns a stream of responses for a single input.
 *
 * Use this for commands where the server produces multiple results over time,
 * such as progress updates, event feeds, or chunked data transfers.
 *
 * @param TInput The input type for the command
 * @param TOutput The type of each streamed output item
 */
@JsExportCompat
interface ServerStreamingServiceCommand<TInput : Any, TOutput : Any, TError : IdkErrorType> : ServiceCommand<TInput, TOutput, TError> {
    /**
     * Executes the command and returns a stream of results.
     *
     * @param args The command input
     * @return A Flow of output items wrapped in IdkResult, or an error if the stream cannot be started
     */
    suspend fun executeStream(args: TInput): IdkResult<Flow<TOutput>, TError>
}

/**
 * A service command that supports bidirectional streaming.
 *
 * Use this for commands where both client and server exchange messages
 * over time, such as interactive sessions, real-time collaboration,
 * or duplex communication channels.
 *
 * @param TInput The type of each input message in the stream
 * @param TOutput The type of each output message in the stream
 */
@JsExportCompat
interface BidiStreamingServiceCommand<TInput : Any, TOutput : Any, TError : IdkErrorType> : ServiceCommand<TInput, TOutput, TError> {
    /**
     * Executes the command with a bidirectional stream.
     *
     * @param inputFlow The stream of input messages from the client
     * @return A Flow of output items wrapped in IdkResult, or an error if the stream cannot be started
     */
    suspend fun executeBidiStream(inputFlow: Flow<TInput>): IdkResult<Flow<TOutput>, TError>
}
