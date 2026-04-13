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

package com.sphereon.core.defaults.context

import com.sphereon.di.Order
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.TenantAware
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.TenantInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TenantContextDataImplTest {
    @Test
    fun tenantIdIsAccessible() {
        val data = TenantContextDataImpl("my-tenant")
        assertEquals("my-tenant", data.tenantId)
    }

    @Test
    fun toStringReturnsTenantId() {
        val data = TenantContextDataImpl("test-tenant")
        assertEquals("test-tenant", data.toString())
    }

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val data = TenantContextDataImpl("tenant-1")
        assertTrue(data.equals(data))
    }

    @Test
    fun equalsReturnsTrueForEqualTenantId() {
        val data1 = TenantContextDataImpl("tenant-1")
        val data2 = TenantContextDataImpl("tenant-1")
        assertTrue(data1 == data2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTenantId() {
        val data1 = TenantContextDataImpl("tenant-1")
        val data2 = TenantContextDataImpl("tenant-2")
        assertFalse(data1 == data2)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val data = TenantContextDataImpl("tenant-1")
        assertFalse(data.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentClass() {
        val data = TenantContextDataImpl("tenant-1")
        assertFalse(data.equals("tenant-1"))
    }

    @Test
    fun hashCodeIsConsistentWithEquals() {
        val data1 = TenantContextDataImpl("tenant-1")
        val data2 = TenantContextDataImpl("tenant-1")
        assertEquals(data1.hashCode(), data2.hashCode())
    }

    @Test
    fun hashCodeDiffersForDifferentTenantIds() {
        val data1 = TenantContextDataImpl("tenant-1")
        val data2 = TenantContextDataImpl("tenant-2")
        assertNotEquals(data1.hashCode(), data2.hashCode())
    }
}

class DefaultTenantInputStringTest {
    @Test
    fun tenantPropertyIsAccessible() {
        val input = DefaultTenantInputString("my-tenant")
        assertEquals("my-tenant", input.tenant)
    }

    @Test
    fun toStringFormatsCorrectly() {
        val input = DefaultTenantInputString("test-tenant")
        assertEquals("TenantInput(tenant='test-tenant')", input.toString())
    }

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val input = DefaultTenantInputString("tenant-1")
        assertTrue(input.equals(input))
    }

    @Test
    fun equalsReturnsTrueForEqualTenant() {
        val input1 = DefaultTenantInputString("tenant-1")
        val input2 = DefaultTenantInputString("tenant-1")
        assertTrue(input1 == input2)
    }

    @Test
    fun equalsReturnsFalseForDifferentTenant() {
        val input1 = DefaultTenantInputString("tenant-1")
        val input2 = DefaultTenantInputString("tenant-2")
        assertFalse(input1 == input2)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val input = DefaultTenantInputString("tenant-1")
        assertFalse(input.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentClass() {
        val input = DefaultTenantInputString("tenant-1")
        assertFalse(input.equals("tenant-1"))
    }

    @Test
    fun hashCodeIsConsistentWithEquals() {
        val input1 = DefaultTenantInputString("tenant-1")
        val input2 = DefaultTenantInputString("tenant-1")
        assertEquals(input1.hashCode(), input2.hashCode())
    }
}

class DefaultPrincipalInputStringTest {
    @Test
    fun principalPropertyIsAccessible() {
        val input = DefaultPrincipalInputString("user@example.com")
        assertEquals("user@example.com", input.principal)
    }

    @Test
    fun toStringFormatsCorrectly() {
        val input = DefaultPrincipalInputString("user@example.com")
        assertEquals("PrincipalInput(principal='user@example.com')", input.toString())
    }

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val input = DefaultPrincipalInputString("user-1")
        assertTrue(input.equals(input))
    }

    @Test
    fun equalsReturnsTrueForEqualPrincipal() {
        val input1 = DefaultPrincipalInputString("user-1")
        val input2 = DefaultPrincipalInputString("user-1")
        assertTrue(input1 == input2)
    }

    @Test
    fun equalsReturnsFalseForDifferentPrincipal() {
        val input1 = DefaultPrincipalInputString("user-1")
        val input2 = DefaultPrincipalInputString("user-2")
        assertFalse(input1 == input2)
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val input = DefaultPrincipalInputString("user-1")
        assertFalse(input.equals(null))
    }

    @Test
    fun equalsReturnsFalseForDifferentClass() {
        val input = DefaultPrincipalInputString("user-1")
        assertFalse(input.equals("user-1"))
    }

    @Test
    fun hashCodeIsConsistentWithEquals() {
        val input1 = DefaultPrincipalInputString("user-1")
        val input2 = DefaultPrincipalInputString("user-1")
        assertEquals(input1.hashCode(), input2.hashCode())
    }
}

class StaticTenantResolverTest {
    @Test
    fun orderIsLowest() {
        val resolver = StaticTenantResolver()
        assertEquals(Order.LOWEST.orderValue, resolver.order)
    }

    @Test
    fun supportsStringTenantInput() {
        val resolver = StaticTenantResolver()
        val input = DefaultTenantInputString("my-tenant")
        assertTrue(resolver.supports(input))
    }

    @Test
    fun doesNotSupportBlankTenantInput() {
        val resolver = StaticTenantResolver()
        val input = DefaultTenantInputString("   ")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun doesNotSupportEmptyTenantInput() {
        val resolver = StaticTenantResolver()
        val input = DefaultTenantInputString("")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun resolveTenantTrimsAndLowercases() {
        val resolver = StaticTenantResolver()
        val input = DefaultTenantInputString("  MY-TENANT  ")
        assertEquals("my-tenant", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantHandlesNormalInput() {
        val resolver = StaticTenantResolver()
        val input = DefaultTenantInputString("tenant-123")
        assertEquals("tenant-123", resolver.resolveTenant(input))
    }
}

class EmailDomainTenantResolverTest {
    @Test
    fun orderIsMedium() {
        val resolver = EmailDomainTenantResolver()
        assertEquals(Order.MEDIUM.orderValue, resolver.order)
    }

    @Test
    fun supportsEmailInput() {
        val resolver = EmailDomainTenantResolver()
        val input = DefaultTenantInputString("user@example.com")
        assertTrue(resolver.supports(input))
    }

    @Test
    fun doesNotSupportNonEmailInput() {
        val resolver = EmailDomainTenantResolver()
        val input = DefaultTenantInputString("my-tenant")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun resolveTenantExtractsDomainAndLowercases() {
        val resolver = EmailDomainTenantResolver()
        val input = DefaultTenantInputString("user@EXAMPLE.COM")
        assertEquals("example.com", resolver.resolveTenant(input))
    }

    @Test
    fun resolveTenantTrimsWhitespace() {
        val resolver = EmailDomainTenantResolver()
        val input = DefaultTenantInputString("user@example.com  ")
        assertEquals("example.com", resolver.resolveTenant(input))
    }
}

class StaticPrincipalResolverTest {
    private val testTenant =
        object : TenantAware {
            override val tenant: TenantContextData = TenantContextDataImpl("test-tenant")
        }

    @Test
    fun priorityIsLowest() {
        val resolver = StaticPrincipalResolver()
        assertEquals(Order.LOWEST.orderValue, resolver.priority)
    }

    @Test
    fun supportsStringPrincipalInput() {
        val resolver = StaticPrincipalResolver()
        val input = DefaultPrincipalInputString("user-123")
        assertTrue(resolver.supports(input))
    }

    @Test
    fun doesNotSupportBlankPrincipalInput() {
        val resolver = StaticPrincipalResolver()
        val input = DefaultPrincipalInputString("   ")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun doesNotSupportEmptyPrincipalInput() {
        val resolver = StaticPrincipalResolver()
        val input = DefaultPrincipalInputString("")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun resolvePrincipalTrims() {
        val resolver = StaticPrincipalResolver()
        val input = DefaultPrincipalInputString("  user-123  ")
        assertEquals("user-123", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalPreservesCase() {
        val resolver = StaticPrincipalResolver()
        val input = DefaultPrincipalInputString("UserName")
        assertEquals("UserName", resolver.resolvePrincipal(input, testTenant))
    }
}

class EmailPrincipalResolverTest {
    private val testTenant =
        object : TenantAware {
            override val tenant: TenantContextData = TenantContextDataImpl("test-tenant")
        }

    @Test
    fun priorityIsMedium() {
        val resolver = EmailPrincipalResolver()
        assertEquals(Order.MEDIUM.orderValue, resolver.priority)
    }

    @Test
    fun supportsEmailPrincipalInput() {
        val resolver = EmailPrincipalResolver()
        val input = DefaultPrincipalInputString("user@example.com")
        assertTrue(resolver.supports(input))
    }

    @Test
    fun doesNotSupportNonEmailPrincipalInput() {
        val resolver = EmailPrincipalResolver()
        val input = DefaultPrincipalInputString("user-123")
        assertFalse(resolver.supports(input))
    }

    @Test
    fun resolvePrincipalLowercasesEmail() {
        val resolver = EmailPrincipalResolver()
        val input = DefaultPrincipalInputString("USER@EXAMPLE.COM")
        assertEquals("user@example.com", resolver.resolvePrincipal(input, testTenant))
    }

    @Test
    fun resolvePrincipalTrimsWhitespace() {
        val resolver = EmailPrincipalResolver()
        val input = DefaultPrincipalInputString("  user@example.com  ")
        assertEquals("user@example.com", resolver.resolvePrincipal(input, testTenant))
    }
}

class TenantResolutionHandlerImplTest {
    @Test
    fun resolveTenantUsesFirstSupportingResolver() {
        val staticResolver = StaticTenantResolver()
        val emailResolver = EmailDomainTenantResolver()
        val handler = TenantResolutionHandlerImpl(setOf(staticResolver, emailResolver))

        // Email resolver has higher priority (MEDIUM) than static (LOWEST)
        // So for email input, email resolver should be used
        val emailInput = DefaultTenantInputString("user@example.com")
        val result = handler.resolveTenant(emailInput)
        assertEquals("example.com", result.tenant.tenantId)
    }

    @Test
    fun resolveTenantFallsBackToLowerPriorityResolver() {
        val staticResolver = StaticTenantResolver()
        val emailResolver = EmailDomainTenantResolver()
        val handler = TenantResolutionHandlerImpl(setOf(staticResolver, emailResolver))

        // For non-email input, static resolver should be used
        val normalInput = DefaultTenantInputString("my-tenant")
        val result = handler.resolveTenant(normalInput)
        assertEquals("my-tenant", result.tenant.tenantId)
    }

    @Test
    fun resolveTenantThrowsWhenNoResolverSupports() {
        val emailResolver = EmailDomainTenantResolver()
        val handler = TenantResolutionHandlerImpl(setOf(emailResolver))

        val normalInput = DefaultTenantInputString("   ") // blank, no resolver supports
        assertFailsWith<IllegalArgumentException> {
            handler.resolveTenant(normalInput)
        }
    }
}

class PrincipalResolutionHandlerImplTest {
    private val testTenant =
        object : TenantAware {
            override val tenant: TenantContextData = TenantContextDataImpl("test-tenant")
        }

    @Test
    fun resolvePrincipalUsesFirstSupportingResolver() {
        val staticResolver = StaticPrincipalResolver()
        val emailResolver = EmailPrincipalResolver()
        val handler = PrincipalResolutionHandlerImpl(setOf(staticResolver, emailResolver))

        // Email resolver has higher priority (MEDIUM) than static (LOWEST)
        val emailInput = DefaultPrincipalInputString("user@example.com")
        val result = handler.resolvePrincipal(emailInput, testTenant)
        assertEquals("user@example.com", result.principal)
    }

    @Test
    fun resolvePrincipalFallsBackToLowerPriorityResolver() {
        val staticResolver = StaticPrincipalResolver()
        val emailResolver = EmailPrincipalResolver()
        val handler = PrincipalResolutionHandlerImpl(setOf(staticResolver, emailResolver))

        // For non-email input, static resolver should be used
        val normalInput = DefaultPrincipalInputString("user-123")
        val result = handler.resolvePrincipal(normalInput, testTenant)
        assertEquals("user-123", result.principal)
    }

    @Test
    fun resolvePrincipalThrowsWhenNoResolverSupports() {
        val emailResolver = EmailPrincipalResolver()
        val handler = PrincipalResolutionHandlerImpl(setOf(emailResolver))

        val normalInput = DefaultPrincipalInputString("   ") // blank, no resolver supports
        assertFailsWith<IllegalArgumentException> {
            handler.resolvePrincipal(normalInput, testTenant)
        }
    }

    @Test
    fun resolvedPrincipalAwareToStringUsePrincipalValue() {
        val staticResolver = StaticPrincipalResolver()
        val handler = PrincipalResolutionHandlerImpl(setOf(staticResolver))

        val input = DefaultPrincipalInputString("test-user")
        val result = handler.resolvePrincipal(input, testTenant)

        assertEquals("test-user", result.toString())
    }
}
