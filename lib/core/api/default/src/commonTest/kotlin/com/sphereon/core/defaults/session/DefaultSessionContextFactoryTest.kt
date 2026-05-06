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

package com.sphereon.core.defaults.session

import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.IdentityMetadata
import com.sphereon.di.context.IdentityResolutionResult
import com.sphereon.di.context.PrincipalType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultSessionContextFactoryTest {
    private val factory = DefaultSessionContextFactory()

    @Test
    fun createsSessionContextFromResolution() {
        val resolution =
            IdentityResolutionResult(
                tenantId = "t1",
                principalId = "p1",
                principalType = PrincipalType.USER,
                metadata = IdentityMetadata(),
            )

        val sessionContext = factory.create(sessionId = "s1", resolution = resolution)

        assertEquals("s1", sessionContext.sessionId)
        assertEquals("t1", sessionContext.context.tenant.tenantId)
        assertEquals("p1", sessionContext.context.principal)
        assertEquals("t1:p1:default", sessionContext.context.id)
        assertFalse(sessionContext.isAnonymous())
    }

    @Test
    fun fallsBackToAnonymousConstantsWhenResolutionIsEmpty() {
        val resolution =
            IdentityResolutionResult(
                tenantId = null,
                principalId = null,
                principalType = PrincipalType.ANONYMOUS,
                metadata = IdentityMetadata(),
            )

        val sessionContext = factory.create(sessionId = "s-anon", resolution = resolution)

        assertEquals("s-anon", sessionContext.sessionId)
        assertEquals(IdentityConstants.ANONYMOUS_TENANT_ID, sessionContext.context.tenant.tenantId)
        assertEquals(IdentityConstants.ANONYMOUS_PRINCIPAL_ID, sessionContext.context.principal)
    }

    @Test
    fun treatsAnonymousSessionIdWithAnonymousResolutionAsAnonymous() {
        val resolution =
            IdentityResolutionResult(
                tenantId = null,
                principalId = null,
                principalType = PrincipalType.ANONYMOUS,
                metadata = IdentityMetadata(),
            )

        val sessionContext =
            factory.create(
                sessionId = IdentityConstants.ANONYMOUS_SESSION_ID,
                resolution = resolution,
            )

        assertTrue(sessionContext.isAnonymous())
    }

    @Test
    fun ignoresMetadataInDefaultImplementation() {
        val resolution =
            IdentityResolutionResult(
                tenantId = "t1",
                principalId = "p1",
                principalType = PrincipalType.USER,
                metadata = IdentityMetadata(),
            )

        val sessionContext =
            factory.create(
                sessionId = "s1",
                resolution = resolution,
                metadata = mapOf("ignored" to "value"),
            )

        assertEquals("t1", sessionContext.context.tenant.tenantId)
        assertEquals("p1", sessionContext.context.principal)
    }
}
