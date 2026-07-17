/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault.portability

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.getDigest

interface VaultChecksumSession {
    fun update(bytes: ByteArray)

    fun finishHex(): String
}

interface VaultChecksumProvider {
    val algorithm: String

    fun newSession(): VaultChecksumSession
}

class Sha256VaultChecksumProvider : VaultChecksumProvider {
    override val algorithm: String = "sha256"

    override fun newSession(): VaultChecksumSession =
        object : VaultChecksumSession {
            private val digest = getDigest(DigestAlg.SHA256)
            private var finished = false

            override fun update(bytes: ByteArray) {
                check(!finished) { "Checksum session is already finished" }
                digest.update(bytes)
            }

            override fun finishHex(): String {
                check(!finished) { "Checksum session is already finished" }
                finished = true
                return digest.digest().toHexLowercase()
            }
        }
}

internal fun ByteArray.toHexLowercase(): String =
    buildString(size * 2) {
        this@toHexLowercase.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX[value ushr 4])
            append(HEX[value and 0x0f])
        }
    }

private const val HEX = "0123456789abcdef"

suspend fun checksum(
    source: VaultByteSource,
    provider: VaultChecksumProvider,
    maxBytes: Long = Long.MAX_VALUE,
): Pair<String, Long> {
    val session = provider.newSession()
    var total = 0L
    while (true) {
        val chunk = source.read() ?: break
        require(chunk.isNotEmpty()) { "VaultByteSource returned an empty non-EOF chunk" }
        total = checkedAdd(total, chunk.size.toLong())
        require(total <= maxBytes) { "Stream exceeded the configured byte limit" }
        session.update(chunk)
    }
    return session.finishHex() to total
}
