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

package com.sphereon.openid.oid4vci.integration

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Graph accessor so the test can resolve [IssuerKeyIdResolver] from the SessionScope.
 */
@ContributesTo(SessionScope::class)
interface IssuerKeyIdResolverTestGraph {
    val issuerKeyIdResolver: IssuerKeyIdResolver
}

/**
 * Locks in the shared-resolver contract for DID-bound signing keys:
 *
 * - The resolver is **deterministic**: the same (alias, didMethod) pair yields the
 *   same verification-method URL and the same canonical public-JWK on every call.
 *   This is the guarantee that the JWT-header `kid` the credential handler writes
 *   and the JWKS-entry `kid` the well-known endpoint publishes can never diverge.
 *
 * - The resolver honours the did:jwk canonicalisation: identical public key material
 *   from two separate KMS generations (= different alias) yields equal DIDs only
 *   because the KMS never generates the same material twice. Conversely, two
 *   different keys yield two different DIDs. We assert the latter to pin the
 *   direction of the contract.
 */
class IssuerKeyIdResolverE2ETest {
    private val ctx = Oid4vciTestContext(this)

    @Test
    fun resolverIsDeterministicForTheSameAlias() =
        runTest {
            val graph = ctx.session.graph as IssuerKeyIdResolverTestGraph
            val resolver = graph.issuerKeyIdResolver
            val kms =
                ctx.session.graph
                    .asKeyManagerServiceGraph()
                    .keyManagerService

            val keyResult =
                kms.generateKeyResult(
                    alias = "resolver-determinism-key",
                    use = JwkUse.sig,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertTrue(keyResult.isOk, "Key generation should succeed")

            val first =
                resolver
                    .resolveDidVerificationMethodId("resolver-determinism-key", "jwk")
                    .getOrThrow()
            val second =
                resolver
                    .resolveDidVerificationMethodId("resolver-determinism-key", "jwk")
                    .getOrThrow()

            assertEquals(
                first,
                second,
                "resolveDidVerificationMethodId MUST be deterministic for the same alias — " +
                    "otherwise JWT-header kid and JWKS-entry kid will not match.",
            )
            assertTrue(first.startsWith("did:jwk:"), "Expected did:jwk URL, got: $first")
            assertTrue(first.contains('#'), "Verification-method URL must contain a fragment: $first")
        }

    @Test
    fun resolverReturnsDistinctDidsForDistinctKeys() =
        runTest {
            val graph = ctx.session.graph as IssuerKeyIdResolverTestGraph
            val resolver = graph.issuerKeyIdResolver
            val kms =
                ctx.session.graph
                    .asKeyManagerServiceGraph()
                    .keyManagerService

            assertTrue(
                kms
                    .generateKeyResult(
                        alias = "resolver-key-A",
                        use = JwkUse.sig,
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    ).isOk,
            )
            assertTrue(
                kms
                    .generateKeyResult(
                        alias = "resolver-key-B",
                        use = JwkUse.sig,
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    ).isOk,
            )

            val a = resolver.resolveDidVerificationMethodId("resolver-key-A", "jwk").getOrThrow()
            val b = resolver.resolveDidVerificationMethodId("resolver-key-B", "jwk").getOrThrow()
            assertNotEquals(a, b, "Different signing keys must produce different did:jwk URLs")
        }

    @Test
    fun resolvePublicJwkReturnsOnlyPublicMembers() =
        runTest {
            val graph = ctx.session.graph as IssuerKeyIdResolverTestGraph
            val resolver = graph.issuerKeyIdResolver
            val kms =
                ctx.session.graph
                    .asKeyManagerServiceGraph()
                    .keyManagerService
            assertTrue(
                kms
                    .generateKeyResult(
                        alias = "resolver-public-members-key",
                        use = JwkUse.sig,
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    ).isOk,
            )

            val jwk = resolver.resolvePublicJwk("resolver-public-members-key").getOrThrow()
            val allowed = setOf("kty", "crv", "x", "y", "n", "e")
            val leaks = jwk.keys - allowed
            assertTrue(
                leaks.isEmpty(),
                "Public JWK must only expose key-material members; leaked: $leaks",
            )
            assertTrue("kty" in jwk.keys)
        }
}
