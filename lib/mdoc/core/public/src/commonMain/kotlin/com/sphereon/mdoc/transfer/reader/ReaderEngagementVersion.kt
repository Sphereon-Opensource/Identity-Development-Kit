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

package com.sphereon.mdoc.transfer.reader

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlinx.serialization.Serializable
import com.sphereon.cbor.CborString
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasToCbor
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderEngagementVersion", exact = true)
value class ReaderEngagementVersion(private val version: String = "1.1") : HasToCbor<CborString> {
    init {
        require(version == "1.0" || version == "1.1" ) { "Version must be 1.0 or 1.1" }


    }

    override fun toCborStructure(): CborString = CborString(version)

    override fun toString(): String = version

    companion object Decoder : HasFromCbor<CborString, ReaderEngagementVersion> {

        override fun fromCborStructure(structure: CborString): ReaderEngagementVersion = ReaderEngagementVersion(structure.value)
    }

}