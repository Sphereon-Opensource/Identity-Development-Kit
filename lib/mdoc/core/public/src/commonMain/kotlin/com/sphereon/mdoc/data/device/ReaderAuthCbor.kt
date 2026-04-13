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

package com.sphereon.mdoc.data.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.cddl_bool
import com.sphereon.cbor.cddl_bstr
import com.sphereon.cbor.cddl_tstr


@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderAuthCbor", exact = true)
data class ReaderAuthCbor(
    val readerAuth: ByteArray,
    val readerSignIsValid: cddl_bool,
    // fixme: X509Certificate
    val readerCertificateChain: List<cddl_bstr>,
    val readerCertificatedIsTrusted: cddl_bool,
    val readerCommonName: cddl_tstr
)
