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

package com.sphereon.identity.matching.impl.protection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.ProtectedIdentifierValue
import com.sphereon.identity.matching.protection.IdentifierProtectionPolicy
import com.sphereon.identity.matching.protection.IdentifierProtector
import com.sphereon.identity.matching.protection.NormalizationProfile
import com.sphereon.identity.matching.protection.normalizeIdentifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral-contract tests for [IdentifierProtector] using a self-contained pure-JVM
 * implementation built on real HMAC-SHA256 and AES-256-GCM (javax.crypto). This mirrors the
 * approach used by `ReconciliationCryptoServiceTest`: the KMS-backed production class delegates
 * the same primitives to the platform KMS, so validating the contract over real crypto here
 * proves the protection logic (normalization, domain-separated blind indexing, AAD-bound
 * encryption, blob layout) that [KmsBackedIdentifierProtector] implements.
 *
 * The reference implementation reuses the exact same building blocks the production class does:
 * - [normalizeIdentifier] for canonicalization,
 * - RFC 8785 [Jcs] canonicalization for the deterministic blind-index message and the AAD,
 * - the iv(12) | authTag(16) | ciphertext Base64Url blob layout.
 */
@OptIn(ExperimentalEncodingApi::class)
class KmsBackedIdentifierProtectorTest {
    /**
     * Pure-JVM [IdentifierProtector] mirroring [KmsBackedIdentifierProtector]. HMAC keys are
     * domain-separated per blind-index key alias so different tenants/aliases never collide.
     */
    private class JvmIdentifierProtector(
        private val hmacKeyBytes: ByteArray,
        private val aesKey: ByteArray,
        private val keyVersion: String = "v1",
    ) : IdentifierProtector {
        private val random = SecureRandom()

        private fun biKeyRef(tenantId: String) = "idfr:bi:$tenantId"

        private fun encKeyRef(tenantId: String) = "idfr:enc:$tenantId"

        private fun hmac(
            keyId: String,
            message: ByteArray,
        ): String {
            val mac = Mac.getInstance("HmacSHA256")
            // Bind keyId into the key material to emulate per-alias KMS keys (domain separation).
            mac.init(SecretKeySpec(hmacKeyBytes + keyId.encodeToByteArray(), "HmacSHA256"))
            val digest = mac.doFinal(message)
            return "f" + digest.joinToString("") { "%02x".format(it) }
        }

        private fun blindIndexMessage(
            tenantId: String,
            type: IdentifierType,
            scope: String,
            normalized: String,
        ): ByteArray =
            Jcs.canonicalize(
                JsonObject(
                    mapOf(
                        "purpose" to JsonPrimitive("identifier-bi"),
                        "tenant_id" to JsonPrimitive(tenantId),
                        "type" to JsonPrimitive(type.value),
                        "scope" to JsonPrimitive(scope),
                        "value" to JsonPrimitive(normalized),
                    ),
                ),
            )

        private fun aad(
            tenantId: String,
            identityId: String?,
            type: IdentifierType,
        ): ByteArray =
            Jcs.canonicalize(
                JsonObject(
                    mapOf(
                        "v" to JsonPrimitive(1),
                        "tenant_id" to JsonPrimitive(tenantId),
                        "purpose" to JsonPrimitive("identifier-enc"),
                        "identity_id" to JsonPrimitive(identityId ?: ""),
                        "type" to JsonPrimitive(type.value),
                    ),
                ),
            )

        private fun computeBi(
            tenantId: String,
            type: IdentifierType,
            scope: String,
            normalized: String,
        ): String = hmac(biKeyRef(tenantId), blindIndexMessage(tenantId, type, scope, normalized))

        private fun encryptBlob(
            tenantId: String,
            identityId: String?,
            type: IdentifierType,
            normalized: String,
        ): String {
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(tenantId, identityId, type))
            val out = cipher.doFinal(normalized.encodeToByteArray())
            val authTag = out.copyOfRange(out.size - 16, out.size)
            val ciphertext = out.copyOfRange(0, out.size - 16)
            return Base64.UrlSafe.encode(iv + authTag + ciphertext)
        }

