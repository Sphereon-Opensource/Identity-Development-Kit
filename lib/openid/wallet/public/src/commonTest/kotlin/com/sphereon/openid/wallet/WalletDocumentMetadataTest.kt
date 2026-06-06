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

package com.sphereon.openid.wallet

import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val NOW = Instant.fromEpochSeconds(1_800_000_000)

private fun activeInst(
    id: String,
    validFrom: Instant,
    validUntil: Instant,
    boundTo: IdentifierRef? = null,
) = WalletCredentialInstance(
    credentialId = id,
    format = "dc+sd-jwt",
    raw = "raw-$id",
    holderKeyAlias = "key-$id",
    validFrom = validFrom,
    validUntil = validUntil,
    state = CredentialInstanceState.ACTIVE,
    boundTo = boundTo,
)

private fun expiredInst(id: String) =
    WalletCredentialInstance(
        credentialId = id,
        format = "dc+sd-jwt",
        raw = "raw-$id",
        holderKeyAlias = "key-$id",
        validFrom = Instant.fromEpochSeconds(1_700_000_000),
        validUntil = Instant.fromEpochSeconds(1_750_000_000),
        state = CredentialInstanceState.EXPIRED,
    )

private fun revokedInst(id: String) =
    WalletCredentialInstance(
        credentialId = id,
        format = "dc+sd-jwt",
        raw = "raw-$id",
        holderKeyAlias = "key-$id",
        state = CredentialInstanceState.REVOKED,
    )

private val ISSUER_REF = IdentifierRef(type = IdentifierType.DID, value = "did:ex:iss")
private val SUBJECT_REF = IdentifierRef(type = IdentifierType("sub-id"), value = "did:ex:subj")
private val RP_REF = IdentifierRef(type = IdentifierType("client_id"), value = "https://rp")

private val EN_DISPLAY = DisplayProperties(name = "Test Credential", locale = "en", logo = LogoProperties(uri = "https://example.com/logo-en.png"))
private val NL_DISPLAY = DisplayProperties(name = "Test Credential NL", locale = "nl", logo = LogoProperties(uri = "https://example.com/logo-nl.png"))

private fun buildTestDoc(): WalletDocument {
    val inst1 =
        activeInst(
            "c1",
            validFrom = Instant.fromEpochSeconds(1_700_000_000),
            validUntil = Instant.fromEpochSeconds(1_900_000_000),
        )
    val inst2 =
        activeInst(
            "c2",
            validFrom = Instant.fromEpochSeconds(1_710_000_000),
            validUntil = Instant.fromEpochSeconds(1_950_000_000),
            boundTo = RP_REF,
        )
    val inst3 = expiredInst("c3")

    return WalletDocument(
        id = "doc-1",
        issuer = ISSUER_REF,
        credentialTypeId = "vct:test-credential",
        subjects = listOf(SUBJECT_REF),
        issuerDisplay = listOf(DisplayProperties(name = "Test Issuer", locale = "en")),
        credentialDisplay = listOf(EN_DISPLAY, NL_DISPLAY),
        credentials = listOf(inst1, inst2, inst3),
    )
}

class WalletDocumentMetadataTest {
    @Test
    fun metadataDerivedCorrectly() {
        val doc = buildTestDoc()
        val meta = doc.metadata(now = NOW)

        assertEquals("doc-1", meta.documentId)
        assertEquals(ISSUER_REF, meta.issuer)
        assertEquals(listOf(SUBJECT_REF), meta.subjects)

        // format derived from instances
        assertEquals(CredentialFormat.SD_JWT_DC, meta.credentialFormat)

        assertEquals("vct:test-credential", meta.credentialType)

        // issuedAt = min(validFrom) across all instances that have one
        assertEquals(Instant.fromEpochSeconds(1_700_000_000), meta.issuedAt)

        // expiresAt = max(validUntil) across all instances that have one
        assertEquals(Instant.fromEpochSeconds(1_950_000_000), meta.expiresAt)

        assertEquals(3, meta.instanceCount)
        assertEquals(1, meta.boundInstanceCount)

        // ACTIVE instance exists → status ACTIVE
        assertEquals(CredentialInstanceState.ACTIVE, meta.status)

        assertEquals(doc.issuerDisplay, meta.issuerDisplay)
        assertEquals(doc.credentialDisplay, meta.credentialDisplay)

        assertEquals(NOW, meta.updatedAt)
    }

    @Test
    fun metadataJsonRoundTrip() {
        val doc = buildTestDoc()
        val meta = doc.metadata(now = NOW)
        val json = Json.encodeToString(WalletDocumentMetadata.serializer(), meta)
        val decoded = Json.decodeFromString(WalletDocumentMetadata.serializer(), json)
        assertEquals(meta, decoded)
    }

    @Test
    fun allExpiredDocHasExpiredStatus() {
        val doc =
            WalletDocument(
                id = "doc-exp",
                issuer = ISSUER_REF,
                credentialTypeId = "vct:exp",
                credentials = listOf(expiredInst("e1"), expiredInst("e2")),
            )
        val meta = doc.metadata(now = NOW)
        assertEquals(CredentialInstanceState.EXPIRED, meta.status)
    }

    @Test
    fun revokedInstanceDrivesRevokedStatus() {
        val doc =
            WalletDocument(
                id = "doc-rev",
                issuer = ISSUER_REF,
                credentialTypeId = "vct:rev",
                credentials = listOf(expiredInst("e1"), revokedInst("r1")),
            )
        val meta = doc.metadata(now = NOW)
        assertEquals(CredentialInstanceState.REVOKED, meta.status)
    }

    @Test
    fun walletDocumentJsonRoundTrip() {
        val doc = buildTestDoc()
        val json = Json.encodeToString(WalletDocument.serializer(), doc)
        val decoded = Json.decodeFromString(WalletDocument.serializer(), json)
        assertEquals(doc, decoded)
    }

    @Test
    fun identifierRefJsonRoundTrip() {
        val ref = IdentifierRef(type = IdentifierType.DID, value = "did:ex:test")
        val json = Json.encodeToString(IdentifierRef.serializer(), ref)
        val decoded = Json.decodeFromString(IdentifierRef.serializer(), json)
        assertEquals(ref, decoded)
    }

    @Test
    fun walletCredentialInstanceJsonRoundTrip() {
        val inst = activeInst("ci-1", Instant.fromEpochSeconds(1_700_000_000), Instant.fromEpochSeconds(1_900_000_000), boundTo = RP_REF)
        val json = Json.encodeToString(WalletCredentialInstance.serializer(), inst)
        val decoded = Json.decodeFromString(WalletCredentialInstance.serializer(), json)
        assertEquals(inst, decoded)
    }
}
