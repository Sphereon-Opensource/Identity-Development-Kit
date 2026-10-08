/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transport.oid4vp

import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.openid.oid4vp.dcql.ClaimsPathPointer
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
        val dcqlQuery =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "mdl",
                            format = "mso_mdoc",
                            meta =
                                JsonObject(
                                    mapOf(
                                        "doctype_value" to JsonPrimitive("org.iso.18013.5.1.mDL"),
                                    ),
                                ),
                            claims =
                                listOf(
                                    DcqlClaimQuery(
                                        path = ClaimsPathPointer(listOf(JsonPrimitive("org.iso.18013.5.1"), JsonPrimitive("given_name"))),
                                        intent_to_retain = true,
                                    ),
                                ),
                        ),
                    ),
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
    fun rejectsDraftSingleSegmentMdocClaimPath() {
        assertFailsWith<IllegalArgumentException> {
            val dcqlQuery =
                DcqlQuery(
                    credentials =
                        listOf(
                            DcqlCredentialQuery(
                                id = "mdl",
                                format = "mso_mdoc",
                                meta =
                                    JsonObject(
                                        mapOf(
                                            "doctype_value" to JsonPrimitive("org.iso.18013.5.1.mDL"),
                                        ),
                                    ),
                                claims =
                                    listOf(
                                        DcqlClaimQuery(
                                            path = ClaimsPathPointer(listOf(JsonPrimitive("family_name"))),
                                        ),
                                    ),
                            ),
                        ),
                )
            DcqlMdocRequestMapper.toDeviceRequest(dcqlQuery)
        }
    }

    @Test
    fun failsWhenNoMdocCredentialQueriesPresent() {
        val dcqlQuery =
            DcqlQuery(
                credentials =
                    listOf(
                        DcqlCredentialQuery(
                            id = "sdjwt",
                            format = "dc+sd-jwt",
                            meta =
                                JsonObject(
                                    mapOf(
                                        "vct_values" to JsonArray(listOf(JsonPrimitive("https://credentials.example.com/identity"))),
                                    ),
                                ),
                            claims =
                                listOf(
                                    DcqlClaimQuery(path = ClaimsPathPointer(listOf(JsonPrimitive("name")))),
                                ),
                        ),
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            DcqlMdocRequestMapper.toDeviceRequest(dcqlQuery)
        }
    }
}
