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

package com.sphereon.core.api.binary

/**
 * Creates a [StreamingBody] from a platform-specific byte channel.
 *
 * **JVM:** Accepts Ktor's `ByteReadChannel` and converts it to a [StreamingBody.ByteStream]
 * **Other platforms:** Throws [UnsupportedOperationException]
 *
 * @param channel The platform-specific channel (ByteReadChannel on JVM)
 * @param contentLength Optional known content length
 * @return A StreamingBody wrapping the channel
 * @throws UnsupportedOperationException on platforms without channel support
 */
expect fun StreamingBody.Companion.ofChannel(channel: Any, contentLength: Long? = null): StreamingBody

/**
 * Checks if the current platform supports channel-based streaming.
 *
 * @return true if [ofChannel] is supported, false otherwise
 */
expect fun StreamingBody.Companion.supportsChannels(): Boolean
