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

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.conf.DefaultSyncConfigSnapshotCache
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A file-backed keystore cannot be opened without its password, so a `pkcs12` provider entry that
 * carries no password is not a provider anyone can use. The binder refuses that entry, and the
 * refusal is scoped to it: enumeration keeps the providers that do bind, and resolving the refused
 * provider by id still throws. Failing the whole map instead would turn one malformed entry into a
 * 500 on every request that resolves any provider, including requests that never name it.
 *
 * This is what makes generic provider configuration unusable for a customer tenant. A deployment's
 * own providers receive their password as deployment configuration; a tenant's keystore password is
 * a system credential redeemed over the internal secret channel and never appears in configuration,
 * so a tenant-scoped `kms.providers` entry can never complete.
 */
class KmsProviderKeystoreBindingTest {
    private val app = createJvmCryptoTestAppGraph(this)

    private fun clearConfigCache() {
        (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache.clear()
    }

    private fun rows(id: String, password: String?): Map<String, String> =
        buildMap {
            put("kms.providers.$id.type", "software")
            put("kms.providers.$id.id", id)
            put("kms.providers.$id.enabled", "true")
            put("kms.providers.$id.autoCreateCertificate", "true")
            put("kms.providers.$id.keystore.type", "pkcs12")
            put("kms.providers.$id.keystore.id", id)
            put("kms.providers.$id.keystore.path", "/keystore/$id.p12")
            put("kms.providers.$id.keystore.key-visibility", "private")
            put("kms.providers.$id.keystore.access-mode", "read_write")
            password?.let { put("kms.providers.$id.keystore.password", it) }
        }

    private fun <T> withRows(rows: Map<String, String>, block: () -> T): T {
        clearConfigCache()
        rows.forEach { (key, value) -> DefaultAppMapPropertySource.addProperty(key, value) }
        try {
            return block()
        } finally {
            rows.keys.forEach { DefaultAppMapPropertySource.deleteProperty(it) }
            clearConfigCache()
        }
    }

    @Test
    fun aPkcs12ProviderWithItsPasswordBinds() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        withRows(rows(id = "deployment-owned", password = "keystore-password")) {
            val configs = binder.getKmsProviderConfigs(configService)
            assertTrue(
                configs.any { it.id == "deployment-owned" },
                "a complete pkcs12 provider must bind; got ${configs.map { it.id }}",
            )
        }
    }

    @Test
    fun aPkcs12ProviderWithoutItsPasswordIsSkippedWithoutTakingTheSiblingsWithIt() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        val complete = rows(id = "deployment-owned", password = "keystore-password")
        val incomplete = rows(id = "tenant-owned", password = null)

        withRows(complete + incomplete) {
            val configs = binder.getKmsProviderConfigs(configService)
            assertTrue(
                configs.any { it.id == "deployment-owned" },
                "the complete provider must survive an unbindable sibling; got ${configs.map { it.id }}",
            )
            assertTrue(
                configs.none { it.id == "tenant-owned" },
                "an unbindable provider must not be returned half-bound; got ${configs.map { it.id }}",
            )
            assertTrue(
                binder.getKmsProviderIds(configService).contains("deployment-owned"),
                "enumeration must keep listing the providers that bind",
            )
        }
    }

    @Test
    fun resolvingTheUnbindableProviderByIdStillFails() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        val complete = rows(id = "deployment-owned", password = "keystore-password")
        val incomplete = rows(id = "tenant-owned", password = null)

        withRows(complete + incomplete) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    binder.getKmsProviderConfig(configService, "tenant-owned")
                }
            // A single-entry lookup reports the id as configuration spells it, so the refusal an
            // operator sees for a named provider carries the hyphen rather than the normalized
            // delimiter the whole-map diagnostics use.
            assertTrue(
                failure.message.orEmpty().contains("kms.providers.tenant-owned"),
                "the refusal must name the entry that could not bind: ${failure.message}",
            )
        }
    }
}
