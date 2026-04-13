/*
 * Ac 2025 Sphereon International B.V.
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
import com.sphereon.openid.oid4vp.dcql.DcqlClaimSet
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.MdocMeta
import kotlinx.serialization.json.Json

internal object DcqlMdocRequestMapper {
    private val dcqlJson = Json { ignoreUnknownKeys = true }

    fun toDeviceRequest(dcqlQuery: DcqlQuery, log: LogService? = null): DeviceRequest {
        val docRequests = buildDocRequestsFromDcql(dcqlQuery, log)
        return DeviceRequest(
            docRequests = docRequests.toTypedArray(),
            oid4vpRequest = null,
            macKeys = null,
            original = null
        )
    }

    private fun buildDocRequestsFromDcql(dcqlQuery: DcqlQuery, log: LogService?): List<DocRequest> {
        val credentialQueries = dcqlQuery.credentials.orEmpty()
        if (credentialQueries.isEmpty()) {
            throw IllegalArgumentException("DCQL query has no credential queries to build mdoc requests")
        }

        val docRequestBuilders = mutableMapOf<String, DocRequestBuildState>()
        credentialQueries.forEach { credentialQuery ->
            if (!isMdocCredentialQuery(credentialQuery)) {
                return@forEach
            }

            val meta = credentialQuery.meta?.let { dcqlJson.decodeFromJsonElement(MdocMeta.serializer(), it) }
            val docTypeValue = meta?.doctype_value?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "DCQL mso_mdoc query missing meta.doctype_value for credential id '${credentialQuery.id}'"
                )

            val state = docRequestBuilders.getOrPut(docTypeValue) {
                val builder = DocRequest.Builder()
                val itemsBuilder = builder.docType(DocType(docTypeValue))
                DocRequestBuildState(builder, itemsBuilder)
            }

            val claimPaths = collectDcqlClaimPaths(credentialQuery)
            if (claimPaths.isEmpty()) {
                return@forEach
            }

            val namespaceValues = meta?.namespace_values.orEmpty()
            claimPaths.forEach { claim ->
                val path = claim.path
                if (path.isEmpty()) {
                    return@forEach
                }

                val intentToRetain = IntentToRetain(claim.intentToRetain ?: false)

                if (path.size == 1) {
                    if (namespaceValues.isEmpty()) {
                        throw IllegalArgumentException(
                            "DCQL claim path '${path[0]}' missing namespace for docType '$docTypeValue'"
                        )
                    }
                    namespaceValues.forEach { namespace ->
                        state.itemsBuilder.add(
                            NameSpace(namespace),
                            DataElementIdentifier(path[0]),
                            intentToRetain
                        )
                    }
                    return@forEach
                }

                if (path.size > 2) {
                    log?.debug("DCQL claim path has more than two segments for mdoc, using first two: $path")
                }

                val namespace = path[0]
                val identifier = path[1]
                state.itemsBuilder.add(
                    NameSpace(namespace),
                    DataElementIdentifier(identifier),
                    intentToRetain
                )
            }
        }

        if (docRequestBuilders.isEmpty()) {
            throw IllegalArgumentException("DCQL query does not contain any mso_mdoc credential requests")
        }

        return docRequestBuilders.values.map { it.builder.build() }
    }

    private fun isMdocCredentialQuery(credentialQuery: DcqlCredentialQuery): Boolean {
        val format = credentialQuery.format?.lowercase()
        return format == null || format == Oid4VPFormatIdentifier.MSO_MDOC.value || format.contains("mdoc")
    }

    private fun collectDcqlClaimPaths(credentialQuery: DcqlCredentialQuery): List<DcqlClaimPath> {
        val claimPaths = mutableListOf<DcqlClaimPath>()
        credentialQuery.claims.orEmpty().forEach { claim ->
            claimPaths.add(DcqlClaimPath(path = claim.path, intentToRetain = claim.intent_to_retain))
        }
        credentialQuery.claim_sets.orEmpty().forEach { claimSet ->
            claimPaths.addAll(claimSetToPaths(claimSet))
        }
        return claimPaths
    }

    private fun claimSetToPaths(claimSet: DcqlClaimSet): List<DcqlClaimPath> {
        return claimSet.claims.map { claim ->
            val path = if (claim.contains(".")) {
                claim.split(".")
            } else {
                listOf(claim)
            }
            DcqlClaimPath(path = path, intentToRetain = null)
        }
    }

    private data class DcqlClaimPath(
        val path: List<String>,
        val intentToRetain: Boolean? = null
    )

    private data class DocRequestBuildState(
        val builder: DocRequest.Builder,
        val itemsBuilder: DeviceItemsRequest.Builder
    )
}
