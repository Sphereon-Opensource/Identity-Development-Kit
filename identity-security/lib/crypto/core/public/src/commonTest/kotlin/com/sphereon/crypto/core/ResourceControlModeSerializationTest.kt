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

package com.sphereon.crypto.core

import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ResourceControlModeSerializationTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val tolerantJson = Json { ignoreUnknownKeys = true }
    private val defaultsJson = Json { encodeDefaults = true }

    @Test
    fun platformManagedHasStableWireValue() {
        assertEquals("\"platform_managed\"", json.encodeToString(ResourceControlMode.PLATFORM_MANAGED))
    }

    @Test
    fun externallyManagedHasStableWireValue() {
        assertEquals("\"externally_managed\"", json.encodeToString(ResourceControlMode.EXTERNALLY_MANAGED))
    }

    @Test
    fun oldKeyReferencePayloadDefaultsToPlatformManaged() {
        val decoded =
            json.decodeFromString<ManagedKeyReference>(
                """{"alias":"legacy","kid":"legacy-kid","providerId":"software","origin":"managed","signatureAlgorithm":null,"keyType":null,"keyVisibility":null,"keyEncoding":null}""",
            )

        assertEquals(ResourceControlMode.PLATFORM_MANAGED, decoded.controlMode)
    }

    @Test
    fun externallyManagedReferenceRoundTripsDirectly() {
        val reference =
            ManagedKeyReference(
                alias = "external-key",
                kid = "external-kid",
                providerId = "aws-production",
                origin = Origin.EXTERNAL,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
            )

        assertEquals(reference, json.decodeFromString<ManagedKeyReference>(json.encodeToString(reference)))
    }

    @Test
    fun externallyManagedReferenceRoundTripsInsideSerializableEnvelope() {
        val envelope =
            ManagedKeyReferenceEnvelope(
                reference =
                    ManagedKeyReference(
                        alias = "external-key",
                        providerId = "azure-production",
                        origin = Origin.EXTERNAL,
                        controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                    ),
            )

        assertEquals(envelope, json.decodeFromString<ManagedKeyReferenceEnvelope>(json.encodeToString(envelope)))
    }

    @Test
    fun legacySurrogateIgnoresNewControlModeField() {
        val newPayload =
            json.encodeToString(
                ManagedKeyReference(
                    alias = "external-key",
                    providerId = "aws-production",
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                ),
            )

        val decoded =
            tolerantJson
                .decodeFromString<LegacyManagedKeyReference>(newPayload)

        assertEquals("external-key", decoded.alias)
        assertEquals("aws-production", decoded.providerId)
        assertEquals(Origin.EXTERNAL, decoded.origin)
    }

    @Test
    fun encodeDefaultsEmitsPlatformManagedControlMode() {
        val encoded =
            defaultsJson
                .encodeToString(ManagedKeyReference(alias = "platform-key", providerId = "software"))

        val objectValue = Json.decodeFromString<JsonObject>(encoded)
        assertEquals(JsonPrimitive("platform_managed"), objectValue["controlMode"])
    }

    @Test
    fun appendedControlModePreservesExistingConstructorAndDestructuringOrder() {
        val reference =
            ManagedKeyReference(
                "alias",
                "kid",
                "provider",
                Origin.EXTERNAL,
                null,
                null,
                KeyVisibility.PUBLIC,
                KeyEncoding.JOSE,
                ResourceControlMode.EXTERNALLY_MANAGED,
            )

        val (alias, kid, providerId, origin, signatureAlgorithm, keyType, keyVisibility, keyEncoding) = reference
        assertEquals("alias", alias)
        assertEquals("kid", kid)
        assertEquals("provider", providerId)
        assertEquals(Origin.EXTERNAL, origin)
        assertNull(signatureAlgorithm)
        assertNull(keyType)
        assertEquals(KeyVisibility.PUBLIC, keyVisibility)
        assertEquals(KeyEncoding.JOSE, keyEncoding)
        assertEquals(ResourceControlMode.EXTERNALLY_MANAGED, reference.controlMode)
    }

    @Test
    fun conversionHelpersPropagateExplicitControlModeAndDefaultToPlatformManaged() {
        val managed = managedKeyInfo()

        assertEquals(
            ResourceControlMode.EXTERNALLY_MANAGED,
            managed.toKeyReference(Origin.EXTERNAL, ResourceControlMode.EXTERNALLY_MANAGED).controlMode,
        )
        assertEquals(
            ResourceControlMode.EXTERNALLY_MANAGED,
            managed.toKeyReferenceOrNull(Origin.EXTERNAL, ResourceControlMode.EXTERNALLY_MANAGED)?.controlMode,
        )
        assertEquals(
            ResourceControlMode.EXTERNALLY_MANAGED,
            managed.toSigningKeyReferenceOrNull(Origin.EXTERNAL, ResourceControlMode.EXTERNALLY_MANAGED)?.controlMode,
        )
        assertEquals(ResourceControlMode.PLATFORM_MANAGED, managed.toKeyReference().controlMode)
        assertEquals(ResourceControlMode.PLATFORM_MANAGED, managed.toKeyReferenceOrNull()?.controlMode)
        assertEquals(ResourceControlMode.PLATFORM_MANAGED, managed.toSigningKeyReferenceOrNull()?.controlMode)
    }

    @Test
    fun signingReferenceConversionPreservesTheRequestedKid() {
        val managed = managedKeyInfo()

        assertEquals(
            "external-key-kid",
            managed.toSigningKeyReferenceOrNull()?.kid,
            "Signing conversion must not discard an independent kid constraint when an alias is present",
        )
    }

    @Test
    fun managedKeyReferenceControlModeParticipatesInEquality() {
        val platformManaged =
            ManagedKeyReference(
                alias = "managed-key",
                providerId = "software",
                origin = Origin.MANAGED,
                controlMode = ResourceControlMode.PLATFORM_MANAGED,
            )
        val externallyManaged = platformManaged.copy(controlMode = ResourceControlMode.EXTERNALLY_MANAGED)

        assertNotEquals(platformManaged, externallyManaged)
    }

    private fun managedKeyInfo() =
        ManagedKeyInfo(
            alias = "external-key",
            providerId = "provider",
            resolvedKeyInfo =
                ResolvedKeyInfo(
                    key = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, kid = "external-key-kid", x = "eA", y = "eQ"),
                    kid = "external-key-kid",
                    alias = "external-key",
                    providerId = "provider",
                    keyType = KeyTypeMapping.EC,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                ),
        )
}

@Serializable
private data class ManagedKeyReferenceEnvelope(
    val reference: ManagedKeyReference,
)

@Serializable
private data class LegacyManagedKeyReference(
    val alias: String,
    val kid: String? = null,
    val providerId: String,
    val origin: Origin? = null,
    val signatureAlgorithm: SignatureAlgorithm? = null,
    val keyType: KeyTypeMapping? = null,
    val keyVisibility: KeyVisibility? = null,
    val keyEncoding: KeyEncoding? = null,
)
