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

package com.sphereon.crypto.jose.jwe.command

import com.sphereon.compression.CompressionAlgorithm
import com.sphereon.compression.compress
import com.sphereon.compression.decompress

// JWE zip=DEF is raw DEFLATE (RFC 1951); the shared compression library implements it on native through zlib.
internal actual suspend fun deflate(plaintext: ByteArray): ByteArray = compress(plaintext, CompressionAlgorithm.DEFLATE_RAW)

internal actual suspend fun inflate(compressed: ByteArray): ByteArray = decompress(compressed, CompressionAlgorithm.DEFLATE_RAW)
