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

package com.sphereon.crypto.key.persistence

import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * A generated key reference must record no public material.
 *
 * The software key store mints a key's self-signed wrapper certificate when the entry is stored,
 * not when the pair is generated, so material derived here would carry no `x5c`. Storing it would
 * pin that chainless value for the life of the row and beat the key-store-resolved one, which is
 * how a verification method ends up with no certificate at all. The first read fills the column.
 */
class KeyReferenceRecordPublicMaterialTest {
    private fun managed(key: Jwk) =
        ManagedKeyInfo(
            alias = "issuer-signing",
            providerId = "software-provider",
            resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = key,
                    alias = "issuer-signing",
                    providerId = "software-provider",
                    keyVisibility = KeyVisibility.PRIVATE,
                    keyType = if (key.kty == JwaKeyType.oct) KeyTypeMapping.Symmetric else KeyTypeMapping.EC,
                ),
        )

    private fun ecKeyPair() =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "eA",
            y = "eQ",
            d = "ZA",
        )

    @Test
    fun aGeneratedKeyRecordsNoPublicMaterialBecauseItHasNoCertificateYet() {
        val record = KeyReferenceRecord.fromManagedKey(managed(ecKeyPair()), tenantId = "tenant-a")

        assertNull(
            record.publicKeyJwk,
            "generation must not pin public material derived before the key store minted the certificate",
        )
    }

    @Test
    fun aSymmetricKeyRecordsNoPublicMaterialEither() {
        val record =
            KeyReferenceRecord.fromManagedKey(
                managed(Jwk(kty = JwaKeyType.oct, k = "c2VjcmV0")),
                tenantId = "tenant-a",
            )

        assertNull(record.publicKeyJwk)
    }
}
