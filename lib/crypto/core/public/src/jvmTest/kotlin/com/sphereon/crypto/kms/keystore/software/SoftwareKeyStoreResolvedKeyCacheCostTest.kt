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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.runTest
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Quantifies what the per-instance resolved-key cache is worth.
 *
 * `SoftwareKeyStoreService.resolvedKeyCache` exists to avoid "expensive DER->JWK conversions and
 * certificate chain encoding", but it is a per-instance field and nothing keeps an instance alive
 * between operations: `RealSoftwareKeyStoreFactory` is an `@AssistedFactory` and
 * `KmsProviderManagerImpl` is deliberately stateless, so a provider lookup builds a fresh keystore
 * service every call and the cache always starts empty.
 *
 * This measures the same lookups two ways against one keystore file: a fresh service per lookup,
 * which is what production does today, and a single reused service, which is what surviving
 * instances would give. The underlying `KeyStore` is already shared through
 * `KeyStoreLoaderFactory`'s process-wide cache in both arms, so the difference isolates the
 * conversion cost alone.
 */
class SoftwareKeyStoreResolvedKeyCacheCostTest {
    private val keyCount = 12
    private val lookups = 40

    /** P-256 coordinates are fixed 32-byte fields; `BigInteger` drops leading zeros and may add a sign byte. */
    private fun BigInteger.toFixedWidth(): ByteArray {
        val raw = toByteArray()
        val stripped = if (raw.size > 32 && raw[0] == 0.toByte()) raw.copyOfRange(raw.size - 32, raw.size) else raw
        if (stripped.size == 32) return stripped
        return ByteArray(32).also { stripped.copyInto(it, 32 - stripped.size) }
    }

    private fun keyStoreConfig(path: String) =
        Pkcs12KeyStoreConfig(
            id = "resolved-key-cache-cost",
            password = "test-password",
            path = path,
            persist = true,
            overwriteAlias = true,
            keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
        )

    /**
     * Built from the key pair's coordinates rather than through `derPrivateKeyToJwk`, because a
     * PKCS#8 EC encoding may omit the optional public key component (RFC 5915 §3) and the resulting
     * JWK would carry no `x`.
     */
    private fun ecResolvedKeyInfo(alias: String): ResolvedKeyInfo<Jwk> {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val pair = generator.generateKeyPair()
        val public = pair.public as ECPublicKey
        val private = pair.private as ECPrivateKey
        val jwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = public.w.affineX.toFixedWidth().encodeToBase64Url(),
                y = public.w.affineY.toFixedWidth().encodeToBase64Url(),
                d = private.s.toFixedWidth().encodeToBase64Url(),
                kid = alias,
            )
        return ResolvedKeyInfo(
            key = jwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.EC,
            alias = alias,
            providerId = "resolved-key-cache-cost",
            kid = alias,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        )
    }

    @Test
    fun freshServicePerLookupRepeatsTheConversionsTheCacheExistsToAvoid() =
        runTest {
            val dir = Files.createTempDirectory("sks-resolved-key-cost").toFile()
            dir.deleteOnExit()
            val path = dir.resolve("keystore.p12").absolutePath
            val aliases = (0 until keyCount).map { "cost-key-$it" }

            val seed = SoftwareKeyStoreService(keyStoreConfig(path))
            aliases.forEach { alias -> seed.storeKey(ecResolvedKeyInfo(alias), "resolved-key-cache-cost", alias) }

            // Warm the process-wide keystore load so neither arm pays the PKCS12 unlock.
            SoftwareKeyStoreService(keyStoreConfig(path)).getKey(KeyInfo<Jwk>(alias = aliases.first()))

            val reused = SoftwareKeyStoreService(keyStoreConfig(path))
            val reusedStart = TimeSource.Monotonic.markNow()
            repeat(lookups) { index -> reused.getKey(KeyInfo<Jwk>(alias = aliases[index % keyCount])) }
            val reusedMillis = reusedStart.elapsedNow().inWholeMicroseconds / 1000.0

            val freshStart = TimeSource.Monotonic.markNow()
            repeat(lookups) { index ->
                SoftwareKeyStoreService(keyStoreConfig(path)).getKey(KeyInfo<Jwk>(alias = aliases[index % keyCount]))
            }
            val freshMillis = freshStart.elapsedNow().inWholeMicroseconds / 1000.0

            println(
                "VDX_RESOLVED_KEY_CACHE_COST lookups=$lookups keys=$keyCount " +
                    "fresh.total=${freshMillis}ms fresh.per=${freshMillis / lookups}ms " +
                    "reused.total=${reusedMillis}ms reused.per=${reusedMillis / lookups}ms",
            )

            // With the resolved-key state held app-level rather than per instance, a fresh service
            // per lookup must no longer repeat the conversions. Before the fix this arm cost about
            // three times the reused one; the bound is deliberately loose so the assertion tracks
            // the structural fix and not the timing of whatever machine runs it.
            assertTrue(
                freshMillis < reusedMillis * 2,
                "a fresh service per lookup must reuse the app-level resolved-key state; " +
                    "fresh=${freshMillis}ms reused=${reusedMillis}ms",
            )
        }

    @Test
    fun boundedStoreEvictsLeastRecentlyUsedOnceFull() =
        runTest {
            val store = BoundedTtlStore<String, String>(maxEntries = 2, ttlMillis = 60_000)
            store.put("a", "1")
            store.put("b", "2")
            store.get("a")
            store.put("c", "3")

            assertEquals(2, store.size())
            assertNotNull(store.get("a"), "the recently accessed entry must survive")
            assertNull(store.get("b"), "the least recently used entry must be evicted once the bound is reached")
            assertNotNull(store.get("c"))
        }

    @Test
    fun boundedStoreExpiresEntriesThatWentIdle() =
        runTest {
            val store = BoundedTtlStore<String, String>(maxEntries = 8, ttlMillis = 1)
            store.put("stale", "value")
            Thread.sleep(20)

            assertNull(store.get("stale"), "an entry idle beyond the ttl must not be served")
        }

    @Test
    fun eachTenantKeepsItsOwnResolvedKeyShard() =
        runTest {
            val root = Files.createTempDirectory("sks-resolved-key-shard").toFile()
            root.deleteOnExit()
            val alias = "shared-alias"

            val tenants =
                listOf("tenant-alpha", "tenant-beta").map { tenant ->
                    val path = root.resolve(tenant).also(java.io.File::mkdirs).resolve("keystore.p12").absolutePath
                    val service = SoftwareKeyStoreService(keyStoreConfig(path))
                    service.storeKey(ecResolvedKeyInfo(alias), "resolved-key-cache-cost", alias)
                    tenant to path
                }

            // Same alias in both keystores: the shards must stay distinct, so each tenant resolves
            // its own key material rather than the other tenant's entry left in a shared cache.
            val alphaMaterial = SoftwareKeyStoreService(keyStoreConfig(tenants[0].second)).getKey(KeyInfo<Jwk>(alias = alias))
            val betaMaterial = SoftwareKeyStoreService(keyStoreConfig(tenants[1].second)).getKey(KeyInfo<Jwk>(alias = alias))

            assertEquals(alias, alphaMaterial.alias)
            assertEquals(alias, betaMaterial.alias)
            assertNotEquals(
                (alphaMaterial.key as? Jwk)?.x,
                (betaMaterial.key as? Jwk)?.x,
                "two tenants storing the same alias must not share a resolved-key cache entry",
            )
        }
}
