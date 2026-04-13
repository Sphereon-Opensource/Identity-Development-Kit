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

package com.sphereon.ktor.http.client.server

import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes

data class ServerKeyStoreOpts(
    val path: String,
    val keyStorePassword: String,
    val type: PredefinedKeyStoreTypes,
    val keyAlias: String,
    val privateKeyPassword: String,
)

data class ServerTrustStoreOpts(
    val path: String,
    val trustStorePassword: String,
    val type: PredefinedKeyStoreTypes
)

data class ServerOpts(
    val port: Int = 0,
    val keyStoreOpts: ServerKeyStoreOpts? = null,
    val trustStoreOpts: ServerTrustStoreOpts? = null
)

expect interface TestServer {
    fun actualPort(): Int
    fun stop()
}

expect fun startServer(opts: ServerOpts): TestServer
