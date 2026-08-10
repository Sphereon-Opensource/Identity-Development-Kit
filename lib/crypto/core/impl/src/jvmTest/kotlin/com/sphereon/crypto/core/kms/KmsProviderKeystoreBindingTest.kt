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
 * carries no password is not a provider anyone can use. The binder refuses it, and because the
 * entries under `kms.providers` are bound as one strict map, the refusal takes the whole map with
 * it rather than dropping the single entry: a caller that silently saw one provider fewer would
 * reach for a different key instead of failing.
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
    fun aPkcs12ProviderWithoutItsPasswordFailsTheWholeMap() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        val complete = rows(id = "deployment-owned", password = "keystore-password")
        val incomplete = rows(id = "tenant-owned", password = null)

        withRows(complete + incomplete) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    binder.getKmsProviderConfigs(configService)
                }
            // The diagnostic names the entry in its normalized form: the key normalizer replaces a
            // hyphen with the key delimiter, so an operator matching the refusal against the stored
            // key has to expect `tenant.owned` where configuration says `tenant-owned`.
            assertTrue(
                failure.message.orEmpty().contains("kms.providers.tenant.owned"),
                "the refusal must name the entry that could not bind: ${failure.message}",
            )
        }
    }
}
