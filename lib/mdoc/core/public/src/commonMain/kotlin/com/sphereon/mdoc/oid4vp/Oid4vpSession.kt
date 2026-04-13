/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.oid4vp

import com.sphereon.cbor.dsl.cborArray
import com.sphereon.cbor.dsl.encode
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.transfer.reader.OID4VPHandover
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * ISO 18013-7
 */
fun clientIdToHash(
    clientId: String,
    generatedNonce: String,
): ByteArray {
    val clientIdToHash =
        cborArray {
            add(clientId)
            add(generatedNonce)
        }.encode()
    return hash(clientIdToHash, DigestAlg.SHA256)
}

fun responseUriToHash(
    responseUri: String,
    generatedNonce: String,
): ByteArray {
    val responseUriToHash =
        cborArray {
            add(responseUri)
            add(generatedNonce)
        }.encode()
    return hash(responseUriToHash, DigestAlg.SHA256)
}

fun oid4vpHandoverFromClientIdAndResponseUri(
    clientId: String,
    responseUri: String,
    mdocGeneratedNonce: String = Uuid.v4String(),
    authorizationRequestNonce: String,
): OID4VPHandover =
    OID4VPHandover(
        clientIdHash = clientIdToHash(clientId, mdocGeneratedNonce),
        responseUriHash = responseUriToHash(responseUri, mdocGeneratedNonce),
        nonce = authorizationRequestNonce,
    )

private const val PATH_ENTRY_GROUP_COUNT = 3

fun parseConstraintFieldPath(path: String) {
    val (nameSpace, elementIdentifier) = assertedPathEntry(path)
}

fun assertedPathEntry(pathEntry: String): Pair<NameSpace, DataElementIdentifier> {
    // WARNING: Do not remove the backslashes that seem redundant near the ] bracket. If you do JS will fail!
    val match = Regex("^\\\$\\['([\\w.]+)'\\]\\['(\\w+)'\\]$").matchEntire(pathEntry)
    val results = match?.groupValues
    require(!results.isNullOrEmpty() && results.size == PATH_ENTRY_GROUP_COUNT) {
        "Path entry in the OID4VP constraint field is not valid: $pathEntry"
    }
    return Pair(NameSpace(results[1]), DataElementIdentifier(results[2]))
}
