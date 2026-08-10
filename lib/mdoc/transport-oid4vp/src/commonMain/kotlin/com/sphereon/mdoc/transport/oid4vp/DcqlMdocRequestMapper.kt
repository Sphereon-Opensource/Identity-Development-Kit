/*
 * Ac 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transport.oid4vp

import com.sphereon.core.api.log.LogService
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.oid4vp.Oid4VPFormatIdentifier
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.MdocMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object DcqlMdocRequestMapper {
    private val dcqlJson = Json { ignoreUnknownKeys = true }

    fun toDeviceRequest(
        dcqlQuery: DcqlQuery,
        log: LogService? = null,
    ): DeviceRequest {
        val docRequests = buildDocRequestsFromDcql(dcqlQuery, log)
        return DeviceRequest(
            docRequests = docRequests.toTypedArray(),
            oid4vpRequest = null,
            macKeys = null,
            original = null,
        )
    }

    private fun buildDocRequestsFromDcql(
        dcqlQuery: DcqlQuery,
        log: LogService?,
    ): List<DocRequest> {
        val credentialQueries = dcqlQuery.credentials

        val docRequestBuilders = mutableMapOf<String, DocRequestBuildState>()
        credentialQueries.forEach { credentialQuery ->
            if (!isMdocCredentialQuery(credentialQuery)) {
                return@forEach
            }

            val meta = dcqlJson.decodeFromJsonElement(MdocMeta.serializer(), credentialQuery.meta)
            val docTypeValue =
                meta?.doctype_value?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException(
                        "DCQL mso_mdoc query missing meta.doctype_value for credential id '${credentialQuery.id}'",
                    )

            val state =
                docRequestBuilders.getOrPut(docTypeValue) {
                    val builder = DocRequest.Builder()
                    val itemsBuilder = builder.docType(DocType(docTypeValue))
                    DocRequestBuildState(builder, itemsBuilder)
                }

            val claimPaths = collectDcqlClaimPaths(credentialQuery)
            if (claimPaths.isEmpty()) {
                return@forEach
            }

            claimPaths.forEach { claim ->
                val path = claim.path
                val namespace = path[0]
                val identifier = path[1]
                state.itemsBuilder.add(
                    NameSpace(namespace),
                    DataElementIdentifier(identifier),
                    IntentToRetain(false),
                )
            }
        }

        require(docRequestBuilders.isNotEmpty()) { "DCQL query does not contain any mso_mdoc credential requests" }

        return docRequestBuilders.values.map { it.builder.build() }
    }

    private fun isMdocCredentialQuery(credentialQuery: DcqlCredentialQuery): Boolean {
        return credentialQuery.format == Oid4VPFormatIdentifier.MSO_MDOC.value
    }

    private fun collectDcqlClaimPaths(credentialQuery: DcqlCredentialQuery): List<DcqlClaimPath> {
        val claims = credentialQuery.claims ?: return emptyList()
        val selected = selectClaims(claims, credentialQuery.claim_sets)
        return selected.map { claim ->
            val path =
                claim.path.components.mapIndexed { index, component ->
                    (component as? JsonPrimitive)?.contentOrNull?.takeIf { component.isString }
                        ?: throw IllegalArgumentException(
                            "mso_mdoc Claims Path Pointer component at index $index must be a string",
                        )
                }
            require(path.size == 2) {
                "mso_mdoc Claims Path Pointer must contain exactly namespace and data element identifier"
            }
            DcqlClaimPath(path = path)
        }
    }

    private fun selectClaims(
        claims: List<DcqlClaimQuery>,
        claimSets: List<List<String>>?,
    ): List<DcqlClaimQuery> {
        if (claimSets == null) return claims
        val claimsById = claims.mapNotNull { claim -> claim.id?.let { it to claim } }.toMap()
        return claimSets.firstNotNullOfOrNull { option ->
            option.map { claimsById[it] ?: return@firstNotNullOfOrNull null }
        } ?: emptyList()
    }

    private data class DcqlClaimPath(
        val path: List<String>,
    )

    private data class DocRequestBuildState(
        val builder: DocRequest.Builder,
        val itemsBuilder: DeviceItemsRequest.Builder,
    )
}
