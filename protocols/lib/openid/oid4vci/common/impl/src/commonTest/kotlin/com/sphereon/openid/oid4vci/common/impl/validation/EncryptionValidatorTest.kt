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

package com.sphereon.openid.oid4vci.common.impl.validation

import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class EncryptionValidatorTest {
    private val sampleJwk =
        JsonObject(
            mapOf(
                "kty" to JsonPrimitive("EC"),
                "crv" to JsonPrimitive("P-256"),
                "x" to JsonPrimitive("f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU"),
                "y" to JsonPrimitive("x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"),
            ),
        )

    @Test
    fun validParametersWithAlgPass() {
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = sampleJwk,
                alg = "ECDH-ES+A256KW",
                enc = "A256GCM",
            )
        val result = credentialResponseEncryptionValidator(encryption)
        assertTrue(result is Valid, "Valid encryption parameters with alg should pass validation")
    }

    @Test
    fun validParametersWithoutAlgPass() {
        // OID4VCI 1.1: alg is optional, key agreement may come from the JWK
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = sampleJwk,
                enc = "A256GCM",
            )
        val result = credentialResponseEncryptionValidator(encryption)
        assertTrue(result is Valid, "Encryption parameters without alg should pass validation for 1.1")
    }

    @Test
    fun emptyEncFails() {
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = sampleJwk,
                alg = "ECDH-ES+A256KW",
                enc = "",
            )
        val result = credentialResponseEncryptionValidator(encryption)
        assertTrue(result is Invalid, "Empty enc should fail validation")
    }

    @Test
    fun emptyJwkFails() {
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = JsonObject(emptyMap()),
                alg = "ECDH-ES+A256KW",
                enc = "A256GCM",
            )
        val result = credentialResponseEncryptionValidator(encryption)
        assertTrue(result is Invalid, "Empty jwk should fail validation")
    }
}
