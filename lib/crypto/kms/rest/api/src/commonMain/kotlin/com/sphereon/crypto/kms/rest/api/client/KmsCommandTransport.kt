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
 */

package com.sphereon.crypto.kms.rest.api.client

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionContext

/**
 * Transport abstraction for IDK's KMS REST client.
 *
 * Provides the contract for sending command invocations to a KMS REST API
 * over HTTP. This is IDK's standalone REST client — external developers
 * can use it directly to bridge pre-existing KMS software.
 *
 * @see HttpServiceCommandTransport for the HTTP implementation
 */
interface KmsCommandTransport {
    /**
     * Invokes a command with an explicit session context.
     */
    suspend fun <T : Any> invoke(
        commandId: String,
        input: Any,
        sessionContext: SessionContext,
        outputTypeToken: TypeToken<T>
    ): IdkResult<T, IdkError>

    /**
     * Creates a session-bound transport that reuses the given session context
     * for all subsequent invocations.
     */
    fun bindSession(runtimeSessionContext: SessionContext): SessionBoundKmsCommandTransport
}

/**
 * Session-bound variant of [KmsCommandTransport].
 *
 * Created via [KmsCommandTransport.bindSession], carries session context
 * internally so callers don't need to pass it on every invocation.
 */
interface SessionBoundKmsCommandTransport {
    suspend fun <T : Any> invoke(
        commandId: String,
        input: Any,
        outputTypeToken: TypeToken<T>
    ): IdkResult<T, IdkError>
}
