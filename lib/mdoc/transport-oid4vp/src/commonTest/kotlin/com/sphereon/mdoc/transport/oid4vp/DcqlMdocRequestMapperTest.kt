package com.sphereon.mdoc.transport.oid4vp

import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DcqlMdocRequestMapperTest {

    @Test
    fun mapsDcqlClaimPathToDocRequest() {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(
                    id = "mdl",
                    format = "mso_mdoc",
                    meta = JsonObject(
                        mapOf(
                            "doctype_value" to JsonPrimitive("org.iso.18013.5.1.mDL")
                        )
                    ),
                    claims = listOf(
                        DcqlClaimQuery(
                            path = listOf("org.iso.18013.5.1", "given_name"),
                            intent_to_retain = true
                        )
                    )
                )
            )
        )

        val deviceRequest = DcqlMdocRequestMapper.toDeviceRequest(dcqlQuery)
        val docRequest = deviceRequest.docRequests?.single()
        assertNotNull(docRequest)
        assertEquals(DocType("org.iso.18013.5.1.mDL"), docRequest.itemsRequest.docType)

        val identifiers = docRequest.itemsRequest.getIdentifiers(NameSpace("org.iso.18013.5.1"))
        val intent = identifiers[DataElementIdentifier("given_name")]
        assertEquals(IntentToRetain(true), intent)
    }

    @Test
    fun mapsSingleSegmentClaimAcrossNamespaces() {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(
                    id = "mdl",
                    format = "mso_mdoc",
                    meta = JsonObject(
                        mapOf(
                            "doctype_value" to JsonPrimitive("org.iso.18013.5.1.mDL"),
                            "namespace_values" to JsonArray(
                                listOf(
                                    JsonPrimitive("org.iso.18013.5.1"),
                                    JsonPrimitive("org.iso.18013.5.1.aamva")
                                )
                            )
                        )
                    ),
                    claims = listOf(
                        DcqlClaimQuery(
                            path = listOf("family_name")
                        )
                    )
                )
            )
        )

        val deviceRequest = DcqlMdocRequestMapper.toDeviceRequest(dcqlQuery)
        val docRequest = deviceRequest.docRequests?.single()
        assertNotNull(docRequest)

        val baseIdentifiers = docRequest.itemsRequest.getIdentifiers(NameSpace("org.iso.18013.5.1"))
        val aamvaIdentifiers = docRequest.itemsRequest.getIdentifiers(NameSpace("org.iso.18013.5.1.aamva"))

        assertTrue(baseIdentifiers.containsKey(DataElementIdentifier("family_name")))
        assertTrue(aamvaIdentifiers.containsKey(DataElementIdentifier("family_name")))
    }

    @Test
    fun failsWhenNoMdocCredentialQueriesPresent() {
        val dcqlQuery = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(
                    id = "sdjwt",
                    format = "vc+sd-jwt",
                    claims = listOf(
                        DcqlClaimQuery(path = listOf("name"))
                    )
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            DcqlMdocRequestMapper.toDeviceRequest(dcqlQuery)
        }
    }
}
