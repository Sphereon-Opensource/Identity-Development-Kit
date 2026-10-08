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

package com.sphereon.oauth2.oidf.op

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.defaults.conf.DefaultUserOpaqueSecretResolver
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixture-owned handle; the known wire credential never enters the configuration property map. */
internal object OidfOpTestOpaqueSecrets {
    val handle = "sec_${UUID.randomUUID().toString().replace("-", "")}"

    fun installHandle() {
        DefaultPrincipalMapPropertySource.addProperty("oauth2.clients.oidf-op-basic.client-secret-id", handle)
    }

    val resolver =
        OpaqueSecretResolver { secretId ->
            if (secretId == handle) {
                Ok("oidf-op-basic-secret-2026")
            } else {
                Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "Unknown OP fixture secret handle"))
            }
        }
}

/** Only the test graph includes this replacement; the main harness keeps the fail-closed default. */
@ContributesTo(UserScope::class, replaces = [DefaultUserOpaqueSecretResolver::class])
interface OidfOpTestOpaqueSecretModule {
    @Provides
    @SingleIn(UserScope::class)
    fun provideOpaqueSecretResolver(): OpaqueSecretResolver = OidfOpTestOpaqueSecrets.resolver
}

class OidfOpOpaqueSecretFixtureTest {
    @Test
    fun onlyGeneratedFixtureHandleResolvesUnchangedWireCredential() =
        runTest {
            assertTrue(Regex("^sec_[A-Za-z0-9_-]{16,128}$").matches(OidfOpTestOpaqueSecrets.handle))
            val resolved = OidfOpTestOpaqueSecrets.resolver.resolve(OidfOpTestOpaqueSecrets.handle)
            assertTrue(resolved.isOk)
            assertEquals("oidf-op-basic-secret-2026", resolved.value)
            assertTrue(OidfOpTestOpaqueSecrets.resolver.resolve("sec_unknown_fixture_0001").isErr)
            assertTrue(OidfOpTestOpaqueSecrets.resolver.resolve("oidf-op-basic-secret-2026").isErr)
        }
}
