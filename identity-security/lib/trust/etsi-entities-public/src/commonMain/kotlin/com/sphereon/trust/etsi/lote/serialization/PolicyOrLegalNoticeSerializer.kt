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

package com.sphereon.trust.etsi.lote.serialization

import com.sphereon.trust.etsi.lote.model.MultiLangURI
import com.sphereon.trust.etsi.lote.model.PolicyOrLegalNoticeEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object PolicyOrLegalNoticeSerializer : KSerializer<PolicyOrLegalNoticeEntry> {
    @Serializable
    private data class Wire(
        @SerialName("LoTEPolicy") val policy: MultiLangURI? = null,
        @SerialName("LoTELegalNotice") val notice: String? = null,
    )
    override val descriptor = Wire.serializer().descriptor
    override fun serialize(encoder: Encoder, value: PolicyOrLegalNoticeEntry) {
        require((value.notice != null) != (value.noticeURI != null)) { "Exactly one policy URI or legal notice is required" }
        encoder.encodeSerializableValue(Wire.serializer(), Wire(
            policy = value.noticeURI?.let { MultiLangURI(requireNotNull(value.lang), it) },
            notice = value.notice,
        ))
    }
    override fun deserialize(decoder: Decoder): PolicyOrLegalNoticeEntry {
        val wire = decoder.decodeSerializableValue(Wire.serializer())
        require((wire.notice != null) != (wire.policy != null)) { "Exactly one policy URI or legal notice is required" }
        return PolicyOrLegalNoticeEntry(lang = wire.policy?.lang, notice = wire.notice, noticeURI = wire.policy?.uriValue)
    }
}
