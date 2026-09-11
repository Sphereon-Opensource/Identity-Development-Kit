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

package com.sphereon.ktor.http.client

import com.sphereon.ktor.http.client.config.CaOpts
import com.sphereon.ktor.http.client.config.ClientSslConfig
import com.sphereon.ktor.http.client.config.KeystoreCertificateOpts
import com.sphereon.ktor.http.client.config.ResolvedCaCertificateOpts
import com.sphereon.ktor.http.client.config.ServerSslConfig
import com.sphereon.ktor.http.client.config.SslConfig
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpMinimumTlsVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SslConfigTest {
    @Test
    fun defaultSslConfigHasDefaultClientAndServer() {
        val config = SslConfig()
        assertEquals(ClientSslConfig(), config.client)
        assertEquals(ServerSslConfig(), config.server)
    }

    @Test
    fun sslConfigCanBeCustomized() {
        val clientConfig = ClientSslConfig(engine = HttpClientEngineType.OKHTTP)
        val serverConfig = ServerSslConfig(ca = CaOpts(includePlatformDefaults = false))
        val config = SslConfig(client = clientConfig, server = serverConfig)

        assertEquals(HttpClientEngineType.OKHTTP, config.client.engine)
        assertFalse(config.server.ca.includePlatformDefaults)
    }
}

class ClientSslConfigTest {
    @Test
    fun defaultEngineIsCio() {
        val config = ClientSslConfig()
        assertEquals(HttpClientEngineType.CIO, config.engine)
    }

    @Test
    fun defaultPerHostCertificateIsEmpty() {
        val config = ClientSslConfig()
        assertTrue(config.perHostCertificate.isEmpty())
    }

    @Test
    fun defaultCertificateIsNull() {
        val config = ClientSslConfig()
        assertNull(config.defaultCertificate)
    }

    @Test
    fun hostNameToAliasReturnsMapping() {
        val cert1 = KeystoreCertificateOpts("alias1", "store1")
        val cert2 = KeystoreCertificateOpts("alias2", "store2")
        val config =
            ClientSslConfig(
                perHostCertificate = mapOf("host1" to cert1, "host2" to cert2),
            )

        val mapping = config.hostNameToAlias()

        assertEquals("alias1", mapping["host1"])
        assertEquals("alias2", mapping["host2"])
    }

    @Test
    fun defaultAliasReturnsNullWhenNoDefaultCert() {
        val config = ClientSslConfig()
        assertNull(config.defaultAlias())
    }

    @Test
    fun defaultAliasReturnsAliasWhenDefaultCertExists() {
        val cert = KeystoreCertificateOpts("default-alias", "default-store")
        val config = ClientSslConfig(defaultCertificate = cert)

        assertEquals("default-alias", config.defaultAlias())
    }

    @Test
    fun allKeyStoreIdsReturnsAllStoreIds() {
        val cert1 = KeystoreCertificateOpts("alias1", "store1")
        val cert2 = KeystoreCertificateOpts("alias2", "store2")
        val defaultCert = KeystoreCertificateOpts("default-alias", "default-store")
        val config =
            ClientSslConfig(
                perHostCertificate = mapOf("host1" to cert1, "host2" to cert2),
                defaultCertificate = defaultCert,
            )

        val storeIds = config.allKeyStoreIds()

        assertTrue(storeIds.contains("store1"))
        assertTrue(storeIds.contains("store2"))
        assertTrue(storeIds.contains("default-store"))
        assertEquals(3, storeIds.size)
    }

    @Test
    fun allKeyStoreIdsHandlesNullDefaultCertificate() {
        val cert = KeystoreCertificateOpts("alias", "store")
        val config = ClientSslConfig(perHostCertificate = mapOf("host" to cert))

        val storeIds = config.allKeyStoreIds()

        assertEquals(setOf("store"), storeIds)
    }

    @Test
    fun isMtlsReturnsFalseWhenNoDefaultAndNoHostMatch() {
        val config = ClientSslConfig()
        assertFalse(config.isMtls())
        assertFalse(config.isMtls("somehost"))
    }

    @Test
    fun isMtlsReturnsTrueWhenDefaultCertExists() {
        val cert = KeystoreCertificateOpts("alias", "store")
        val config = ClientSslConfig(defaultCertificate = cert)

        assertTrue(config.isMtls())
        assertTrue(config.isMtls("anyhost"))
    }

