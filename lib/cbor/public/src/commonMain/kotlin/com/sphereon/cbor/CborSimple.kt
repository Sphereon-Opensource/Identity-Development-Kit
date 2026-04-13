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

package com.sphereon.cbor

import kotlinx.io.bytestring.ByteStringBuilder
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.or
import kotlin.js.JsStatic
import kotlin.native.ObjCName

/**
 * Simple (major type 7)
 *
 * @param value the simple value, for example [Simple.TRUE].
 */
abstract class CborSimple<Type : Any?>(
    value: Type,
    cddl: CDDL,
) : CborItem<Type>(value, cddl) {
    init {
        val info =
            when (cddl) {
                is CDDL.bool -> {
                    when (value) {
                        true -> CDDL.True.info
                        else -> CDDL.False.info
                    }
                }

                else -> {
                    cddl.info
                }
            }
        require(info != null)
        require(info is Int)
        check(info < 24 || (info in 32..255))
    }

    override fun encode(builder: ByteStringBuilder) {
        val majorTypeShifted = (majorType!!.type shl 5).toByte()
        builder.append(majorTypeShifted.or(info!!.toByte()))
    }

    override fun equals(other: Any?): Boolean = other is CborSimple<*> && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString() =
        when (this) {
            FALSE -> "Simple(FALSE)"
            TRUE -> "Simple(TRUE)"
            NULL -> "Simple(NULL)"
            UNDEFINED -> "Simple(UNDEFINED)"
            else -> "Simple($value)"
        }

    companion object {
        /** The [Simple] value for FALSE */
        @OptIn(ExperimentalObjCName::class)
        @ObjCName("CBOR_FALSE")
        @JsStatic
        val FALSE = CborFalse()

        /** The [Simple] value for TRUE */
        @OptIn(ExperimentalObjCName::class)
        @ObjCName("CBOR_TRUE")
        @JsStatic
        val TRUE = CborTrue()

        /** The [Simple] value for NULL */
        @OptIn(ExperimentalObjCName::class)
        @ObjCName("CBOR_NULL")
        @JsStatic
        val NULL = CborNull()

        /** The [Simple] value for UNDEFINED */
        @JsStatic
        val UNDEFINED = CborUndefined()
    }
}
