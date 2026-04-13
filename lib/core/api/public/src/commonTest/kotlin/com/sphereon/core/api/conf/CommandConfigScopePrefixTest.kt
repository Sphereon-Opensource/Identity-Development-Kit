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

package com.sphereon.core.api.conf

import com.sphereon.core.api.session.CommandId
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandConfigScopePrefixTest {
    @Test
    fun fullCommandScopeGeneratesFourPrefixes() {
        val scope = CommandConfigScope(module = "kms", service = "keys", command = "get")
        val prefixes = scope.prefixesFor("http.client")

        assertEquals(4, prefixes.size)
        assertEquals("http.client", prefixes[0])
        assertEquals("cmd.kms.default.default.http.client", prefixes[1])
        assertEquals("cmd.kms.keys.default.http.client", prefixes[2])
        assertEquals("cmd.kms.keys.get.http.client", prefixes[3])
    }

    @Test
    fun moduleScopeGeneratesTwoPrefixes() {
        val scope = CommandConfigScope.module("kms")
        val prefixes = scope.prefixesFor("http.client")

        assertEquals(2, prefixes.size)
        assertEquals("http.client", prefixes[0])
        assertEquals("cmd.kms.default.default.http.client", prefixes[1])
    }

    @Test
    fun serviceScopeGeneratesThreePrefixes() {
        val scope = CommandConfigScope.service("kms", "keys")
        val prefixes = scope.prefixesFor("http.client")

        assertEquals(3, prefixes.size)
        assertEquals("http.client", prefixes[0])
        assertEquals("cmd.kms.default.default.http.client", prefixes[1])
        assertEquals("cmd.kms.keys.default.http.client", prefixes[2])
    }

    @Test
    fun globalScopeGeneratesOneBarePrefix() {
        val scope = CommandConfigScope.GLOBAL
        val prefixes = scope.prefixesFor("http.client")

        assertEquals(1, prefixes.size)
        assertEquals("http.client", prefixes[0])
    }

    @Test
    fun fromCommandIdCreatesScopeWithAllSegments() {
        val scope = CommandConfigScope.fromCommandId(CommandId("party.parties.create"))
        assertEquals("party", scope.module)
        assertEquals("parties", scope.service)
        assertEquals("create", scope.command)
    }

    @Test
    fun fromCommandIdStringCreatesScopeWithAllSegments() {
        val scope = CommandConfigScope.fromCommandId("did.manager.resolve")
        assertEquals("did", scope.module)
        assertEquals("manager", scope.service)
        assertEquals("resolve", scope.command)
    }

    @Test
    fun prefixesForDifferentSuffixes() {
        val scope = CommandConfigScope(module = "oid4vp", service = "verifier", command = "request")
        val prefixes = scope.prefixesFor("logging.policy")

        assertEquals("logging.policy", prefixes[0])
        assertEquals("cmd.oid4vp.default.default.logging.policy", prefixes[1])
        assertEquals("cmd.oid4vp.verifier.default.logging.policy", prefixes[2])
        assertEquals("cmd.oid4vp.verifier.request.logging.policy", prefixes[3])
    }

    @Test
    fun defaultConstantValue() {
        assertEquals("default", CommandConfigScope.DEFAULT)
    }
}
