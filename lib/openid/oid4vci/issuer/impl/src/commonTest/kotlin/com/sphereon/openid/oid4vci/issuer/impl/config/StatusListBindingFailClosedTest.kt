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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.statuslist.StatusListDefinitionsProvider
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.impl.config.ConfigDrivenStatusListDefinitionsProvider
import dev.zacsweers.metro.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fail-closed status-list binding resolution on the config-driven issuer provider: a credential
 * configuration that declares a `statusListId` which cannot be resolved to a hosted definition
 * (definitions source missing from the deployment, or unknown list id) must surface an error from
 * [com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider.statusListBindingFor]
 * so issuance aborts — issuing without the status claim would produce a credential that can never
 * be revoked.
 *
 * Runs against the REAL config chain: [ConfigDrivenOid4vciIssuerConfigProvider] resolving through
 * the real [ConfigDrivenStatusListDefinitionsProvider], both reading the same in-memory
 * [TestPrincipalConfigService].
 */
class StatusListBindingFailClosedTest {
    private val issuerProperties =
        mapOf<String, Any>(
            "oid4vci.issuer.identifier" to "https://issuer.example.com",
            "oid4vci.issuer.credentialConfigurationIds" to "EuPid",
            "oid4vci.issuer.credentials.[EuPid].format" to "dc+sd-jwt",
            "oid4vci.issuer.credentials.[EuPid].statusListId" to "eupid-revocation",
        )

    private val statusListProperties =
        mapOf<String, Any>(
            "statuslists.ids" to "eupid-revocation",
            "statuslists.[eupid-revocation].uri" to "https://issuer.example.com/statuslists/eupid-revocation",
            "statuslists.[eupid-revocation].spec" to "token_status_list",
        )

    private fun provider(
        properties: Map<String, Any>,
        withDefinitionsSource: Boolean,
    ): ConfigDrivenOid4vciIssuerConfigProvider {
        val execution = TestSessionExecution(TestPrincipalConfigService(properties))
        val definitionsSource =
            if (withDefinitionsSource) {
                Provider<StatusListDefinitionsProvider> { ConfigDrivenStatusListDefinitionsProvider(execution) }
            } else {
                null
            }
        return ConfigDrivenOid4vciIssuerConfigProvider(execution, definitionsSource)
    }

    @Test
    fun declaredStatusListWithoutDefinitionsSourceFails() {
        // The credential config declares a status list, but no definitions source is wired
        // (status-list module absent from the deployment).
        val result = provider(issuerProperties, withDefinitionsSource = false).statusListBindingFor("EuPid")

        assertTrue(result.isErr, "a declared statusListId without a definitions source must fail resolution")
        assertEquals("STATUSLIST_BINDING_UNRESOLVABLE", result.error.code)
        val message = result.error.message.defaultMessage
        assertTrue("EuPid" in message, "the error must name the credential configuration: $message")
        assertTrue("eupid-revocation" in message, "the error must name the declared status list: $message")
    }

    @Test
    fun declaredStatusListWithUnknownListIdFails() {
        // Definitions source present, but it hosts no list with the declared id.
        val result = provider(issuerProperties, withDefinitionsSource = true).statusListBindingFor("EuPid")

        assertTrue(result.isErr, "a declared statusListId unknown to the definitions source must fail resolution")
        assertEquals("STATUSLIST_BINDING_UNRESOLVABLE", result.error.code)
        assertTrue("eupid-revocation" in result.error.message.defaultMessage)
    }

    @Test
    fun declaredStatusListResolvesAgainstHostedDefinition() {
        val result = provider(issuerProperties + statusListProperties, withDefinitionsSource = true).statusListBindingFor("EuPid")

        assertTrue(result.isOk, "a declared statusListId with a hosted definition must resolve")
        val binding = assertNotNull(result.value)
        assertEquals("eupid-revocation", binding.statusListCorrelationId)
        assertEquals(StatusListSpec.TOKEN_STATUS_LIST, binding.spec)
    }

    @Test
    fun configurationWithoutStatusListResolvesToNull() {
        val properties =
            mapOf<String, Any>(
                "oid4vci.issuer.identifier" to "https://issuer.example.com",
                "oid4vci.issuer.credentialConfigurationIds" to "EuPid",
                "oid4vci.issuer.credentials.[EuPid].format" to "dc+sd-jwt",
            )
        val result = provider(properties, withDefinitionsSource = false).statusListBindingFor("EuPid")

        assertTrue(result.isOk, "a configuration without a statusListId must keep issuing as before")
        assertNull(result.value)
    }

    @Test
    fun advertisedBindingsOmitUnresolvableEntries() {
        // The lenient map view (used for advertisement) exposes only resolvable bindings; the
        // fail-closed gate for issuance is statusListBindingFor.
        assertTrue(provider(issuerProperties, withDefinitionsSource = false).statusListBindings.isEmpty())
        assertEquals(
            setOf("EuPid"),
            provider(issuerProperties + statusListProperties, withDefinitionsSource = true).statusListBindings.keys,
        )
    }
}
