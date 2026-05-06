/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.openid.oid4vci.issuer.impl.hook

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.ServiceCommandRegistry
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.issuer.hook.PostIssuanceHookArgs
import com.sphereon.openid.oid4vci.issuer.hook.PostIssuanceHookResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Integration test for [PostIssuanceHookDispatcher] — drives the full
 * resolve → supports-filter → fan-out path with multiple real hook
 * `ServiceCommand`s. Validates what the Sprint 1 plan's S1-4 exit
 * criteria call for:
 *
 *  * Pattern fan-out: two hooks registered under `hook.post-issuance.*`
 *    both fire for the same event (confirms the dispatch isn't a hardcoded
 *    wire to a single hook).
 *  * Per-session allow-list narrows dispatch to a named subset.
 *  * `supports()` decline removes a hook from dispatch for args that
 *    don't meet its precondition.
 *  * Explicit config overrides pattern: the default pattern can be
 *    suppressed with an `hooks.<point>.patterns` override.
 *  * One failing hook doesn't cascade — siblings still fire.
 */
class PostIssuanceHookDispatcherTest {
    private val tenantId = "tenant-e2e"
    private val t0: Instant = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun allMatchingHooksFireOnSameIssuance() =
        runTest {
            val consume = RecordingHook("hook.post-issuance.redemption-consume")
            val webhook = RecordingHook("hook.post-issuance.webhook-notify")
            val siem = RecordingHook("hook.post-issuance.siem-fanout")

            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(consume, webhook, siem),
                    sessionCommands = FakeSessionRegistry(consume, webhook, siem),
                    propertyResolver = null,
                )

            dispatcher.dispatch(args = hookArgs(boundUsageToken = "tok-1"))

