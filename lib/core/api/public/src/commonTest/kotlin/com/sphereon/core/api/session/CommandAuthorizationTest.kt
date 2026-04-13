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

package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.context.NoOpSessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissiveAuthorizerTest {

    @Test
    fun permissiveAuthorizerAllowsAllCommands() = runTest {
        val commandId = CommandId("core.session.session-create")
        val result = PermissiveAuthorizer.isAuthorized(commandId, NoOpSessionContext)
        assertTrue(result.isOk)
    }

    @Test
    fun permissiveAuthorizerAllowsAnyCommand() = runTest {
        val commands = listOf(
            "core.session.session-create",
            "did.manager.did-resolve",
            "openid.oid4vp.verifier-request-create"
        )

        for (cmd in commands) {
            val result = PermissiveAuthorizer.isAuthorized(CommandId(cmd), NoOpSessionContext)
            assertTrue(result.isOk, "Expected $cmd to be authorized")
        }
    }
}

class PatternCommandAuthorizerTest {

    @Test
    fun emptyPatternsAllowsAllCommands() = runTest {
        val authorizer = PatternCommandAuthorizer(emptySet())
        val result = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isOk)
    }

    @Test
    fun matchingPatternAllowsCommand() = runTest {
        val authorizer = PatternCommandAuthorizer(setOf("core.**"))
        val result = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isOk)
    }

    @Test
    fun nonMatchingPatternDeniesCommand() = runTest {
        val authorizer = PatternCommandAuthorizer(setOf("did.**"))
        val result = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isErr)
        assertEquals("COMMAND_NOT_AUTHORIZED", result.error.code)
    }

    @Test
    fun multiplePatternMatchesAnyAllows() = runTest {
        val authorizer = PatternCommandAuthorizer(setOf("did.**", "core.**"))

        val coreResult = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(coreResult.isOk)

        val didResult = authorizer.isAuthorized(
            CommandId("did.manager.did-resolve"),
            NoOpSessionContext
        )
        assertTrue(didResult.isOk)
    }

    @Test
    fun fromPatternsCreatesAuthorizer() = runTest {
        val authorizer = PatternCommandAuthorizer.fromPatterns("core.**", "did.**")

        val coreResult = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(coreResult.isOk)
    }

    @Test
    fun permissiveFactoryAllowsAll() = runTest {
        val authorizer = PatternCommandAuthorizer.permissive()
        val result = authorizer.isAuthorized(
            CommandId("any.command.type-action"),
            NoOpSessionContext
        )
        assertTrue(result.isOk)
    }

    @Test
    fun wildcardPatternMatchesSingleSegment() = runTest {
        val authorizer = PatternCommandAuthorizer(setOf("data.*.party-read"))

        val storeResult = authorizer.isAuthorized(
            CommandId("data.store.party-read"),
            NoOpSessionContext
        )
        assertTrue(storeResult.isOk)

        val cacheResult = authorizer.isAuthorized(
            CommandId("data.cache.party-read"),
            NoOpSessionContext
        )
        assertTrue(cacheResult.isOk)

        val writeResult = authorizer.isAuthorized(
            CommandId("data.store.party-write"),
            NoOpSessionContext
        )
        assertTrue(writeResult.isErr)
    }

    @Test
    fun alternativesPatternMatchesOptions() = runTest {
        val authorizer = PatternCommandAuthorizer(setOf("data.{store,cache}.party-read"))

        val storeResult = authorizer.isAuthorized(
            CommandId("data.store.party-read"),
            NoOpSessionContext
        )
        assertTrue(storeResult.isOk)

        val userResult = authorizer.isAuthorized(
            CommandId("data.user.party-read"),
            NoOpSessionContext
        )
        assertTrue(userResult.isErr)
    }
}

class CompositeAuthorizerTest {

    @Test
    fun allAuthorizersMustPassForSuccess() = runTest {
        val allow1 = PatternCommandAuthorizer(setOf("core.**"))
        val allow2 = PatternCommandAuthorizer(setOf("core.session.**"))
        val composite = CompositeAuthorizer(listOf(allow1, allow2))

        val result = composite.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isOk)
    }

    @Test
    fun firstFailureStopsChecking() = runTest {
        val deny = PatternCommandAuthorizer(setOf("did.**"))
        val allow = PatternCommandAuthorizer(setOf("core.**"))
        val composite = CompositeAuthorizer(listOf(deny, allow))

        val result = composite.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isErr)
    }

    @Test
    fun ofFactoryCreatesComposite() = runTest {
        val allow1 = PatternCommandAuthorizer(setOf("core.**"))
        val allow2 = PatternCommandAuthorizer(setOf("core.session.**"))
        val composite = CompositeAuthorizer.of(allow1, allow2)

        val result = composite.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isOk)
    }
}

class DenyPatternAuthorizerTest {

    @Test
    fun matchingDenyPatternDeniesCommand() = runTest {
        val authorizer = DenyPatternAuthorizer(setOf("admin.**"))

        val result = authorizer.isAuthorized(
            CommandId("admin.user.user-delete"),
            NoOpSessionContext
        )
        assertTrue(result.isErr)
    }

    @Test
    fun nonMatchingDenyPatternAllowsCommand() = runTest {
        val authorizer = DenyPatternAuthorizer(setOf("admin.**"))

        val result = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(result.isOk)
    }

    @Test
    fun fromPatternsCreatesDenyAuthorizer() = runTest {
        val authorizer = DenyPatternAuthorizer.fromPatterns("admin.**", "dangerous.**")

        val adminResult = authorizer.isAuthorized(
            CommandId("admin.user.user-delete"),
            NoOpSessionContext
        )
        assertTrue(adminResult.isErr)

        val dangerousResult = authorizer.isAuthorized(
            CommandId("dangerous.system.system-wipe"),
            NoOpSessionContext
        )
        assertTrue(dangerousResult.isErr)

        val safeResult = authorizer.isAuthorized(
            CommandId("core.session.session-create"),
            NoOpSessionContext
        )
        assertTrue(safeResult.isOk)
    }
}

class AuthorizationErrorTest {

    @Test
    fun authorizationErrorContainsCorrectCode() {
        val error = authorizationError(
            CommandId("core.session.session-create"),
            "Access denied"
        )
        assertEquals("COMMAND_NOT_AUTHORIZED", error.code)
    }

    @Test
    fun authorizationErrorContainsCommandIdInMessage() {
        val error = authorizationError(
            CommandId("core.session.session-create"),
            "Access denied"
        )
        assertTrue(error.message.defaultMessage.contains("core.session.session-create"))
    }

    @Test
    fun authorizationErrorContainsReasonInMessage() {
        val error = authorizationError(
            CommandId("core.session.session-create"),
            "Insufficient permissions"
        )
        assertTrue(error.message.defaultMessage.contains("Insufficient permissions"))
    }

    @Test
    fun authorizationErrorContainsActorInMeta() {
        val error = authorizationError(
            CommandId("core.session.session-create"),
            "Access denied",
            actor = "user-123"
        )
        assertEquals("user-123", error.meta["actor"])
    }
}