    @Test
    fun isMtlsReturnsTrueWhenHostMatches() {
        val cert = KeystoreCertificateOpts("alias", "store")
        val config = ClientSslConfig(perHostCertificate = mapOf("specific-host" to cert))

        assertFalse(config.isMtls("other-host"))
        assertTrue(config.isMtls("specific-host"))
    }

    @Test
    fun allCertificatesIncludesAllCertsWithDefaultOnEmptyKey() {
        val hostCert = KeystoreCertificateOpts("host-alias", "host-store")
        val defaultCert = KeystoreCertificateOpts("default-alias", "default-store")
        val config =
            ClientSslConfig(
                perHostCertificate = mapOf("host1" to hostCert),
                defaultCertificate = defaultCert,
            )

        val allCerts = config.allCertificates()

        assertEquals(hostCert, allCerts["host1"])
        assertEquals(defaultCert, allCerts[""])
    }
}

class ServerSslConfigTest {
    @Test
    fun defaultCaHasDefaultOpts() {
        val config = ServerSslConfig()
        assertEquals(CaOpts(), config.ca)
    }

    @Test
    fun caOptsCanBeCustomized() {
        val caOpts = CaOpts(includePlatformDefaults = false)
        val config = ServerSslConfig(ca = caOpts)

        assertFalse(config.ca.includePlatformDefaults)
    }
}

class CaOptsTest {
    @Test
    fun defaultIncludePlatformDefaultsIsTrue() {
        val opts = CaOpts()
        assertTrue(opts.includePlatformDefaults)
    }

    @Test
    fun defaultAdditionalCAsIsEmpty() {
        val opts = CaOpts()
        assertTrue(opts.additionalCAs.isEmpty())
    }

    @Test
    fun additionalCAsCanBeSet() {
        val cert = KeystoreCertificateOpts("alias", "store")
        val opts = CaOpts(additionalCAs = setOf(cert))

        assertEquals(1, opts.additionalCAs.size)
        assertTrue(opts.additionalCAs.contains(cert))
    }

    @Test
    fun executionScopedCertificatePemsCanBeSetWithoutKeystoreReferences() {
        val pem = "-----BEGIN CERTIFICATE-----\nPUBLIC\n-----END CERTIFICATE-----"
        val certificate = ResolvedCaCertificateOpts(
            certificateAlias = "governed-ca",
            certificatePem = pem,
            certificateFingerprint = "test-ca-fingerprint",
        )
        val opts = CaOpts(includePlatformDefaults = false, resolvedCertificates = setOf(certificate))

        assertEquals(setOf(certificate), opts.resolvedCertificates)
        assertEquals(pem, opts.resolvedCertificates.single().certificatePem)
        assertFalse(opts.includePlatformDefaults)
        assertTrue(opts.hasCustomTrust())
        assertTrue(opts.additionalCAs.isEmpty())
    }

    @Test
    fun clientTlsDefaultsToTls12AndCanRaiseTheFloorToTls13() {
        assertEquals(HttpMinimumTlsVersion.TLS_1_2, ClientSslConfig().minimumTlsVersion)
        assertEquals(
            HttpMinimumTlsVersion.TLS_1_3,
            ClientSslConfig(minimumTlsVersion = HttpMinimumTlsVersion.TLS_1_3).minimumTlsVersion,
        )
    }
}

class KeystoreCertificateOptsTest {
    @Test
    fun hasRequiredFields() {
        val opts =
            KeystoreCertificateOpts(
                certificateAlias = "my-alias",
                keyStoreId = "my-store",
            )

        assertEquals("my-alias", opts.certificateAlias)
        assertEquals("my-store", opts.keyStoreId)
    }

    @Test
    fun dataClassEquality() {
        val opts1 = KeystoreCertificateOpts("alias", "store")
        val opts2 = KeystoreCertificateOpts("alias", "store")
        val opts3 = KeystoreCertificateOpts("different", "store")

        assertEquals(opts1, opts2)
        assertFalse(opts1 == opts3)
    }
}

class HttpClientEngineTypeTest {
    @Test
    fun cioEngineExists() {
        assertEquals("CIO", HttpClientEngineType.CIO.name)
    }

    @Test
    fun okhttpEngineExists() {
        assertEquals("OKHTTP", HttpClientEngineType.OKHTTP.name)
    }

    @Test
    fun darwinEngineExists() {
        assertEquals("DARWIN", HttpClientEngineType.DARWIN.name)
    }

    @Test
    fun hasFourEngineTypes() {
        assertEquals(4, HttpClientEngineType.entries.size)
    }
}
