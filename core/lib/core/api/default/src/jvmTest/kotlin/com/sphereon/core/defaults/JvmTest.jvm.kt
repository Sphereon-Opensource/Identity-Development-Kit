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

package com.sphereon.core.defaults

import com.sphereon.core.api.TestAppGraph
import com.sphereon.core.api.context.asCoreApiContextGraph
import com.sphereon.core.api.createTestAppGraph
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class JvmTest {
    @Test
    fun `context and logging test`() {
        runBlocking {
            val appGraph: TestAppGraph = createTestAppGraph(this, "appId", "profile", "version")
            println(appGraph)
            println(appGraph.userContextManager)

           /* val kmsTest1 = appGraph.kmsCreator.create("testing", order = Order.LOW, kmsType = PredefinedKmsProviderTypes.SOFTWARE)

            println(kmsTest1)

            val kmsTest2 = appGraph.kmsCreator.create("testing", order = Order.LOW, kmsType = PredefinedKmsProviderTypes.SOFTWARE)
            println(kmsTest2)

            val kmsFactory = appGraph.kmsCreator.create("test2", order = Order.HIGH, kmsType = PredefinedKmsProviderTypes.MOBILE)
            println(kmsFactory)

            assertNotEquals(kmsTest1, kmsTest2)
            val kmsTest3 = appGraph.keyService.mobileKmsFactories.last().create("testing from inject", order = Order.HIGH)
            println(kmsTest3)*/

            appGraph.appLogger().info("test message at app level")

            println("appId: ${appGraph.appId}")
            println("version: ${appGraph.version}")

            val keyvaultUrl = appGraph.appConfigService.getPropertyAsString("azure.keyvault.url")
            println("azure.keyvault.url: $keyvaultUrl")
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test@principal.com"),
                    DefaultPrincipalInputString("test@principal.com"),
                )

            val sessionContextGraph = contextInstance.sessionContextManager.createOrGetFromId("testAction", principalType = com.sphereon.di.context.PrincipalType.USER)
            val sessionContext1 = sessionContextGraph.sessionContext
            println(contextInstance)
            println(appGraph)

//            val log1 = contextComp.logger
//            val log2 = test.logger

            val contextComp1Extended = contextInstance.asCoreApiContextGraph()
            val log = sessionContextGraph.asCoreApiServiceGraph().logger()
            log.info("test message")

            val contextInstance2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test2@principal2.com"),
                    DefaultPrincipalInputString("test2@principal2.com"),
                )
            val contextComp2Extended = contextInstance2.asCoreApiContextGraph()
            val sessionContext2Comp =
                contextComp2Extended.createExecutionContextGraph(appGraph.userContextManager.getActive().context, "testAction")
//            contextComp2.loggerWithTag("test").info("test message2", sessionContext2Comp.sessionContext)

            log.info("test message3")

           /* sessionContext2Comp.asCoreApiServiceGraph().helloWorld.execute(
                LogMessage(level = LogLevel.WARN, message = "OLA"),
                sessionContext2Comp.sessionContext
            )
*/
        }
    }

    /*@Test
    fun `auto registered action should be executed by id`() {
        runBlocking {
            val appGraph: TestAppGraph = createTestAppGraph(this, "appId", "profile", "version")
            val start = Clock.System.now()
            val cmd = appGraph.commandInvoker.resolve(HelloWorldServiceImpl.SERVICE_ID)!!
            @Suppress("UNCHECKED_CAST")
            val out = appGraph.commandInvoker.execute(
                tenantInput = DefaultTenantInputString("test@principal.com"),
                principalInput = DefaultPrincipalInputString("test@principal.com"),
                command = cmd as com.sphereon.core.api.service.ServiceCommand<String, String, com.sphereon.core.api.error.IdkError>,
                input = "Hello world"
            )
            assertTrue(out.isOk)
            println(Clock.System.now().minus(start).toString(DurationUnit.MILLISECONDS))

        }
    }

    @Test
    fun `graph declared action should be executed by injection`() {
        runBlocking {
            val appGraph: TestAppGraph = createTestAppGraph(this, "appId", "profile", "version")
            val start = Clock.System.now()
            val contextInstance = appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test@principal.com"),
                DefaultPrincipalInputString("test@principal.com")
            )
            val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId(
                sessionId = "HelloWorld",
                principalType = PrincipalType.USER,
            )
            val out = sessionGraph.asCoreApiServiceGraph().helloWorld.execute(args = "hello world")
            assertTrue(out.isOk)
            println(Clock.System.now().minus(start).toString(DurationUnit.MILLISECONDS))

        }
    }*/
}
