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

package com.sphereon.crypto.core.x509

import at.asitplus.awesn1.Asn1Element
import at.asitplus.awesn1.Asn1Primitive

internal fun Asn1Primitive.oidString(): String {
    if (tag.tagValue.toInt() != 6) {
        error("Not an OBJECT IDENTIFIER")
    }
    val bytes = content
    if (bytes.isEmpty()) {
        error("OID has no content")
    }
    val first = bytes[0].toInt() and 0xFF
    val arcs = mutableListOf(first / 40, first % 40)
    var value = 0
    for (b in bytes.drop(1)) {
        val v = b.toInt() and 0xFF
        value = (value shl 7) or (v and 0x7F)
        if (v and 0x80 == 0) {
            arcs += value
            value = 0
        }
    }
    return arcs.joinToString(".")
}

fun isAccessMethodOid(
    element: Asn1Element,
    validOids: List<String>,
): Boolean = (element as? Asn1Primitive)?.oidString() in validOids

fun extractUriFromTaggedObject(element: Asn1Element): String? =
    (element as? Asn1Primitive)
        ?.content
        ?.decodeToString()
