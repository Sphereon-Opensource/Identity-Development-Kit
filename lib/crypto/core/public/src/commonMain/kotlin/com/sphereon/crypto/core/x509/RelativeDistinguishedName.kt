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

package com.sphereon.crypto.core.x509

import at.asitplus.signum.indispensable.asn1.Asn1Primitive
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName


/** Comma‑separated X.500 name from a list of RDNs */
fun List<RelativeDistinguishedName>.toX500(): String =
    joinToString(",") { it.toX500() }

/** Plus‑separated X.500 string for a single RDN */
fun RelativeDistinguishedName.toX500(): String =
    attrsAndValues.joinToString("+") { atv ->
        val type = OID_TO_SHORT_NAME_MAP[atv.oid.toString()] ?: atv.oid.toString()
        val valueString = (atv.value as? Asn1Primitive)
            ?.content
            ?.decodeToString()
            ?: atv.value.toString()
        "$type=$valueString"
    }
