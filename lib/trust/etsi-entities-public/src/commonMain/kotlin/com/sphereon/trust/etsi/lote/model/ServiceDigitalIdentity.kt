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

package com.sphereon.trust.etsi.lote.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Digital identity of a service per ETSI TS 119 602.
 *
 * Key difference from 612: supports plural certificates, public key values (JWK),
 * and multiple X.509 Subject Key Identifiers.
 */
@JsExportCompat
@Serializable
data class LoTEServiceDigitalIdentity(
    @SerialName("X509Certificates")
    val x509Certificates: List<PkiObject> = emptyList(),
    @SerialName("X509SubjectNames")
    val x509SubjectNames: List<String> = emptyList(),
    @SerialName("PublicKeyValues")
    val publicKeyValues: List<JsonElement> = emptyList(),
    @SerialName("X509SKIs")
    val x509SKIs: List<String> = emptyList(),
    @SerialName("OtherIds")
    val otherIds: List<String> = emptyList(),
)

/**
 * PKI object containing a Base64-encoded DER value.
 */
@JsExportCompat
@Serializable
data class PkiObject(
    @SerialName("pkiOb")
    val value: String,
)
