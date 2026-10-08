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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.holder.impl.SignedMetadataVerifier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedMetadataVerificationTest {
    @Test
    fun detectsJwtCompactFormat() {
        // 3 base64url parts separated by dots
        assertTrue(SignedMetadataVerifier.isJwtResponse("eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJ0ZXN0In0.signature"))
        assertTrue(SignedMetadataVerifier.isJwtResponse("  eyJhbGciOi.eyJpc3Mi.sig  "))
    }

    @Test
    fun rejectsJsonAsNonJwt() {
        assertFalse(SignedMetadataVerifier.isJwtResponse("""{"credential_issuer": "test"}"""))
        assertFalse(SignedMetadataVerifier.isJwtResponse("  {  }"))
    }

    @Test
    fun rejectsEmptyAndMalformed() {
        assertFalse(SignedMetadataVerifier.isJwtResponse(""))
        assertFalse(SignedMetadataVerifier.isJwtResponse("not.a.jwt.four.parts"))
        assertFalse(SignedMetadataVerifier.isJwtResponse("onlyonepart"))
    }
}
