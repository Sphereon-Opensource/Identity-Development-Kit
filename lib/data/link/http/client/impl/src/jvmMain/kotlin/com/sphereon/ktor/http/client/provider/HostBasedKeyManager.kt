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

package com.sphereon.ktor.http.client.provider

import java.net.Socket
import java.security.Principal
import javax.net.ssl.X509KeyManager
import javax.net.ssl.SSLSocket

class HostBasedKeyManager(
    private val delegate: X509KeyManager,
    private val hostNameToAlias: Map<String, String>,
    private val defaultAlias: String? = null,
) : X509KeyManager by delegate {
    override fun chooseClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String? {
        val host = ((socket as? SSLSocket)?.handshakeSession?.peerHost ?: socket?.inetAddress?.hostName)
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase()
            ?: return defaultAlias
        return hostNameToAlias.entries.firstOrNull { it.key.trim().trimEnd('.').lowercase() == host }?.value ?: defaultAlias
    }
}


