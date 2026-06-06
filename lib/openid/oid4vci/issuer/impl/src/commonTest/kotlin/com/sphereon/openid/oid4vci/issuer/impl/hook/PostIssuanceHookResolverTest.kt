/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.hook

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.service.ServiceCommandRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [PostIssuanceHookResolver]. Covers every routing decision
 * the issuer's hook fan-out relies on, without requiring the full issuer
 * machinery (asBridge, nonceManager, proofVerifiers, sessionStore, etc.)
 * to be wired.
 */
class PostIssuanceHookResolverTest {
    private val allCommands =
        listOf(
            "hook.post-issuance.redemption-consume",
            "hook.post-issuance.webhook-notify",
            "hook.post-issuance.siem-fanout",
            "hook.identity.after-correlation-verified", // non-matching — different hook point
            "kms.keys.generate", // non-hook command
        )

    @Test
    fun defaultPatternResolvesAllPostIssuanceHooks() {
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(allCommands),
                propertyResolver = null,
                sessionAllowList = null,
            )
        assertEquals(
            setOf(
                "hook.post-issuance.redemption-consume",
                "hook.post-issuance.webhook-notify",
                "hook.post-issuance.siem-fanout",
            ),
            ids,
            "default pattern `hook.post-issuance.**` must match every command under the prefix",
        )
    }

    @Test
    fun explicitConfigOverridesPattern() {
        // Operator pins dispatch to a single command; pattern default is suppressed.
        val config =
            FakePropertyResolver(
                "hooks.oid4vci.after-credential-issued.commands" to "hook.post-issuance.redemption-consume",
            )
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(allCommands),
                propertyResolver = config,
                sessionAllowList = null,
            )
        // With explicit list the default pattern ALSO applies (both are unioned) —
        // unless the operator overrides the pattern too. This test models "I want
        // exactly these commands" by setting both keys.
        val idsExclusive =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(allCommands),
                propertyResolver =
                    FakePropertyResolver(
                        "hooks.oid4vci.after-credential-issued.commands" to "hook.post-issuance.redemption-consume",
                        "hooks.oid4vci.after-credential-issued.patterns" to "hook.never.matches.**",
                    ),
                sessionAllowList = null,
            )
        assertEquals(
            setOf("hook.post-issuance.redemption-consume"),
            idsExclusive,
            "explicit commands + non-matching patterns narrows dispatch to the listed IDs only",
        )
        assertTrue(
            "hook.post-issuance.redemption-consume" in ids,
            "union case still resolves the explicit ID",
        )
    }

    @Test
    fun sessionAllowListIsTheResolvedSetWhenNoDeploymentConfig() {
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(allCommands),
                propertyResolver = null,
                sessionAllowList = listOf("hook.post-issuance.redemption-consume"),
            )
        assertEquals(
            setOf("hook.post-issuance.redemption-consume"),
            ids,
            "a per-session allow-list is authoritative: it is the resolved set verbatim",
        )
    }

    @Test
    fun sessionAllowListIsAuthoritativeOverDeploymentPatterns() {
        // A per-session allow-list is an explicit opt-in and is authoritative: it fires exactly the
        // listed hook ids regardless of the deployment patterns (which apply only when no allow-list
        // is given). Execution stays gated downstream (the dispatcher only fires ids that resolve and
        // whose supports(args) is true).
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(allCommands),
                propertyResolver =
                    FakePropertyResolver(
                        "hooks.oid4vci.after-credential-issued.patterns" to "hook.post-issuance.siem-fanout",
                    ),
                sessionAllowList =
                    listOf(
                        "hook.post-issuance.siem-fanout",
                        "hook.post-issuance.redemption-consume", // not in the deployment pattern, but allow-listed
                    ),
            )
        assertEquals(
            setOf("hook.post-issuance.siem-fanout", "hook.post-issuance.redemption-consume"),
            ids,
            "the session allow-list is authoritative; deployment patterns do not narrow it",
        )
    }

    @Test
    fun unknownExplicitIdsDroppedSilently() {
        // Operator config references a command that doesn't exist (maybe
        // renamed, not yet deployed). Resolution must not error — we drop
        // the unknown name and move on.
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(allCommands),
                propertyResolver =
                    FakePropertyResolver(
                        "hooks.oid4vci.after-credential-issued.commands" to
                            "hook.post-issuance.redemption-consume,hook.does.not.exist",
                        "hooks.oid4vci.after-credential-issued.patterns" to "hook.never.matches.**",
                    ),
                sessionAllowList = null,
            )
        assertEquals(setOf("hook.post-issuance.redemption-consume"), ids)
    }

    @Test
    fun noHooksResolvedWhenRegistryIsEmpty() {
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
                hookPointId = "oid4vci.after-credential-issued",
                discovery = FakeRegistry(emptyList()),
                propertyResolver = null,
                sessionAllowList = null,
            )
        assertTrue(ids.isEmpty(), "pure-IDK deployment without any hooks returns empty set")
    }

    // ─────────────────────────────────────────────────────────────

    private class FakeRegistry(
        private val ids: List<String>
    ) : ServiceCommandRegistry {
        override fun has(commandId: String): Boolean = commandId in ids

        override fun listCommandIds(): List<String> = ids
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
