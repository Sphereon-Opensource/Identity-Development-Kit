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

import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.transfer.reader.OID4VPHandover

/**
 * Construct an OID4VP 1.0 final §B.2.6 OpenID4VPHandover.
 *
 * Both holder and verifier independently reconstruct the SessionTranscript with this
 * handover (the wallet does NOT transmit it on the wire). For encrypted-response modes
 * (`direct_post.jwt`, `dc_api.jwt`) [jwkThumbprint] MUST be the RFC 7638 SHA-256
 * thumbprint of the verifier's encryption-key JWK (raw 32 bytes). For plain modes
 * [jwkThumbprint] MUST be null. The CBOR encoder in `SessionCborCodecsImpl` handles
 * the rest of the §B.2.6 envelope (sha-256 of CBOR-encoded handoverInfo, wrapped in
 * `["OpenID4VPHandover", <hash>]`).
 */
fun oid4vpHandoverFromInputs(
    clientId: String,
    nonce: String,
    jwkThumbprint: ByteArray?,
    responseUri: String,
): OID4VPHandover =
    OID4VPHandover(
        clientId = clientId,
        nonce = nonce,
        jwkThumbprint = jwkThumbprint,
        responseUri = responseUri,
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