        override suspend fun protect(
            tenantId: String,
            identityId: String?,
            type: IdentifierType,
            plaintext: String,
            policy: IdentifierProtectionPolicy,
            salt: ByteArray?,
        ): IdkResult<ProtectedIdentifierValue, IdkError> {
            val normalized = normalizeIdentifier(plaintext, policy.normalization)
            return when (policy.mode) {
                IdentifierProtectionMode.PLAINTEXT -> {
                    Ok(ProtectedIdentifierValue(mode = IdentifierProtectionMode.PLAINTEXT, plaintext = normalized))
                }

                IdentifierProtectionMode.SEARCHABLE_ENCRYPTED,
                IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX -> {
                    Ok(blinded(tenantId, identityId, type, normalized, policy.mode, "tenant"))
                }

                IdentifierProtectionMode.SALTED_BLINDED -> {
                    val s = salt ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "salt required"))
                    Ok(blinded(tenantId, identityId, type, normalized, IdentifierProtectionMode.SALTED_BLINDED, Base64.encode(s)))
                }
            }
        }

        private fun blinded(
            tenantId: String,
            identityId: String?,
            type: IdentifierType,
            normalized: String,
            mode: IdentifierProtectionMode,
            scope: String,
        ): ProtectedIdentifierValue =
            ProtectedIdentifierValue(
                mode = mode,
                valueCiphertext = encryptBlob(tenantId, identityId, type, normalized),
                encKeyRef = encKeyRef(tenantId),
                encKeyVersion = keyVersion,
                valueHmac = computeBi(tenantId, type, scope, normalized),
                hmacKeyRef = biKeyRef(tenantId),
                hmacKeyVersion = keyVersion,
            )

        override suspend fun blindIndex(
            tenantId: String,
            type: IdentifierType,
            plaintext: String,
            policy: IdentifierProtectionPolicy,
            salt: ByteArray?,
        ): IdkResult<String, IdkError> {
            val normalized = normalizeIdentifier(plaintext, policy.normalization)
            val scope =
                if (policy.mode == IdentifierProtectionMode.SALTED_BLINDED) {
                    val s = salt ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "salt required"))
                    Base64.encode(s)
                } else {
                    "tenant"
                }
            return Ok(computeBi(tenantId, type, scope, normalized))
        }

        override suspend fun reveal(
            protected: ProtectedIdentifierValue,
            tenantId: String,
            identityId: String?,
            type: IdentifierType,
        ): IdkResult<String, IdkError> {
            val blob = protected.valueCiphertext ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "no ciphertext"))
            val combined = Base64.UrlSafe.decode(blob)
            val iv = combined.copyOfRange(0, 12)
            val authTag = combined.copyOfRange(12, 28)
            val ciphertext = combined.copyOfRange(28, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(tenantId, identityId, type))
            return Ok(cipher.doFinal(ciphertext + authTag).decodeToString())
        }
    }

    private fun newProtector(): IdentifierProtector {
        val hmacKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val aesKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return JvmIdentifierProtector(hmacKey, aesKey)
    }

    private val emailPolicy =
        IdentifierProtectionPolicy(
            identifierType = IdentifierType("email"),
            mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
            normalization = NormalizationProfile.EMAIL,
        )

    private val saltedPolicy =
        IdentifierProtectionPolicy(
            identifierType = IdentifierType("email"),
            mode = IdentifierProtectionMode.SALTED_BLINDED,
            normalization = NormalizationProfile.EMAIL,
        )

    private val plaintextPolicy =
        IdentifierProtectionPolicy(
            identifierType = IdentifierType("issuer"),
            mode = IdentifierProtectionMode.PLAINTEXT,
            normalization = NormalizationProfile.URL_HOST,
        )

    @Test
    fun searchable_protectThenBlindIndex_yieldsSameHmac() =
        runTest {
            val protector = newProtector()
            val tenant = "tenant-a"
            val type = IdentifierType("email")

            val protected =
                protector
                    .protect(tenant, "identity-1", type, "  Alice@Example.com ", emailPolicy)
                    .getOrNull() ?: error("protect failed")

            // Same logical value (different casing/whitespace) yields the SAME blind index.
            val index =
                protector
                    .blindIndex(tenant, type, "alice@example.com", emailPolicy)
                    .getOrNull() ?: error("blindIndex failed")

            assertEquals(
                protected.valueHmac,
                index,
                "blindIndex of the normalized value must equal the stored valueHmac (searchable determinism)",
            )
            assertEquals(IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX, protected.mode)
            assertEquals("idfr:bi:$tenant", protected.hmacKeyRef)
            assertEquals("idfr:enc:$tenant", protected.encKeyRef)
        }

    @Test
    fun searchable_revealReturnsNormalizedOriginal() =
        runTest {
            val protector = newProtector()
            val tenant = "tenant-a"
            val type = IdentifierType("email")

            val protected =
                protector
                    .protect(tenant, "identity-1", type, "Alice@Example.com", emailPolicy)
                    .getOrNull() ?: error("protect failed")

            val revealed =
                protector
                    .reveal(protected, tenant, "identity-1", type)
                    .getOrNull() ?: error("reveal failed")

            assertEquals("alice@example.com", revealed, "reveal must return the normalized plaintext")
        }

    @Test
    fun salted_differentSalts_yieldDifferentHmacForSameValue() =
        runTest {
            val protector = newProtector()
            val tenant = "tenant-a"
            val type = IdentifierType("email")
            val saltA = ByteArray(16) { 1 }
            val saltB = ByteArray(16) { 2 }

            val a =
                protector
                    .protect(tenant, "identity-A", type, "shared@example.com", saltedPolicy, saltA)
                    .getOrNull() ?: error("protect A failed")
            val b =
                protector
                    .protect(tenant, "identity-B", type, "shared@example.com", saltedPolicy, saltB)
                    .getOrNull() ?: error("protect B failed")

            assertNotEquals(
                a.valueHmac,
                b.valueHmac,
                "Same value with different salts must produce different blind indexes (no cross-identity correlation)",
            )

            // Same salt reproduces the same blind index (a salted value stays discoverable when its salt is known).
            val reindexA =
                protector
                    .blindIndex(tenant, type, "shared@example.com", saltedPolicy, saltA)
                    .getOrNull() ?: error("blindIndex A failed")
            assertEquals(a.valueHmac, reindexA, "Same salt must reproduce the same blind index")
        }

    @Test
    fun salted_missingSalt_returnsError() =
        runTest {
            val protector = newProtector()
            val result =
                protector.protect("tenant-a", "identity-1", IdentifierType("email"), "x@example.com", saltedPolicy, salt = null)
            assertTrue(result.isErr, "SALTED_BLINDED without a salt must fail")
        }

    @Test
    fun plaintext_storesPlaintextAndNoHmacOrCiphertext() =
        runTest {
            val protector = newProtector()
            val protected =
                protector
                    .protect("tenant-a", null, IdentifierType("issuer"), "https://issuer.example.com/", plaintextPolicy)
                    .getOrNull() ?: error("protect failed")

            assertEquals(IdentifierProtectionMode.PLAINTEXT, protected.mode)
            assertEquals("https://issuer.example.com", protected.plaintext, "URL_HOST normalization strips the trailing slash")
            assertNull(protected.valueHmac, "PLAINTEXT mode must not produce a blind index")
            assertNull(protected.valueCiphertext, "PLAINTEXT mode must not produce ciphertext")
        }
}
