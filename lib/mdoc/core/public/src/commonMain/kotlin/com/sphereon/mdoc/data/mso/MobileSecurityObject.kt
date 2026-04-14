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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.util.stringify
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("MsoVersion", exact = true)
value class MsoVersion(
    private val value: String,
) {
    init {
        require(value == "1.0") { "Version must be '1.0' but was '$value' instead" }
    }

    override fun toString(): String = value
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DigestAlgorithm", exact = true)
value class DigestAlgorithm(
    private val alg: String,
) {
    override fun toString(): String = alg
}

@JvmInline
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("DigestID", exact = true)
value class DigestID(
    private val value: UInt,
) {
    override fun toString(): String = value.toString()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileSecurityObject", exact = true)
data class MobileSecurityObject(
    val version: MsoVersion = MsoVersion("1.0"),
    val digestAlgorithm: DigestAlgorithm,
    val valueDigests: Map<NameSpace, Map<DigestID, ByteArray>>,
    val deviceKeyInfo: DeviceKeyInfo,
    val docType: DocType,
    val validityInfo: ValidityInfo,
    val original: ByteArray?,
) {
    fun getKeyInfo() = deviceKeyInfo.toKeyInfo()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as MobileSecurityObject

        if (version != other.version) {
            return false
        }
        if (digestAlgorithm != other.digestAlgorithm) {
            return false
        }
        if (valueDigests != other.valueDigests) {
            return false
        }
        if (deviceKeyInfo != other.deviceKeyInfo) {
            return false
        }
        if (docType != other.docType) {
            return false
        }
        if (validityInfo != other.validityInfo) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + digestAlgorithm.hashCode()
        result = 31 * result + valueDigests.hashCode()
        result = 31 * result + deviceKeyInfo.hashCode()
        result = 31 * result + docType.hashCode()
        result = 31 * result + validityInfo.hashCode()
        return result
    }

    override fun toString(): String =
        "MobileSecurityObject(version=$version, digestAlgorithm=$digestAlgorithm, valueDigests=${stringify(
            valueDigests.map { it.value.keys },
        )}, deviceKeyInfo=$deviceKeyInfo, docType=$docType, validityInfo=$validityInfo, original=${
            stringify(
                original,
            )
        })"

    companion object {
        @JvmStatic
        val VERSION = StringLabel("version")

        @JvmStatic
        val DIGEST_ALGORITHM = StringLabel("digestAlgorithm")

        @JvmStatic
        val VALUE_DIGESTS = StringLabel("valueDigests")

        @JvmStatic
        val DEVICE_KEY_INFO = StringLabel("deviceKeyInfo")

        @JvmStatic
        val DOC_TYPE = StringLabel("docType")

        @JvmStatic
        val VALIDITY_INFO = StringLabel("validityInfo")
    }
}

typealias ValueDigestsAlias = CborMap<StringLabel, CborMap<NumberLabel, CborByteString>>
