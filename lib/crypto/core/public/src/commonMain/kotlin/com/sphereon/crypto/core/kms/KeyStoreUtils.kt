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

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.openFileChannel
import io.ktor.utils.io.ByteReadChannel

object KeyStoreUtils {
    /**
     * Opens a [ByteReadChannel] based on the provided [KeyStoreLoaderOpts.Source].
     *
     * This function supports different types of sources for keystore data:
     * - If the source is a file, it opens a channel to read from the file at the specified path.
     * - If the source is a byte array, it creates a channel from the byte data.
     * - If the source is already a channel, it returns the existing channel.
     *
     * @param source The keystore data source, which can be a file, byte array, or existing channel.
     * @return A [ByteReadChannel] corresponding to the provided source.
     */
    suspend fun openChannel(source: KeyStoreLoaderOpts.Source): ByteReadChannel =
        when (source) {
            is KeyStoreLoaderOpts.Source.File -> openFileChannel(source.path)
            is KeyStoreLoaderOpts.Source.Bytes -> ByteReadChannel(source.data)
            is KeyStoreLoaderOpts.Source.Channel -> source.channel
        }
}
