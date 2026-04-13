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

/*
 * © 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, rest
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.kms.provider.rest

import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderConfigBinderImpl
import com.sphereon.crypto.kms.KeyManagerServiceImpl
import com.sphereon.di.Order
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RestProviderConfigTest {
    private val decodeConfig =
        """{"id":"test-rest","restProviderId":"test-rest","type":"rest","url":"http://localhost:8080/kms/v1","enabled":false,"exposePrivateKeysDuringGeneration":true,"order":10,"autoCreateCertificate":true}"""
    private val encodeConfig =
        """{"id":"test-rest","restProviderId":"test-rest","url":"http://localhost:8080/kms/v1","enabled":false,"order":10,"httpClientOptions":{"engine":"CIO"},"authConfig":{},"autoCreateCertificate":false,"exposePrivateKeysDuringGeneration":false}"""

    val app = createRestProviderTestAppComponent(application = this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("test")

    @BeforeTest
    fun setUp() {
        registerRestClientKmsSerialization()
    }

    @Test
    fun `test rest kms config to json`() = runTest {
        val config = RestClientKmsProviderConfig(
            id = "test-rest",
            order = Order.HIGHEST.orderValue,
            enabled = false,
            restKmsUrl = "http://localhost:8080/kms/v1",
        )
        val json = CryptoJsonSupport.serializer.encodeToString(config)
        assertEquals(
            encodeConfig,
            json
        )
    }

    @Test
    fun `test json to rest kms config`() = runTest {
        val config = CryptoJsonSupport.decodeKmsProviderConfig(decodeConfig)

        assertEquals("test-rest", config.id)
        assertTrue { config is RestClientKmsProviderConfig }
        config as RestClientKmsProviderConfig
        assertEquals(false, config.enabled)
        assertEquals(Order.HIGHEST.orderValue, config.order)
        assertEquals("rest", config.type)

    }

    @Test
    fun `test provider using config object`() = runTest {
        app as RestProviderTestAppComponent

        val restKmsProvider = app.restClientKmsProvider.create(
            RestClientKmsProviderConfig(
                id = "test-rest",
                restKmsUrl = "http://localhost:8080/kms/v1"
            ),
            session.asCoreApiServiceComponent().serviceExecution,
        )
        assertNotNull(restKmsProvider)
        assertEquals("test-rest", restKmsProvider.id)
    }

    @Test
    fun `test kmsProviderManager using config object`() = runTest {
        app as RestProviderTestAppComponent

        val restKmsProvider =
            app.kmsProviderManager.createFromProviderConfig(
                RestClientKmsProviderConfig(
                    id = "test-rest",
                    restKmsUrl = "http://localhost:8080/kms/v1"
                ),
                session.asCoreApiServiceComponent().serviceExecution
            )
        assertNotNull(restKmsProvider)
        assertEquals("test-rest", restKmsProvider.id)
    }

    @Test
    fun `test config binder using config object`() = runTest {
        app as RestProviderTestAppComponent

        val binder = KmsProviderConfigBinderImpl(app.appLogManager)
        // TODO: Convert this to normalized as well
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-rest.type" to "rest",
                "kms.providers.test-rest.id" to "test-rest",
                "kms.providers.test-rest.url" to "http://localhost:8080/kms/v1",
            )
        )

        val config = binder.getKmsProviderConfig(app.appConfigService, "test-rest")
        val restKmsProvider = app.kmsProviderManager.createFromProviderConfig(config, session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(restKmsProvider)
        assertEquals("test-rest", restKmsProvider.id)
    }

    @Test
    fun `test config binder getting all kms-es`() = runTest {
        app as RestProviderTestAppComponent

        val binder = KmsProviderConfigBinderImpl(app.appLogManager)
        // TODO: Convert this to normalized as well
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-rest.type" to "rest",
                "kms.providers.test-rest.id" to "test-rest",
                "kms.providers.test-rest.url" to "http://localhost:8080/kms/v1",
                "kms.providers.test-rest2.type" to "rest",
                "kms.providers.test-rest2.id" to "test-rest2",
                "kms.providers.test-rest2.url" to "http://localhost:8080/kms/v1",
            )
        )

        val configs = binder.getKmsProviderConfigs(app.appConfigService)
        assertEquals(2, configs.size)
        val restKmsProvider1 = app.kmsProviderManager.createFromProviderConfig(configs.first(), session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(restKmsProvider1)
        val restKmsProvider2 = app.kmsProviderManager.createFromProviderConfig(configs.last(), session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(restKmsProvider2)
        assertNotEquals(restKmsProvider1.id, restKmsProvider2.id)
    }

    @Test
    fun `test multiple kms-es from providerManager and properties`() = runTest {
        app as RestProviderTestAppComponent

        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-rest.type" to "rest",
                "kms.providers.test-rest.id" to "test-rest",
                "kms.providers.test-rest.url" to "http://localhost:8080/kms/v1",
                "kms.providers.test-rest2.type" to "rest",
                "kms.providers.test-rest2.id" to "test-test2",
                "kms.providers.test-rest2.url" to "http://localhost:8080/kms/v1",
            )
        )
        val providers = app.kmsProviderManager.createFromProperties(app.appConfigService, session.asCoreApiServiceComponent().serviceExecution)
        assertEquals(2, providers.size)
        val restKmsProvider1 = providers.first()
        assertNotNull(restKmsProvider1)
        val restKmsProvider2 = providers.last()
        assertNotNull(restKmsProvider2)
        assertNotEquals(restKmsProvider1.id, restKmsProvider2.id)
    }

    @Test
    fun `test multiple kms providers with an actual KMS`() = runTest {
        app as RestProviderTestAppComponent

        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.test-rest.type" to "rest",
                "kms.providers.test-rest.id" to "test-rest",
                "kms.providers.test-rest.url" to "http://localhost:8080/kms/v1",

                "kms.providers.test-rest2.type" to "rest",
                "kms.providers.test-rest2.id" to "test-rest2",
                "kms.providers.test-rest2.url" to "http://localhost:8082/kms/v2",
            )
        )

        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("test-multiple-kms").component
        val kms = session.asKeyManagerServiceComponent().keyManagerService
        assertNotNull(kms.defaultProviderId())
        assertEquals(2, kms.getProviderIds().size)
        assertNotNull(kms.getProvider("test-rest"))
//        assertNotNull(kms.generateKeyAsync())
    }

}
