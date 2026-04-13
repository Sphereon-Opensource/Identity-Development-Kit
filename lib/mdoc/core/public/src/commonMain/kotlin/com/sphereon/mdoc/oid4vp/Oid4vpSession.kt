/*
 * © 2025 Sphereon International B.V.
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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.dsl.cborArray
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.core.compat.Uuid
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.transfer.reader.OID4VPHandover

/**
 * ISO 18013-7
 */
fun clientIdToHash(clientId: String, generatedNonce: String): ByteArray {
    val clientIdToHash = Cbor.encode(cborArray {
        add(clientId)
        add(generatedNonce)
    })
    return hash(clientIdToHash, DigestAlg.SHA256)
}

fun responseUriToHash(responseUri: String, generatedNonce: String): ByteArray {
    val responseUriToHash = Cbor.encode(cborArray {
        add(responseUri)
        add(generatedNonce)
    })
    return hash(responseUriToHash, DigestAlg.SHA256)
}


fun oid4vpHandoverFromClientIdAndResponseUri(clientId: String, responseUri: String, mdocGeneratedNonce: String = Uuid.v4String(), authorizationRequestNonce: String): OID4VPHandover {
    return OID4VPHandover(
        clientIdHash = clientIdToHash(clientId, mdocGeneratedNonce),
        responseUriHash = responseUriToHash(responseUri, mdocGeneratedNonce),
        nonce = authorizationRequestNonce
    )
}


fun parseConstraintFieldPath(path: String) {
    val (nameSpace,elementIdentifier) = assertedPathEntry(path)

}

fun assertedPathEntry(pathEntry: String): Pair<NameSpace, DataElementIdentifier> {
    // WARNING: Do not remove the backslashes that seem redundant near the ] bracket. If you do JS will fail!
    val match = Regex("^\\\$\\['([\\w.]+)'\\]\\['(\\w+)'\\]$").matchEntire(pathEntry)
    val results = match?.groupValues
    if (results.isNullOrEmpty() || results.size != 3) {
        throw IllegalArgumentException("Path entry in the OID4VP constraint field is not valid: $pathEntry")
    }
    return Pair(NameSpace(results[1]), DataElementIdentifier(results[2]))
}
