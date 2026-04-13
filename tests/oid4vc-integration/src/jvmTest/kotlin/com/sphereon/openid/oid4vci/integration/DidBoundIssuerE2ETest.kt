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
import com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests that exercise the real resolver + real DID provider + real KMS
 * with runtime-generated signing keys.
 *
 * Coverage in this file:
 *   - Test 1: canonical did:jwk as the signing-key verification-method URL
 *   - Test 3: canonical DIDs are structurally equal to the JWK we publish
 *   - Test 6: multiple configured aliases each resolve to their own canonical DID
 *
 * **Not covered here** (requires a full format-handler fixture — see
 * `jwt-issuer-hosting-oid4vci.md` follow-ups):
 *   - Test 2: JWT-header kid byte-identical to JWKS-entry kid from the same issuance
 *   - Test 4: cnf.jwk matches proof key and sub is the holder DID, not the client_id
 *   - Test 8: signature verification round-trip using the published JWKS entry
 */
class DidBoundIssuerE2ETest {
    private val ctx = Oid4vciTestContext(this)
    private val json = Json { ignoreUnknownKeys = true }

    private val publicMembers = setOf("kty", "crv", "x", "y", "n", "e")

    @OptIn(ExperimentalEncodingApi::class)
    private fun didJwkEmbedded(didUrl: String): JsonObject {
        assertTrue(didUrl.startsWith("did:jwk:"), "Expected did:jwk URL, got: $didUrl")
        val b64 = didUrl.removePrefix("did:jwk:").substringBefore('#').substringBefore('?')
        val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
        return json.parseToJsonElement(Base64.UrlSafe.decode(padded).decodeToString()) as JsonObject
    }

    // -------------------------------------------------------------------
    // Test 1 + 3 — canonical did:jwk derived from the configured signing key
    // -------------------------------------------------------------------

    @Test
    fun resolverProducesCanonicalDidJwkForConfiguredAlias() =
        runTest {
            val graph = ctx.session.graph as IssuerKeyIdResolverTestGraph
            val resolver: IssuerKeyIdResolver = graph.issuerKeyIdResolver
            val kms =
                ctx.session.graph
                    .asKeyManagerServiceGraph()
                    .keyManagerService

            assertTrue(
                kms
                    .generateKeyResult(
                        alias = "test-credential-signing",
                        use = JwkUse.sig,
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                    ).isOk,
            )

            val vmUrl =
                resolver
                    .resolveDidVerificationMethodId("test-credential-signing", "jwk")
                    .getOrThrow()

            // Test 1: the kid the issuer would write into a JWT header IS a canonical did:jwk VM URL.
            assertTrue(vmUrl.startsWith("did:jwk:"))
            assertTrue(vmUrl.contains('#'), "Verification-method URL must have a fragment: $vmUrl")

            val embedded = didJwkEmbedded(vmUrl)
            assertTrue(
                embedded.keys.all { it in publicMembers },
                "did:jwk-embedded JWK must contain only public-key members; got: ${embedded.keys}",
            )
            assertTrue("kid" !in embedded, "No kid inside the did:jwk-embedded JWK")
            assertTrue("alg" !in embedded, "No alg inside the did:jwk-embedded JWK")
            assertTrue("use" !in embedded, "No use inside the did:jwk-embedded JWK")

            // Test 3: the JWK the issuer would publish in its JWKS structurally matches
            // the JWK embedded in the did:jwk DID from the same alias.
            val published = resolver.resolvePublicJwk("test-credential-signing").getOrThrow()
            assertEquals(
                embedded,
                published,
                "Published JWK and DID-embedded JWK must be structurally identical — " +
                    "otherwise a wallet resolving the DID gets different key material than " +
                    "the key advertised in /.well-known/jwt-vc-issuer.",
            )
            // kty asserted explicitly to protect against a silent regression where both
            // sides agree on an "empty" JWK.
            assertEquals("EC", published["kty"]?.jsonPrimitive?.content)
        }

    // -------------------------------------------------------------------
    // Test 6 — every configured signing alias has its own canonical DID
    // -------------------------------------------------------------------

    @Test
    fun multipleAliasesEachProduceTheirOwnCanonicalDid() =
        runTest {
            val graph = ctx.session.graph as IssuerKeyIdResolverTestGraph
            val resolver = graph.issuerKeyIdResolver
            val kms =
                ctx.session.graph
                    .asKeyManagerServiceGraph()
                    .keyManagerService

            val aliases = listOf("TestCredential", "PID", "AgeOver18")
            aliases.forEach { alias ->
                assertTrue(
                    kms
                        .generateKeyResult(
                            alias = alias,
                            use = JwkUse.sig,
                            alg = SignatureAlgorithm.ECDSA_SHA256,
                        ).isOk,
                    "Key generation for $alias should succeed",
                )
            }

            val vmUrls =
                aliases.associateWith { alias ->
                    resolver.resolveDidVerificationMethodId(alias, "jwk").getOrThrow()
                }

            // Every alias yields a valid did:jwk VM URL.
            vmUrls.forEach { (alias, url) ->
                assertTrue(url.startsWith("did:jwk:"), "Alias $alias did not produce a did:jwk URL")
                val embedded = didJwkEmbedded(url)
                assertTrue(
                    embedded.keys.all { it in publicMembers },
                    "Alias $alias produced a non-canonical did:jwk (members: ${embedded.keys})",
                )
            }

            // They're all distinct.
            val distinct = vmUrls.values.toSet()
            assertEquals(
                aliases.size,
                distinct.size,
                "Distinct signing aliases must produce distinct DIDs — collisions would make the JWKS ambiguous.",
            )

            // The resolver is deterministic: asking again for the same alias yields the same URL.
            aliases.forEach { alias ->
                val again = resolver.resolveDidVerificationMethodId(alias, "jwk").getOrThrow()
                assertEquals(vmUrls[alias], again, "Non-deterministic resolver for $alias")
            }
        }
}
