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

import com.sphereon.trust.etsi.lote.model.OperatorAddress
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject

/** TS 119 602 uses different address member names for operators and entities. */
object TrustedEntityAddressSerializer : JsonTransformingSerializer<OperatorAddress>(OperatorAddress.serializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement = rename(element, true)
    override fun transformDeserialize(element: JsonElement): JsonElement = rename(element, false)

    private fun rename(element: JsonElement, encoding: Boolean): JsonObject = JsonObject(
        element.jsonObject.mapKeys { (key, _) ->
            if (encoding) when (key) {
                "SchemeOperatorPostalAddress" -> "TEPostalAddress"
                "SchemeOperatorElectronicAddress" -> "TEElectronicAddress"
                else -> key
            } else when (key) {
                "TEPostalAddress" -> "SchemeOperatorPostalAddress"
                "TEElectronicAddress" -> "SchemeOperatorElectronicAddress"
                else -> key
            }
        },
    )
}
