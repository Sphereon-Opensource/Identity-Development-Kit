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

package com.sphereon.core.defaults

import kotlinx.coroutines.runBlocking
import com.sphereon.core.api.TestAppComponent
import com.sphereon.core.api.createTestAppComponent
import com.sphereon.core.api.context.asCoreApiContextComponent
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlin.test.Test

class JvmTest {

    @Test
    fun `context and logging test`() {
        runBlocking {
            val appComponent: TestAppComponent = createTestAppComponent(this, "appId", "profile", "version")
            println(appComponent)
            println(appComponent.userContextManager)

           /* val kmsTest1 = appComponent.kmsCreator.create("testing", order = Order.LOW, kmsType = PredefinedKmsProviderTypes.SOFTWARE)

            println(kmsTest1)

            val kmsTest2 = appComponent.kmsCreator.create("testing", order = Order.LOW, kmsType = PredefinedKmsProviderTypes.SOFTWARE)
            println(kmsTest2)

            val kmsFactory = appComponent.kmsCreator.create("test2", order = Order.HIGH, kmsType = PredefinedKmsProviderTypes.MOBILE)
            println(kmsFactory)

            assertNotEquals(kmsTest1, kmsTest2)*/

           /* val kmsTest3 = appComponent.keyService.mobileKmsFactories.last().create("testing from inject", order = Order.HIGH)
            println(kmsTest3)*/

            appComponent.appLogger().info("test message at app level")

            println("appId: ${appComponent.appId}")
            println("version: ${appComponent.version}")

            val keyvaultUrl = appComponent.appConfigService.getPropertyAsString("azure.keyvault.url")
            println("azure.keyvault.url: $keyvaultUrl")
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test@principal.com"),
                DefaultPrincipalInputString("test@principal.com")
            )


            val sessionContextComponent = contextInstance.sessionContextManager.createOrGetFromId("testAction")
            val sessionContext1 = sessionContextComponent.sessionContext
            println(contextInstance)
            println(appComponent)


//            val log1 = contextComp.logger
//            val log2 = test.logger


            val contextComp1Extended = contextInstance.asCoreApiContextComponent()
            val log = sessionContextComponent.asCoreApiServiceComponent().logger()
            log.info("test message")

            val contextInstance2 =
                appComponent.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test2@principal2.com"),
                    DefaultPrincipalInputString("test2@principal2.com")
                )
            val contextComp2Extended = contextInstance2.asCoreApiContextComponent()
            val sessionContext2Comp =
                contextComp2Extended.createExecutionContextComponent(appComponent.userContextManager.getActive().context, "testAction")
//            contextComp2.loggerWithTag("test").info("test message2", sessionContext2Comp.sessionContext)

            log.info("test message3")

           /* sessionContext2Comp.asCoreApiServiceComponent().helloWorld.execute(
                LogMessage(level = LogLevel.WARN, message = "OLA"),
                sessionContext2Comp.sessionContext
            )
*/
        }

    }

    /*@Test
    fun `auto registered action should be executed by id`() {
        runBlocking {
            val appComponent: TestAppComponent = createTestAppComponent(this, "appId", "profile", "version")
            val start = Clock.System.now()
            val out = appComponent.serviceExecutor.execute<String, String>(
                tenantInput = DefaultTenantInputString("test@principal.com"),
                principalInput = DefaultPrincipalInputString("test@principal.com"),
                serviceId = HelloWorldServiceImpl.SERVICE_ID,
                args = "Hello world"
            )
            assertTrue(out.isOk)
            println(Clock.System.now().minus(start).toString(DurationUnit.MILLISECONDS))

        }
    }

    @Test
    fun `component declared action should be executed by injection`() {
        runBlocking {
            val appComponent: TestAppComponent = createTestAppComponent(this, "appId", "profile", "version")
            val start = Clock.System.now()
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test@principal.com"),
                DefaultPrincipalInputString("test@principal.com")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "HelloWorld")
            val out = sessionComponent.asCoreApiServiceComponent().helloWorld.execute(args = "hello world")
            assertTrue(out.isOk)
            println(Clock.System.now().minus(start).toString(DurationUnit.MILLISECONDS))

        }
    }*/
}
