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

import com.sphereon.core.compat.JsExportCompat
import io.ktor.utils.io.ByteChannel
import kotlin.jvm.JvmOverloads

// TODO do we still need this if we have the config objects?

/**
 * Options for loading a [KeyStore], including type, data source, and password.
 *
 * @property type the value indicating the keystore type to load
 * @property source source of raw keystore data (file, bytes, or channel)
 * @property keyStorePassword password used to unlock the keystore
 */
@JsExportCompat
data class
KeyStoreLoaderOpts
    @JvmOverloads
    constructor(
        val type: String,
        val source: Source,
        val keyStorePassword: String? = null,
    ) {
        /**
         * Represents the origin of the keystore data.
         */
        sealed class Source {
            /**
             * Load keystore data from a file path.
             * @param path the filesystem path to the keystore file
             */
            data class File(
                val path: String,
                val autoCreate: Boolean = true,
            ) : Source()

            /**
             * Load keystore data from an in-memory byte array.
             * @param data the raw keystore bytes
             */
            data class Bytes(
                var data: ByteArray,
            ) : Source() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) {
                        return true
                    }
                    if (other == null || this::class != other::class) {
                        return false
                    }

                    other as Bytes

                    if (!data.contentEquals(other.data)) {
                        return false
                    }

                    return true
                }

                override fun hashCode(): Int = data.contentHashCode()
            }

            /**
             * Load keystore data from a ByteReadChannel.
             * @param channel the [ByteChannel] supplying the keystore bytes
             */
            data class Channel(
                val channel: ByteChannel,
            ) : Source()
        }
    }
