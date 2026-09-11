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

import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A generated key reference must record no public material.
 *
 * The software key store mints a key's self-signed wrapper certificate when the entry is stored,
 * not when the pair is generated, so material derived here would carry no `x5c`. Storing it would
 * pin that chainless value for the life of the row and beat the key-store-resolved one, which is
 * how a verification method ends up with no certificate at all. The first read fills the column.
 */
class KeyReferenceRecordPublicMaterialTest {
    private val strictJson = Json { ignoreUnknownKeys = false }

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

    @Test
    fun generatedKeyReferenceDefaultsToPlatformManaged() {
        val record = KeyReferenceRecord.fromManagedKey(managed(ecKeyPair()), tenantId = "tenant-a")

        assertEquals(ResourceControlMode.PLATFORM_MANAGED, record.controlMode)
        assertEquals(ResourceControlMode.PLATFORM_MANAGED, record.toKeyReference().controlMode)
    }

    @Test
    fun keyReferenceConversionCarriesExternalControlMode() {
        val record =
            KeyReferenceRecord.fromManagedKey(
                managed(ecKeyPair()),
                tenantId = "tenant-a",
            ).copy(controlMode = ResourceControlMode.EXTERNALLY_MANAGED)

        assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, record.toKeyReference().controlMode)
    }

    @Test
    fun completeLegacyKeyReferenceRecordPayloadDefaultsToPlatformManaged() {
        val decoded =
            strictJson
                .decodeFromString<KeyReferenceRecord>(
                    """{"id":"record-1","tenantId":"tenant-a","alias":"issuer-signing","kid":"kid-1","providerId":"software-provider","origin":"managed","keyType":null,"signatureAlgorithm":null,"keyVisibility":null,"keyEncoding":null,"publicKeyJwk":null,"createdAt":"2026-08-20T00:00:00Z","createdById":null,"updatedAt":"2026-08-20T00:00:00Z","updatedById":null,"deletedAt":null,"deletedById":null}""",
                )

        assertEquals(ResourceControlMode.PLATFORM_MANAGED, decoded.controlMode)
    }

    @Test
    fun appendedControlModePreservesKeyReferenceRecordConstructorAndComponentOrder() {
        val createdAt = Instant.parse("2026-08-20T00:00:00Z")
        val record =
            KeyReferenceRecord(
                "record-1",
                "tenant-a",
                "alias",
                "kid",
                "provider",
                Origin.EXTERNAL,
                null,
                null,
                KeyVisibility.PUBLIC,
                null,
                null,
                createdAt,
                "creator",
                createdAt,
                "updater",
                null,
                null,
                ResourceControlMode.EXTERNALLY_MANAGED,
            )

        assertEquals("record-1", record.component1())
        assertEquals("tenant-a", record.component2())
        assertEquals("alias", record.component3())
        assertEquals("kid", record.component4())
        assertEquals("provider", record.component5())
        assertEquals(Origin.EXTERNAL, record.component6())
        assertEquals(KeyVisibility.PUBLIC, record.component9())
        assertEquals("creator", record.component13())
        assertEquals("updater", record.component15())
        assertNull(record.component17())
        assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, record.component18())
    }
}
