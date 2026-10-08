/*
 * Â© 2026 Sphereon International B.V.
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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpMinimumTlsVersion
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SslConfig", exact = true)
data class SslConfig(
    val client: ClientSslConfig = ClientSslConfig(),
    val server: ServerSslConfig = ServerSslConfig(),
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ClientSslConfig", exact = true)
data class ClientSslConfig(
    val engine: HttpClientEngineType = HttpClientEngineType.CIO,
    val minimumTlsVersion: HttpMinimumTlsVersion = HttpMinimumTlsVersion.TLS_1_2,
    val perHostCertificate: Map<String, KeystoreCertificateOpts> = mapOf(),
    val defaultCertificate: KeystoreCertificateOpts? = null,
) {
    fun hostNameToAlias() = perHostCertificate.map { it.key to it.value.certificateAlias }.toMap()

    fun defaultAlias() = defaultCertificate?.certificateAlias

    fun allKeyStoreIds() = (perHostCertificate.values.map { it.keyStoreId } + defaultCertificate?.keyStoreId).filterNotNull().toSet()

    fun isMtls(host: String? = null) = defaultCertificate != null || perHostCertificate.containsKey(host ?: "")

    fun allCertificates() = (perHostCertificate + ("" to defaultCertificate))
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerSslConfig", exact = true)
data class ServerSslConfig(
    val ca: CaOpts = CaOpts(),
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CaOpts", exact = true)
data class CaOpts(
    val includePlatformDefaults: Boolean = true,
    val additionalCAs: Set<KeystoreCertificateOpts> = setOf(),
    /** Public, already governed CA certificates. This never contains client identities or private keys. */
    val resolvedCertificates: Set<ResolvedCaCertificateOpts> = setOf(),
) {
    fun hasCustomTrust(): Boolean = additionalCAs.isNotEmpty() || resolvedCertificates.isNotEmpty()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedCaCertificateOpts", exact = true)
data class ResolvedCaCertificateOpts(
    val certificateAlias: String,
    val certificatePem: String,
    val certificateFingerprint: String,
) {
    init {
        require(certificateAlias.isNotBlank()) { "Resolved CA certificate alias must not be blank" }
        require(certificatePem.isNotBlank()) { "Resolved CA certificate PEM must not be blank" }
        require(certificateFingerprint.isNotBlank()) { "Resolved CA certificate fingerprint must not be blank" }
    }

    override fun toString(): String =
        "ResolvedCaCertificateOpts(certificateAlias=$certificateAlias, certificatePem=<public-certificate-redacted>, " +
            "certificateFingerprint=$certificateFingerprint)"
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeystoreCertificateOpts", exact = true)
data class KeystoreCertificateOpts(
    val certificateAlias: String,
    val keyStoreId: String,
)


