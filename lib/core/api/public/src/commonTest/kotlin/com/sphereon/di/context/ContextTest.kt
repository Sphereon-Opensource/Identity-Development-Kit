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
 */

package com.sphereon.di.context

import com.sphereon.di.Order
import com.sphereon.di.session.SessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserContextCompanionTest {

    @Test
    fun backgroundServiceConstantIsCorrect() {
        assertEquals("<anonymous>:<anonymous>:background-service", UserContext.BACKGROUND_SERVICE)
    }

    @Test
    fun anonymousConstantIsCorrect() {
        assertEquals("<anonymous>:<anonymous>:default", UserContext.ANONYMOUS)
    }
}

class AnonymousPrincipalTest {

    @Test
    fun principalIsAnonymous() {
        assertEquals("<anonymous>", AnonymousPrincipal.principal)
    }

    @Test
    fun toStringContainsPrincipal() {
        val str = AnonymousPrincipal.toString()
        assertTrue(str.contains("<anonymous>"))
        assertTrue(str.contains("AnonymousPrincipal"))
    }

    @Test
    fun equalsReturnsTrueForSameInstance() {
        assertTrue(AnonymousPrincipal == AnonymousPrincipal)
    }

    @Test
    fun equalsReturnsTrueForSamePrincipalValue() {
        val other = object : PrincipalAware {
            override val principal = "<anonymous>"
        }
        assertTrue(AnonymousPrincipal.equals(other))
    }

    @Test
    fun equalsReturnsFalseForDifferentPrincipal() {
        val other = object : PrincipalAware {
            override val principal = "different"
        }
        assertFalse(AnonymousPrincipal.equals(other))
    }

    @Test
    fun equalsReturnsFalseForNonPrincipalAware() {
        assertFalse(AnonymousPrincipal.equals("not a principal"))
    }

    @Test
    fun hashCodeIsConsistent() {
        assertEquals("<anonymous>".hashCode(), AnonymousPrincipal.hashCode())
    }
}

class AnonymousContextTest {

    @Test
    fun idIsAnonymousDefault() {
        assertEquals("<anonymous>:<anonymous>:default", AnonymousContext.id)
    }

    @Test
    fun tenantIdIsAnonymous() {
        assertEquals("<anonymous>", AnonymousContext.tenant.tenantId)
    }

    @Test
    fun principalIsAnonymous() {
        assertEquals("<anonymous>", AnonymousContext.principal)
    }

    @Test
    fun secureDetailsIsNull() {
        assertNull(AnonymousContext.secureDetails)
    }

    @Test
    fun toStringContainsId() {
        val str = AnonymousContext.toString()
        assertTrue(str.contains(AnonymousContext.id))
    }

    @Test
    fun toStringContainsTenant() {
        val str = AnonymousContext.toString()
        assertTrue(str.contains("tenant="))
    }
}

class CreateAnonymousSessionContextTest {

    @Test
    fun createdContextHasCorrectSessionId() {
        val ctx = createAnonymousSessionContext("test-session")
        assertEquals("test-session", ctx.sessionId)
    }

    @Test
    fun createdContextHasAnonymousUserContext() {
        val ctx = createAnonymousSessionContext("test-session")
        assertEquals(AnonymousContext, ctx.context)
    }

    @Test
    fun toStringContainsSessionId() {
        val ctx = createAnonymousSessionContext("my-session")
        assertTrue(ctx.toString().contains("my-session"))
    }

    @Test
    fun toStringContainsAnonymous() {
        val ctx = createAnonymousSessionContext("test")
        assertTrue(ctx.toString().contains("Anonymous"))
    }

