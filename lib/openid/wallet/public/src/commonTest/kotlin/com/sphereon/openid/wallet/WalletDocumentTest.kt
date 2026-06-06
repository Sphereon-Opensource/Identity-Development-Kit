package com.sphereon.openid.wallet

import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.openid.oid4vc.common.DisplayProperties
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDocumentTest {
    private fun inst(
        id: String,
        domain: String,
        state: CredentialInstanceState = CredentialInstanceState.ACTIVE
    ) = WalletCredentialInstance(id, "dc+sd-jwt", "raw-$id", "key-$id", domain, 0, null, null, state)

    @Test
    fun roundTripsAndHelpers() {
        val doc =
            WalletDocument(
                id = "d1",
                issuer = IdentifierRef(type = IdentifierType("url"), value = "acme"),
                credentialTypeId = "vct:emp",
                credentialDisplay =
                    listOf(
                        DisplayProperties(name = "Employee Credential", locale = "en"),
                        DisplayProperties(name = "Werknemersbewijs", locale = "nl"),
                    ),
                refresh = WalletRefreshState(lowWatermark = 1),
                credentials = listOf(inst("c1", "default")),
            )
        val json = Json.encodeToString(WalletDocument.serializer(), doc)
        assertEquals(doc, Json.decodeFromString(WalletDocument.serializer(), json))
        assertEquals("Werknemersbewijs", doc.displayName("nl"))
        assertEquals("Employee Credential", doc.displayName("en"))
        assertEquals(2, doc.withAddedInstance(inst("c2", "default")).credentials.size)
        assertEquals("c1", doc.unusedInstance("default")?.credentialId)
    }
}
