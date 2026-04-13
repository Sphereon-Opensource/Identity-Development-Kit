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
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager

data class CompositeKeyManager(
    private val delegates: Set<X509KeyManager>,
) : X509KeyManager {
    override fun getClientAliases(
        keyType: String?,
        issuers: Array<Principal>?,
    ): Array<String> = delegates.flatMap { it.getClientAliases(keyType, issuers)?.asIterable() ?: emptyList() }.toTypedArray()

    override fun chooseClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String? = delegates.firstNotNullOfOrNull { it.chooseClientAlias(keyType, issuers, socket) }

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<Principal>?,
    ): Array<String> = delegates.flatMap { it.getServerAliases(keyType, issuers)?.asIterable() ?: emptyList() }.toTypedArray()

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<Principal>?,
        socket: Socket?,
    ): String? = delegates.firstNotNullOfOrNull { it.chooseServerAlias(keyType, issuers, socket) }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = delegates.firstNotNullOfOrNull { it.getCertificateChain(alias) }

    override fun getPrivateKey(alias: String?): PrivateKey? = delegates.firstNotNullOfOrNull { it.getPrivateKey(alias) }
}
