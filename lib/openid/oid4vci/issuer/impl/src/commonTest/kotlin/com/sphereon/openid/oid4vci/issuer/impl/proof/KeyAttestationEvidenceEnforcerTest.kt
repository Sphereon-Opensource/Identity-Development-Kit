/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyAttestationEvidenceEnforcerTest {
    private val enforcer = KeyAttestationEvidenceEnforcer { NOW }
    private val policy =
        KeyAttestationsRequired(
            keyStorage = listOf("ISO_18045_HIGH"),
            userAuthentication = listOf("high"),
        )

    @Test
    fun acceptsPersistedWalletUnitEvidence() {
        val result =
            enforcer.enforce(
                header = header(),
                claims = claims(),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isOk, "expected valid evidence but got Err: ${result.errorOrNull()}")
        val evidence = result.value!!
        assertEquals("https://status.example/ka", evidence.keyStorageStatusListUri)
        assertEquals("2", evidence.keyStorageStatusIndex)
        assertEquals(NOW + 600, evidence.keyStorageStatusMaintenanceExpiresAtEpochSeconds)
        assertEquals(listOf("ISO_18045_HIGH", "REMOTE_WSCD", "non_exportable"), evidence.keyStorage)
        assertEquals(listOf("high", "pin"), evidence.userAuthentication)
    }

    @Test
    fun rejectsMissingPersistedWalletUnitEvidence() {
        val result =
            enforcer.enforce(
                header = header(),
                claims = claims(includeKeyStorage = false),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isErr, "expected missing key_storage to fail")
        assertTrue("key_storage evidence" in result.error.message.defaultMessage)
    }

    @Test
    fun rejectsRevokedKeyStorageStatus() {
        val result =
            enforcer.enforce(
                header = header(),
                claims = claims(statusRevoked = true),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isErr, "expected revoked status to fail")
        assertTrue("revoked" in result.error.message.defaultMessage)
    }

    @Test
    fun rejectsUntrustedWalletProviderSignatureEvidence() {
        val result =
            enforcer.enforce(
                header = header(x5c = false),
                claims = claims(),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isErr, "expected missing x5c to fail")
        assertTrue("x5c" in result.error.message.defaultMessage)
    }

    @Test
    fun rejectsLocalAndTestEvidence() {
        val result =
            enforcer.enforce(
                header = header(),
                claims = claims(profile = "LOCAL_EVALUATION_REFERENCE", signerProfile = "LOCAL_EVALUATION", production = false),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isErr, "expected local/evaluation evidence to fail")
        assertTrue("LOCAL_EVALUATION" in result.error.message.defaultMessage || "local" in result.error.message.defaultMessage)
    }

    @Test
    fun rejectsUnsupportedStorageAndUserAuthenticationEvidence() {
        val result =
            enforcer.enforce(
                header = header(),
                claims = claims(secureComponent = "REMOTE_HSM", userAuthenticationAssurance = "pin_test"),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isErr, "expected unsupported storage/authentication evidence to fail")
        assertTrue("secure_component" in result.error.message.defaultMessage || "user_authentication" in result.error.message.defaultMessage)
    }

    @Test
    fun rejectsWebAuthnPrfProfileInflationAsRemoteWscd() {
        val result =
            enforcer.enforce(
                header = header(),
                claims = claims(profile = "WEBAUTHN_PRF_WSCD", secureComponent = "REMOTE_WSCD"),
                policy = policy,
                attestedKeyCount = 1,
            )

        assertTrue(result.isErr, "PRF-backed client WSCD evidence must not claim production remote-WSCD assurance")
        assertTrue("WEBAUTHN_PRF_WSCD" in result.error.message.defaultMessage)
    }

    private fun header(x5c: Boolean = true) =
        buildJsonObject {
            put("typ", KeyAttestationVerifier.KEY_ATTESTATION_TYP)
            put("alg", "ES256")
            if (x5c) {
                putJsonArray("x5c") { add("wallet-provider-certificate") }
            }
        }

    private fun claims(
        includeKeyStorage: Boolean = true,
        securityLevel: String = "ISO_18045_HIGH",
        secureComponent: String = "REMOTE_WSCD",
        nonExportable: Boolean = true,
        userAuthenticationAssurance: String = "high",
        status: String = "https://status.example/ka#2",
        statusExp: Long = NOW + 600,
        statusRevoked: Boolean = false,
        profile: String = "TS03_JWT",
        signerProfile: String = "KMS_BACKED",
        production: Boolean = true,
    ) = buildJsonObject {
        put("iss", "https://wallet-provider.example")
        put("sub", "wallet-unit-a")
        put("aud", "https://issuer.example")
        put("iat", NOW - 10)
        put("exp", NOW + 300)
        put("jti", "ka-jti-a")
        putJsonArray("attested_keys") {
            add(
                buildJsonObject {
                    put("keyId", "key-a")
                    put("algorithm", "ES256")
                    put("publicKeyJwk", """{"kty":"EC","crv":"P-256","x":"x","y":"y"}""")
                },
            )
        }
        putJsonObject("certification") {
            put("certification_id", "secure-component-cert-a")
        }
        if (includeKeyStorage) {
            putJsonObject("key_storage") {
                put("security_level", securityLevel)
                put("secure_component", secureComponent)
                put("non_exportable", nonExportable)
            }
        }
        putJsonObject("user_authentication") {
            put("assurance_level", userAuthenticationAssurance)
            putJsonArray("methods") { add("pin") }
        }
        putJsonObject("key_storage_status") {
            put("status", status)
            put("exp", statusExp)
            if (statusRevoked) put("revoked", true)
        }
        put("c_nonce", "nonce-a")
        putJsonObject("evidence") {
            put("profile", profile)
            put("signerProfile", signerProfile)
            put("production", production)
            put("ts03Conformant", true)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