    @Test
    fun equalsReturnsTrueForSameSessionIdAndContext() {
        val ctx1 = createAnonymousSessionContext("session-1")
        val ctx2 = createAnonymousSessionContext("session-1")
        assertEquals(ctx1, ctx2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSessionId() {
        val ctx1 = createAnonymousSessionContext("session-1")
        val ctx2 = createAnonymousSessionContext("session-2")
        assertNotEquals(ctx1, ctx2)
    }

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val ctx = createAnonymousSessionContext("test")
        assertTrue(ctx.equals(ctx))
    }

    @Test
    fun equalsReturnsFalseForNonSessionContext() {
        val ctx = createAnonymousSessionContext("test")
        assertFalse(ctx.equals("not a session context"))
    }

    @Test
    fun hashCodeIsConsistentWithEquals() {
        val ctx1 = createAnonymousSessionContext("session-1")
        val ctx2 = createAnonymousSessionContext("session-1")
        assertEquals(ctx1.hashCode(), ctx2.hashCode())
    }

    @Test
    fun hashCodeDiffersForDifferentSessions() {
        val ctx1 = createAnonymousSessionContext("session-1")
        val ctx2 = createAnonymousSessionContext("session-2")
        assertNotEquals(ctx1.hashCode(), ctx2.hashCode())
    }
}

class UserContextToSessionContextTest {

    @Test
    fun toSessionContextUsesDefaultSessionIdWhenNotProvided() {
        val sessionCtx = AnonymousContext.toSessionContext()
        assertEquals("_from_user_context", sessionCtx.sessionId)
    }

    @Test
    fun toSessionContextUsesProvidedSessionId() {
        val sessionCtx = AnonymousContext.toSessionContext("custom-session")
        assertEquals("custom-session", sessionCtx.sessionId)
    }

    @Test
    fun toSessionContextPreservesUserContext() {
        val sessionCtx = AnonymousContext.toSessionContext()
        assertEquals(AnonymousContext, sessionCtx.context)
    }

    @Test
    fun toStringContainsUserToSessionContext() {
        val sessionCtx = AnonymousContext.toSessionContext("test")
        assertTrue(sessionCtx.toString().contains("UserToSessionContext"))
    }

    @Test
    fun equalsReturnsTrueForMatchingContexts() {
        val ctx1 = AnonymousContext.toSessionContext("test")
        val ctx2 = AnonymousContext.toSessionContext("test")
        assertEquals(ctx1, ctx2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSessionIds() {
        val ctx1 = AnonymousContext.toSessionContext("test-1")
        val ctx2 = AnonymousContext.toSessionContext("test-2")
        assertNotEquals(ctx1, ctx2)
    }

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val ctx = AnonymousContext.toSessionContext()
        assertTrue(ctx.equals(ctx))
    }

    @Test
    fun equalsReturnsFalseForNonSessionContext() {
        val ctx = AnonymousContext.toSessionContext()
        assertFalse(ctx.equals("not a context"))
    }

    @Test
    fun hashCodeIsConsistentWithEquals() {
        val ctx1 = AnonymousContext.toSessionContext("test")
        val ctx2 = AnonymousContext.toSessionContext("test")
        assertEquals(ctx1.hashCode(), ctx2.hashCode())
    }
}

class NoOpSessionContextTest {

    @Test
    fun sessionIdIsAnonymous() {
        assertEquals("<anonymous>", NoOpSessionContext.sessionId)
    }

    @Test
    fun contextIsAnonymous() {
        assertEquals(AnonymousContext, NoOpSessionContext.context)
    }

    @Test
    fun toStringContainsAnonymous() {
        val str = NoOpSessionContext.toString()
        assertTrue(str.contains("Anonymous"))
    }

    @Test
    fun toStringContainsSessionId() {
        val str = NoOpSessionContext.toString()
        assertTrue(str.contains("<anonymous>"))
    }
}

class TenantResolverCompareToTest {

    @Test
    fun resolversWithDifferentOrdersCompareByOrder() {
        val low = TestTenantResolver(Order.LOWEST.orderValue)
        val high = TestTenantResolver(Order.HIGHEST.orderValue)

        assertTrue(high.compareTo(low) < 0) // Higher priority (lower value) comes first
        assertTrue(low.compareTo(high) > 0)
    }

    @Test
    fun resolversWithSameOrderReturnOne() {
        val resolver1 = TestTenantResolver(Order.MEDIUM.orderValue)
        val resolver2 = TestTenantResolver(Order.MEDIUM.orderValue)

        // When orders are equal, compareTo returns 1 (first seen takes precedence)
        assertEquals(1, resolver1.compareTo(resolver2))
    }