            assertEquals(1, consume.invocations)
            assertEquals(1, webhook.invocations)
            assertEquals(1, siem.invocations)
        }

    @Test
    fun hookDecliningViaSupportsIsSkipped() =
        runTest {
            // Redemption hook opts in only when boundUsageToken is set (its
            // real-world contract); dispatch with no token must skip it.
            val redemption =
                SupportsFilteredHook(
                    commandId = "hook.post-issuance.redemption-consume",
                    supportsPredicate = { args -> args is PostIssuanceHookArgs && args.boundUsageToken != null },
                )
            val generic = RecordingHook("hook.post-issuance.audit-generic")

            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(redemption, generic),
                    sessionCommands = FakeSessionRegistry(redemption, generic),
                    propertyResolver = null,
                )

            dispatcher.dispatch(args = hookArgs(boundUsageToken = null))

            assertEquals(0, redemption.invocations, "redemption hook must decline when no usage token")
            assertEquals(1, generic.invocations, "unrelated hook still fires")
        }

    @Test
    fun perSessionAllowListNarrowsToNamedSubset() =
        runTest {
            val consume = RecordingHook("hook.post-issuance.redemption-consume")
            val webhook = RecordingHook("hook.post-issuance.webhook-notify")
            val siem = RecordingHook("hook.post-issuance.siem-fanout")

            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(consume, webhook, siem),
                    sessionCommands = FakeSessionRegistry(consume, webhook, siem),
                    propertyResolver = null,
                )

            dispatcher.dispatch(
                args = hookArgs(boundUsageToken = "tok-1"),
                sessionAllowList = listOf("hook.post-issuance.redemption-consume"),
            )

            assertEquals(1, consume.invocations)
            assertEquals(0, webhook.invocations, "webhook excluded by allow-list")
            assertEquals(0, siem.invocations, "siem excluded by allow-list")
        }

    @Test
    fun explicitConfigOverridesDefaultPattern() =
        runTest {
            val consume = RecordingHook("hook.post-issuance.redemption-consume")
            val webhook = RecordingHook("hook.post-issuance.webhook-notify")

            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(consume, webhook),
                    sessionCommands = FakeSessionRegistry(consume, webhook),
                    propertyResolver =
                        FakePropertyResolver(
                            "hooks.oid4vci.after-credential-issued.commands" to "hook.post-issuance.redemption-consume",
                            "hooks.oid4vci.after-credential-issued.patterns" to "hook.never.matches.**",
                        ),
                )

            dispatcher.dispatch(args = hookArgs(boundUsageToken = "tok-1"))

            assertEquals(1, consume.invocations)
            assertEquals(0, webhook.invocations, "pattern override must suppress default hook.post-issuance.** match")
        }

    @Test
    fun failingHookIsIsolatedOthersStillFire() =
        runTest {
            val failing = FailingHook("hook.post-issuance.broken")
            val ok = RecordingHook("hook.post-issuance.audit-generic")

            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(failing, ok),
                    sessionCommands = FakeSessionRegistry(failing, ok),
                    propertyResolver = null,
                )

            dispatcher.dispatch(args = hookArgs(boundUsageToken = "tok-1"))

            assertTrue(failing.invocations == 1, "failing hook was invoked")
            assertEquals(
                1,
                ok.invocations,
                "sibling hook must still fire when a peer throws",
            )
        }

    @Test
    fun pureIdkDeploymentDispatchIsNoOp() =
        runTest {
            // Simulate pure-IDK deployment: no hook commands registered at all.
            // Dispatcher must return cleanly without error, no hooks invoked.
            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(),
                    sessionCommands = FakeSessionRegistry(),
                    propertyResolver = null,
                )

            // Just proves the call completes; no counter to assert on since no hooks exist.
            dispatcher.dispatch(args = hookArgs(boundUsageToken = "tok-1"))
        }

    @Test
    fun sessionAllowListCannotExpandPatternSet() =
        runTest {
            val consume = RecordingHook("hook.post-issuance.redemption-consume")
            val unrelated = RecordingHook("hook.audit.credential-issuance") // not under hook.post-issuance.**

            val dispatcher =
                PostIssuanceHookDispatcher(
                    resolver = FakeRegistry(consume, unrelated),
                    sessionCommands = FakeSessionRegistry(consume, unrelated),
                    propertyResolver = null,
                )

            // Session allow-list names BOTH, but the deployment-level pattern
            // `hook.post-issuance.**` only matches consume. The allow-list
            // narrows; it cannot add commands the pattern didn't find.
            dispatcher.dispatch(
                args = hookArgs(boundUsageToken = "tok-1"),
                sessionAllowList =
                    listOf(
                        "hook.post-issuance.redemption-consume",
                        "hook.audit.credential-issuance",
                    ),
            )

            assertEquals(1, consume.invocations)
            assertEquals(0, unrelated.invocations, "allow-list may not broaden the deployment set")
        }

    // ─────────────────────────────────────────────────────────────

    private fun hookArgs(boundUsageToken: String?): PostIssuanceHookArgs =
        PostIssuanceHookArgs(
            credentialResponse = CredentialResponse(credentials = null),
            credentialConfigurationId = "EmployeeID",
            tenantId = tenantId,
            issuedAt = t0,
            boundUsageToken = boundUsageToken,
            preAuthCode = boundUsageToken?.let { "PAC-for-$it" },
            subject = "subject-$tenantId",
        )

    private open class RecordingHook(
        override val commandId: String
    ) : ServiceCommand<PostIssuanceHookArgs, PostIssuanceHookResult, IdkError> {
        var invocations: Int = 0
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<PostIssuanceHookArgs> = typeToken()
        override val outputTypeToken: TypeToken<PostIssuanceHookResult> = typeToken()

        override suspend fun supports(args: Any): Boolean = args is PostIssuanceHookArgs

        override suspend fun execute(args: PostIssuanceHookArgs,): IdkResult<PostIssuanceHookResult, IdkError> {
            invocations += 1
            return Ok(PostIssuanceHookResult(handled = true))
        }
    }

    private class SupportsFilteredHook(
        commandId: String,
        private val supportsPredicate: (Any) -> Boolean,
    ) : RecordingHook(commandId) {
        override suspend fun supports(args: Any): Boolean = supportsPredicate(args)
    }

    private class FailingHook(
        override val commandId: String
    ) : ServiceCommand<PostIssuanceHookArgs, PostIssuanceHookResult, IdkError> {
        var invocations: Int = 0
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<PostIssuanceHookArgs> = typeToken()
        override val outputTypeToken: TypeToken<PostIssuanceHookResult> = typeToken()

        override suspend fun supports(args: Any): Boolean = args is PostIssuanceHookArgs

        override suspend fun execute(args: PostIssuanceHookArgs,): IdkResult<PostIssuanceHookResult, IdkError> {
            invocations += 1
            throw IllegalStateException("hook intentionally throws")
        }
    }

    private class FakeRegistry(
        vararg commands: ServiceCommand<*, *, *>
    ) : ServiceCommandRegistry {
        private val ids: List<String> = commands.map { it.commandId }

        override fun has(commandId: String): Boolean = commandId in ids

        override fun listCommandIds(): List<String> = ids
    }

    private class FakeSessionRegistry(
        vararg commands: ServiceCommand<*, *, *>
    ) : SessionScopedCommandRegistry {
        private val byId: Map<String, ServiceCommand<*, *, *>> = commands.associateBy { it.commandId }

        override fun get(commandId: String): ServiceCommand<*, *, *>? = byId[commandId]

        override fun listCommandIds(): List<String> = byId.keys.toList()
    }

    private class FakePropertyResolver(
        vararg entries: Pair<String, String>
    ) : PropertyResolver {
        private val map: Map<String, String> = entries.toMap()

        override fun containsProperty(key: String): Boolean = key in map

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?
        ): String? = map[key] ?: defaultValue

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?
        ): String = map[key] ?: defaultValue ?: error("missing key: $key")

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getProperty(
            key: String,
            targetType: kotlin.reflect.KClass<T>,
            defaultValue: T?,
        ): T? =
            if (targetType == String::class) {
                (map[key] ?: defaultValue as? String) as T?
            } else {
                throw NotImplementedError("only String keys needed for test")
            }

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: kotlin.reflect.KClass<T>,
            defaultValue: T?,
        ): T = getProperty(key, targetType, defaultValue) ?: error("missing key: $key")

        override fun getAllProperties(): Map<String, Any> = map

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = map

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean
        ): Map<String, Any> = map.filterKeys { k -> prefixes.any { k.startsWith(it) } }

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = map.filterKeys { k -> prefixes.any { k.startsWith(it) } }
    }
}
