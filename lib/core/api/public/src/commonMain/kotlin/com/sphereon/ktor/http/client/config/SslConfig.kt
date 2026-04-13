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

package com.sphereon.ktor.http.client.config

import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SslConfig", exact = true)
data class SslConfig(
    val client: ClientSslConfig = ClientSslConfig(),
    val server: ServerSslConfig = ServerSslConfig(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ClientSslConfig", exact = true)
data class ClientSslConfig(
    val engine: HttpClientEngineType = HttpClientEngineType.CIO,
    val perHostCertificate: Map<String, KeystoreCertificateOpts> = mapOf(),
    val defaultCertificate: KeystoreCertificateOpts? = null,
) {
    fun hostNameToAlias() = perHostCertificate.map { it.key to it.value.certificateAlias }.toMap()

    fun defaultAlias() = defaultCertificate?.certificateAlias

    fun allKeyStoreIds() = (perHostCertificate.values.map { it.keyStoreId } + defaultCertificate?.keyStoreId).filterNotNull().toSet()

    fun isMtls(host: String? = null) = defaultCertificate != null || perHostCertificate.containsKey(host ?: "")

    fun allCertificates() = (perHostCertificate + ("" to defaultCertificate))
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerSslConfig", exact = true)
data class ServerSslConfig(
    val ca: CaOpts = CaOpts(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CaOpts", exact = true)
data class CaOpts(
    val includePlatformDefaults: Boolean = true,
    val additionalCAs: Set<KeystoreCertificateOpts> = setOf(),
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("KeystoreCertificateOpts", exact = true)
data class KeystoreCertificateOpts(
    val certificateAlias: String,
    val keyStoreId: String,
)
