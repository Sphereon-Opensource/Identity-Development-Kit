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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.impl.testutil.createDidManagerTestAppGraph
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E integration test proving did:webvh is wired into the DID manager via the
 * [com.sphereon.did.manager.ManagedDidGenerator] SPI: `create(method = "webvh")` is routed to the
 * webvh provider's generator (instead of the generic — and unsupported — `DidProvider.create`), the
 * genesis log entry is signed (eddsa-jcs-2022) and SCID-computed, and the resulting current-state
 * document is persisted by the manager through its normal chokepoint so it becomes hostable.
 *
 * Uses the full real DI stack (software KMS, in-memory DID repository, the webvh provider, and the
 * Data Integrity cryptosuite) — no fakes — so this exercises the actual merged graph including the
 * populated `Set<ManagedDidGenerator>` multibinding.
 */
class DidWebvhCreationDslE2ETest {
    private lateinit var app: com.sphereon.di.app.AppGraph
    private lateinit var dslProcessor: DidCreationDslProcessor
    private lateinit var didManager: DidManager

    @AfterTest
    fun tearDown() {
        if (::app.isInitialized) {
            app.userContextManager.destroyAll()
        }
    }

    @BeforeTest
    fun setUp() {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.softwaretest.type" to "software",
                "kms.providers.softwaretest.id" to "softwaretest",
                "kms.providers.softwaretest.keystore.type" to "memory",
                "kms.providers.softwaretest.keystore.id" to "test-memory-keystore",
                "kms.providers.softwaretest.keystore.keyVisibility" to "private",
                "kms.providers.softwaretest.keystore.overwriteAlias" to "true",
            ),
        )

        app = createDidManagerTestAppGraph(testInstance = this)
        app.userContextManager.destroyAll()

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("did-webvh-dsl-e2e-test", principalType = com.sphereon.di.context.PrincipalType.USER)
        val sessionGraph = session.graph

        dslProcessor = (sessionGraph as DidCreationDslProcessorImpl.Graph).didCreationDslProcessor
        didManager = (sessionGraph as DidManagerServiceImpl.Graph).didManager
    }

    @Test
    fun testCreateDidWebvhRoutesThroughManagedGeneratorAndPersists() =
        runTest {
            // The webvh genesis proof is eddsa-jcs-2022 (Ed25519); the generated key doubles as the
            // genesis update key. The manager derives updateKeyRefs from this VM's KMS alias.
            val result =
                dslProcessor.create {
                    method("webvh")
                    domain("example.com")
                    alias("test-did-webvh")

                    autoGenerateKey {
                        keyType(KeyTypeMapping.OKP)
                        curve(Curve.Ed25519)
                        kmsProvider("softwaretest")
                        purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
                    }
                }

            assertTrue(result.isOk, "did:webvh creation through the manager should succeed: ${result.getErrorOrNull()}")
            val managedDid = result.getOrThrow()

            // SCID-bearing did:webvh identifier resolving at example.com.
            assertTrue(
                managedDid.did.startsWith("did:webvh:"),
                "DID should be a did:webvh, was '${managedDid.did}'",
            )
            assertTrue(
                managedDid.did.endsWith(":example.com"),
                "did:webvh should resolve at example.com, was '${managedDid.did}'",
            )
            assertEquals("webvh", managedDid.method, "method should be webvh")
            assertEquals("test-did-webvh", managedDid.alias, "Alias should match")

            // Genesis document carries the Multikey VM for the update key.
            val document = managedDid.document
            assertNotNull(document, "DID document should be present")
            val vms = document.verificationMethod
            assertNotNull(vms, "Verification methods should be present")
            assertEquals(1, vms.size, "Should have 1 verification method (the update key)")
            assertEquals(
                VerificationMethodType.MULTIKEY.value,
                vms[0].type,
                "webvh update key VM must be a Multikey",
            )
            assertNotNull(vms[0].publicKeyMultibase, "Multikey VM must carry publicKeyMultibase")

            // The manager bound the VM to its KMS key so the webvh DID is signable later.
            assertEquals(1, managedDid.keys.size, "Should have 1 KMS key mapping")

            // Persisted by the manager (current state) -> hostable via the companion did:json.
            val persisted = didManager.get(managedDid.did).getOrThrow()
            assertNotNull(persisted, "did:webvh should be retrievable from the manager")
            assertEquals(managedDid.did, persisted.did)
            assertEquals(1, persisted.keys.size, "Persisted did:webvh should keep its KMS key mapping")
            assertEquals(
                1,
                persisted.document?.verificationMethod?.size,
                "Persisted did:webvh should keep its Multikey VM",
            )
        }
}