    private class TestTenantResolver(override val order: Int) : TenantResolver {
        override fun resolveTenant(tenantInput: TenantInput) = "test"
        override fun supports(tenantInput: TenantInput) = true
    }
}

class PrincipalResolverCompareToTest {

    @Test
    fun resolversWithDifferentPrioritiesCompareByPriority() {
        val low = TestPrincipalResolver(Order.LOWEST.orderValue)
        val high = TestPrincipalResolver(Order.HIGHEST.orderValue)

        assertTrue(high.compareTo(low) < 0)
        assertTrue(low.compareTo(high) > 0)
    }

    @Test
    fun resolversWithSamePriorityReturnOne() {
        val resolver1 = TestPrincipalResolver(Order.MEDIUM.orderValue)
        val resolver2 = TestPrincipalResolver(Order.MEDIUM.orderValue)

        assertEquals(1, resolver1.compareTo(resolver2))
    }

    private class TestPrincipalResolver(override val priority: Int) : PrincipalResolver {
        override fun resolvePrincipal(principalInput: PrincipalInput, tenant: TenantAware) = "test"
        override fun supports(principalInput: PrincipalInput) = true
    }
}

class ContextSessionPairTest {

    private val testContext = AnonymousContext
    private val testSession = createAnonymousSessionContext("test-session")

    @Test
    fun contextIdIsAccessible() {
        val pair = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        assertEquals("ctx-1", pair.contextId)
    }

    @Test
    fun sessionIdIsAccessible() {
        val pair = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        assertEquals("session-1", pair.sessionId)
    }

    @Test
    fun sessionIdCanBeNull() {
        val pair = ContextSessionPair("ctx-1", null, testContext, null)
        assertNull(pair.sessionId)
    }

    @Test
    fun contextIsAccessible() {
        val pair = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        assertEquals(testContext, pair.context)
    }

    @Test
    fun sessionIsAccessible() {
        val pair = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        assertEquals(testSession, pair.session)
    }

    @Test
    fun sessionDefaultsToNull() {
        val pair = ContextSessionPair("ctx-1", "session-1", testContext)
        assertNull(pair.session)
    }

    @Test
    fun isAnonymousReturnsTrueForAnonymousPrincipal() {
        val pair = ContextSessionPair("ctx-1", "session-1", AnonymousContext, testSession)
        assertTrue(pair.isAnonymous)
    }

    @Test
    fun isAnonymousReturnsFalseForNonAnonymousPrincipal() {
        val authContext = object : UserContext {
            override val id = "auth-user"
            override val tenant = object : TenantContextData {
                override val tenantId = "tenant-1"
            }
            override val principal = "user@example.com"
            override val secureDetails: SecuredTenantContextDetails? = null
        }
        val pair = ContextSessionPair("ctx-1", "session-1", authContext)
        assertFalse(pair.isAnonymous)
    }

    @Test
    fun isAuthenticatedReturnsTrueForNonAnonymous() {
        val authContext = object : UserContext {
            override val id = "auth-user"
            override val tenant = object : TenantContextData {
                override val tenantId = "tenant-1"
            }
            override val principal = "user@example.com"
            override val secureDetails: SecuredTenantContextDetails? = null
        }
        val pair = ContextSessionPair("ctx-1", "session-1", authContext)
        assertTrue(pair.isAuthenticated)
    }

    @Test
    fun isAuthenticatedReturnsFalseForAnonymous() {
        val pair = ContextSessionPair("ctx-1", "session-1", AnonymousContext)
        assertFalse(pair.isAuthenticated)
    }

    @Test
    fun dataClassEquality() {
        val pair1 = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        val pair2 = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        assertEquals(pair1, pair2)
    }

    @Test
    fun dataClassHashCode() {
        val pair1 = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        val pair2 = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        assertEquals(pair1.hashCode(), pair2.hashCode())
    }

    @Test
    fun dataClassCopy() {
        val original = ContextSessionPair("ctx-1", "session-1", testContext, testSession)
        val copied = original.copy(sessionId = "session-2")
        assertEquals("session-2", copied.sessionId)
        assertEquals("ctx-1", copied.contextId)
    }
}

class CrossContextOperationExceptionTest {

