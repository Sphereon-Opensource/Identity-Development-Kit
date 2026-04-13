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

package com.sphereon.crypto.kms.provider.mobile

import kotlinx.coroutines.test.runTest
import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.json.CryptoJsonSupport
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderConfigBinderImpl
import com.sphereon.di.Order
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MobileKmsProviderConfigTest {

    private val decodeConfig = """{"id":"test-mobile","enabled":false,"exposePrivateKeysDuringGeneration":false,"order":10,"type":"mobile"}"""
    private val encodeConfig = """{"id":"test-mobile","enabled":false,"order":10,"exposePrivateKeysDuringGeneration":false}"""

    val app = createMobileProviderTestAppComponent(application = this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("test")

    @BeforeTest
    fun setUp() {
//        registerMobileKmsSerialization()
    }


    @Test
    fun `test provider using config object`() = runTest {
        app as MobileProviderTestAppComponent
        val mobileKmsProvider = app.mobileKmsProvider.create(MobileKmsProviderConfig(id = "test-mobile"), session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(mobileKmsProvider)
        assertEquals("test-mobile", mobileKmsProvider.id)
    }

    @Test
    fun `test kmsProviderManager using config object`() = runTest {
        app as MobileProviderTestAppComponent
        val softwareKmsProvider =
            app.kmsProviderManager.createFromProviderConfig(MobileKmsProviderConfig(id = "test-mobile"), session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(softwareKmsProvider)
        assertEquals("test-mobile", softwareKmsProvider.id)
    }

    @Test
    fun `test config binder using config object`() = runTest {
        app as MobileProviderTestAppComponent
        val binder = KmsProviderConfigBinderImpl(app.appLogManager)
        // TODO: Convert this to normalized as well
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "software.kms.app.profile.kms.providers.test-mobile.type" to "mobile",
                "software.kms.app.profile.kms.providers.test-mobile.id" to "test-mobile"
            )
        )

        val config = binder.getKmsProviderConfig(app.appConfigService, "test-mobile")
        val softwareKmsProvider = app.kmsProviderManager.createFromProviderConfig(config, session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(softwareKmsProvider)
        assertEquals("test-mobile", softwareKmsProvider.id)
    }

    @Test
    fun `test config binder getting all kms-es`() = runTest {
        app as MobileProviderTestAppComponent
        val binder = KmsProviderConfigBinderImpl(app.appLogManager)
        // TODO: Convert this to normalized as well
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "software.kms.app.profile.kms.providers.test-mobile.type" to "mobile",
                "software.kms.app.profile.kms.providers.test-mobile.id" to "test-mobile",
                "software.kms.app.profile.kms.providers.test-mobile2.type" to "mobile",
                "software.kms.app.profile.kms.providers.test-mobile2.id" to "test2"
            )
        )

        val configs = binder.getKmsProviderConfigs(app.appConfigService)
        assertEquals(2, configs.size)
        val softwareKmsProvider1 = app.kmsProviderManager.createFromProviderConfig(configs.first(), session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(softwareKmsProvider1)
        val softwareKmsProvider2 = app.kmsProviderManager.createFromProviderConfig(configs.last(), session.asCoreApiServiceComponent().serviceExecution)
        assertNotNull(softwareKmsProvider2)
        assertNotEquals(softwareKmsProvider1.id, softwareKmsProvider2.id)
    }


    @Test
    fun `test multiple kms-es from providerManager and properties`() = runTest {
        app as MobileProviderTestAppComponent

        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "software.kms.app.profile.kms.providers.test-mobile.type" to "mobile",
                "software.kms.app.profile.kms.providers.test-mobile.id" to "test-mobile",
                "software.kms.app.profile.kms.providers.test-mobile2.type" to "mobile",
                "software.kms.app.profile.kms.providers.test-mobile2.id" to "test2"
            )
        )
        val providers = app.kmsProviderManager.createFromProperties(app.appConfigService, session.asCoreApiServiceComponent().serviceExecution)
        assertEquals(2, providers.size)
        val softwareKmsProvider1 = providers.first()
        assertNotNull(softwareKmsProvider1)
        val softwareKmsProvider2 = providers.last()
        assertNotNull(softwareKmsProvider2)
        assertNotEquals(softwareKmsProvider1.id, softwareKmsProvider2.id)
    }

    @Test
    fun `test software kms config to json`() = runTest {
        val config = MobileKmsProviderConfig(id = "test-mobile", order = Order.HIGHEST.orderValue, enabled = false)
        val json = CryptoJsonSupport.serializer.encodeToString(config)
        assertEquals(
            encodeConfig,
            json
        )
    }

    @Test
    fun `test json to software kms config`() = runTest {
        val config = CryptoJsonSupport.decodeKmsProviderConfig(decodeConfig)
        assertEquals("test-mobile", config.id)
        assertTrue { config is MobileKmsProviderConfig }
        config as MobileKmsProviderConfig
        assertEquals(false, config.enabled)
        assertEquals(Order.HIGHEST.orderValue, config.order)
        assertEquals("mobile", config.type)


    }

}
