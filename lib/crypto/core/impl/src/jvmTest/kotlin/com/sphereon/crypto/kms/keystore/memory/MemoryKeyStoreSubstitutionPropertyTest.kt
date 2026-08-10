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

package com.sphereon.crypto.kms.keystore.memory

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Property-based regression guard for the [MemoryKeyStore] substitution bug (architecture review
 * §H.B4).
 *
 * The historical bug: when a caller asked the resolver to look up a key it had supplied verbatim,
 * the resolver checked `storedKey.shape == callerSuppliedKey.shape`; both were `null` for the
 * shape field, the equality passed, and an unrelated stored key was substituted for the caller's
 * key. The fix gates the EC-coordinate fallback on supplied AND stored keys both having non-null
 * x/y coordinates, and gates the kid-collision path on `isSameKeyMaterial(supplied, stored)`.
 *
 * This suite asserts the invariant across many random keypairs:
 *
 *  1. RSA-2048: 25 keypairs. For each, the store contains an UNRELATED RSA-2048 keypair under a
 *     deterministic alias. The resolver is asked for the caller-supplied key WITHOUT an alias,
 *     and via a kid that COLLIDES with the stored key. The resolver MUST refuse rather than
 *     substituting the stored key, because the supplied JWK's modulus differs from the stored
 *     key's modulus and `isSameKeyMaterial` must reject the collision.
 *
 *  2. EC P-256: 25 keypairs. Same shape, but exercises the EC-coordinate fallback specifically.
 *     A bug regression to `null == null` matching would surface as silent substitution of the
 *     stored key (different x/y) for the caller's request.
 *
 *  3. Empty-store negative: even with an empty store, requesting an arbitrary RSA JWK without an
 *     alias must error rather than fabricating a match. Guards against a future "use caller-
 *     supplied verbatim" code path being added without an explicit opt-in.
 *
 * The iteration count (25) is a deliberate trade-off vs unit-test wall-clock time: each RSA-2048
 * keypair generation costs roughly 30ms on commodity x86-64 hardware, so 25 keeps the suite
 * sub-second on a hot JVM. Bumping to 100 surfaces the same regression with greater confidence
 * but pushes the wall-clock past 5 seconds.
 */
class MemoryKeyStoreSubstitutionPropertyTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var memoryKeyStore: KeyStore

    private val app = createJvmCryptoTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("memory-keystore-substitution-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "substitution-property-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as JvmCryptoTestAppGraph
        val softwareKmsProvider =
            app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)
        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        val memoryConfig =
            MemoryKeyStoreConfig(
                id = "substitution-property-keystore",
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                overwriteAlias = true,
                scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
            )
        memoryKeyStore = app.memoryKeyStore.create(memoryConfig, session.asCoreApiServiceGraph().serviceExecution)
    }

    @Test
    fun rsaKeystoreNeverSubstitutesAnUnrelatedStoredKeyForACallerSuppliedJwk() =
        runTest {
            repeat(RSA_ITERATIONS) { iteration ->
                // Stored key: KMS-generated, deterministic alias, kid distinct per iteration.
                val stored =
                    keyManagerService
                        .generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
                        .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
                val storedAlias = "substitution-rsa-stored-$iteration"
                val storedKid = stored.kid ?: error("KMS RSA key must carry a kid")
                memoryKeyStore.storeKey(stored, "test-provider", storedAlias, null)

                // Caller-supplied JWK: NEVER stored, but its kid COLLIDES with the stored kid.
                // If `isSameKeyMaterial` reverts to a shape-only check, the collision combined
                // with both keys having `null` for the shape field would silently match.
                val callerJwk = generateRsaPublicJwk(kid = storedKid)
                val callerLookup =
                    com.sphereon.crypto.core.ResolvedKeyInfo(
                        key = callerJwk,
                        kid = storedKid,
                        alias = null,
                        keyVisibility = KeyVisibility.PUBLIC,
                    )

                // Substitution would silently return the stored key. The fix raises so the caller
                // is forced to be explicit. Either outcome is acceptable for the regression
                // guard — what is NOT acceptable is the resolver returning the STORED key.
                assertFails(
                    "iteration=$iteration: resolver MUST NOT substitute the stored RSA key when the " +
                        "caller-supplied JWK has the same kid but different modulus",
                ) {
                    memoryKeyStore.getKey(callerLookup)
                }
            }
        }

    @Test
    fun ecKeystoreNeverSubstitutesAnUnrelatedStoredKeyForACallerSuppliedJwk() =
        runTest {
            repeat(EC_ITERATIONS) { iteration ->
                val stored =
                    keyManagerService
                        .generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
                        .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
                val storedAlias = "substitution-ec-stored-$iteration"
                val storedKid = stored.kid ?: error("KMS EC key must carry a kid")
                memoryKeyStore.storeKey(stored, "test-provider", storedAlias, null)

                val callerJwk = generateEcPublicJwkP256(kid = storedKid)
                val callerLookup =
                    com.sphereon.crypto.core.ResolvedKeyInfo(
                        key = callerJwk,
                        kid = storedKid,
                        alias = null,
                        keyVisibility = KeyVisibility.PUBLIC,
                    )

                assertFails(
                    "iteration=$iteration: resolver MUST NOT substitute the stored EC key when the " +
                        "caller-supplied JWK has the same kid but different (x, y) coordinates",
                ) {
                    memoryKeyStore.getKey(callerLookup)
                }
            }
        }

    @Test
    fun emptyKeystoreCannotFabricateAMatchForACallerSuppliedJwk() =
        runTest {
            // Sanity: with NO stored keys, an aliasless caller-supplied JWK must error out, not
            // silently echo the supplied key back. The legacy substitution bug surfaced first as
            // a `null == null` shape match on an empty store.
            repeat(SMALL_ITERATIONS) { iteration ->
                val callerJwk = generateRsaPublicJwk(kid = "empty-store-rsa-$iteration")
                val callerLookup =
                    com.sphereon.crypto.core.ResolvedKeyInfo(
                        key = callerJwk,
                        kid = callerJwk.kid,
                        alias = null,
                        keyVisibility = KeyVisibility.PUBLIC,
                    )
                assertFails("iteration=$iteration: empty keystore must not silently echo a caller-supplied JWK") {
                    memoryKeyStore.getKey(callerLookup)
                }
            }
        }

    @Test
    fun keystoreReturnsTheCorrectStoredKeyByAlias() =
        runTest {
            // Positive control: the resolver MUST find a stored key by alias regardless of how
            // many other keys share the keystore. Without this, the substitution-resistance test
            // could pass via "resolver always errors" rather than "resolver discriminates".
            val storedFirst =
                keyManagerService
                    .generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
                    .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val storedSecond =
                keyManagerService
                    .generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
                    .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val firstStored = memoryKeyStore.storeKey(storedFirst, "test-provider", "positive-rsa", null)
            val secondStored = memoryKeyStore.storeKey(storedSecond, "test-provider", "positive-ec", null)

            val firstFetched = memoryKeyStore.getKey(firstStored)
            val secondFetched = memoryKeyStore.getKey(secondStored)
            assertEquals("positive-rsa", firstFetched.alias)
            assertEquals("positive-ec", secondFetched.alias)
            // Kid round-trips: a substitution would surface as a kid mismatch here.
            assertEquals(firstStored.kid, firstFetched.kid)
            assertEquals(secondStored.kid, secondFetched.kid)
        }

    private fun generateRsaPublicJwk(kid: String): Jwk {
        val kp =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(RSA_2048, SecureRandom()) }
                .generateKeyPair()
        val pub = kp.public as RSAPublicKey
        return Jwk(
            kty = JwaKeyType.RSA,
            alg = JwaAlgorithm.fromValue("RS256"),
            use = "sig",
            kid = kid,
            n = b64Url(stripLeadingZero(pub.modulus.toByteArray())),
            e = b64Url(stripLeadingZero(pub.publicExponent.toByteArray())),
        )
    }

    private fun generateEcPublicJwkP256(kid: String): Jwk {
        val kp =
            KeyPairGenerator
                .getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }
                .generateKeyPair()
        val pub = kp.public as ECPublicKey
        // RFC 7518 §6.2.1 requires fixed-length 32-byte big-endian x and y for P-256. JCA returns
        // a sign-extended BigInteger, so left-pad with zeros after stripping any leading sign byte.
        val x = leftPad(stripLeadingZero(pub.w.affineX.toByteArray()), EC_P256_COORD_BYTES)
        val y = leftPad(stripLeadingZero(pub.w.affineY.toByteArray()), EC_P256_COORD_BYTES)
        return Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            alg = JwaAlgorithm.fromValue("ES256"),
            use = "sig",
            kid = kid,
            x = b64Url(x),
            y = b64Url(y),
        )
    }

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

    private fun leftPad(
        bytes: ByteArray,
        target: Int,
    ): ByteArray =
        if (bytes.size >= target) {
            bytes
        } else {
            ByteArray(target).also { padded ->
                System.arraycopy(bytes, 0, padded, target - bytes.size, bytes.size)
            }
        }

    private fun b64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        private const val RSA_2048 = 2048
        private const val EC_P256_COORD_BYTES = 32
        private const val RSA_ITERATIONS = 25
        private const val EC_ITERATIONS = 25
        private const val SMALL_ITERATIONS = 10
    }
}