    @Test
    fun messageIsAccessible() {
        val ex = CrossContextOperationException("Test error")
        assertEquals("Test error", ex.message)
    }

    @Test
    fun sourceContextIdIsAccessible() {
        val ex = CrossContextOperationException("Test", sourceContextId = "source-1")
        assertEquals("source-1", ex.sourceContextId)
    }

    @Test
    fun targetContextIdIsAccessible() {
        val ex = CrossContextOperationException("Test", targetContextId = "target-1")
        assertEquals("target-1", ex.targetContextId)
    }

    @Test
    fun targetSessionIdIsAccessible() {
        val ex = CrossContextOperationException("Test", targetSessionId = "session-1")
        assertEquals("session-1", ex.targetSessionId)
    }

    @Test
    fun causeIsAccessible() {
        val cause = RuntimeException("Original error")
        val ex = CrossContextOperationException("Test", cause = cause)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun allFieldsDefaultToNull() {
        val ex = CrossContextOperationException("Test")
        assertNull(ex.sourceContextId)
        assertNull(ex.targetContextId)
        assertNull(ex.targetSessionId)
        assertNull(ex.cause)
    }

    @Test
    fun allFieldsCanBeSet() {
        val cause = RuntimeException("Cause")
        val ex = CrossContextOperationException(
            message = "Error message",
            sourceContextId = "source",
            targetContextId = "target",
            targetSessionId = "session",
            cause = cause
        )
        assertEquals("Error message", ex.message)
        assertEquals("source", ex.sourceContextId)
        assertEquals("target", ex.targetContextId)
        assertEquals("session", ex.targetSessionId)
        assertEquals(cause, ex.cause)
    }
}

class CrossContextResultTest {

    @Test
    fun successContainsValue() {
        val pair = ContextSessionPair("ctx", "sess", AnonymousContext)
        val result = CrossContextResult.Success("test-value", pair)
        assertEquals("test-value", result.value)
    }

    @Test
    fun successContainsExecutedIn() {
        val pair = ContextSessionPair("ctx", "sess", AnonymousContext)
        val result = CrossContextResult.Success("value", pair)
        assertEquals(pair, result.executedIn)
    }

    @Test
    fun contextNotFoundContainsContextId() {
        val result = CrossContextResult.ContextNotFound("missing-ctx")
        assertEquals("missing-ctx", result.contextId)
    }

    @Test
    fun sessionNotFoundContainsContextAndSessionId() {
        val result = CrossContextResult.SessionNotFound("ctx-1", "sess-1")
        assertEquals("ctx-1", result.contextId)
        assertEquals("sess-1", result.sessionId)
    }

    @Test
    fun operationFailedContainsError() {
        val error = RuntimeException("Failed")
        val result = CrossContextResult.OperationFailed(error, "ctx-1", "sess-1")
        assertEquals(error, result.error)
        assertEquals("ctx-1", result.contextId)
        assertEquals("sess-1", result.sessionId)
    }

    @Test
    fun operationFailedSessionIdDefaultsToNull() {
        val error = RuntimeException("Failed")
        val result = CrossContextResult.OperationFailed(error, "ctx-1")
        assertNull(result.sessionId)
    }

    @Test
    fun noSuitableContextFoundIsSingleton() {
        val result1 = CrossContextResult.NoSuitableContextFound
        val result2 = CrossContextResult.NoSuitableContextFound
        assertTrue(result1 === result2)
    }

    @Test
    fun successDataClassEquality() {
        val pair = ContextSessionPair("ctx", "sess", AnonymousContext)
        val result1 = CrossContextResult.Success("value", pair)
        val result2 = CrossContextResult.Success("value", pair)
        assertEquals(result1, result2)
    }

    @Test
    fun contextNotFoundDataClassEquality() {
        val result1 = CrossContextResult.ContextNotFound("ctx-1")
        val result2 = CrossContextResult.ContextNotFound("ctx-1")
        assertEquals(result1, result2)
    }
}
