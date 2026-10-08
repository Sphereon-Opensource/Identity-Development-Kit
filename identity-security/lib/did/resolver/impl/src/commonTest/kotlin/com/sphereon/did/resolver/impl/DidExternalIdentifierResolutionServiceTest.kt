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

package com.sphereon.did.resolver.impl

import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DidExternalIdentifierResolutionServiceTest {
    private val did = "did:web:tenant.example.com"
    private val verifier = key("$did#verifier-request-object", "verifier-x")
    private val issuer = key("$did#issuer-assertion", "issuer-x")

    @Test
    fun selectsRequestedFragmentInsteadOfFirstDocumentKey() {
        val selected = selectDidKeyInfo("$did#issuer-assertion", "issuer-assertion", listOf(verifier, issuer))

        assertEquals(issuer.kid, selected?.kid)
    }

    @Test
    fun refusesMissingOrAmbiguousRequestedFragment() {
        assertNull(selectDidKeyInfo("$did#missing", "missing", listOf(verifier, issuer)))
        assertNull(selectDidKeyInfo("$did#issuer-assertion", "issuer-assertion", listOf(issuer, issuer)))
    }

    @Test
    fun preservesFirstKeyBehaviorForBareDid() {
        assertEquals(verifier.kid, selectDidKeyInfo(did, null, listOf(verifier, issuer))?.kid)
    }

    private fun key(kid: String, x: String): ResolvedKeyInfo<JwkType> =
        ResolvedKeyInfo(
            kid = kid,
            key =
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = x,
                    y = "test-y",
                ),
        )
}
