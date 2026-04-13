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

package com.sphereon.mdoc.json

import com.sphereon.cbor.json.JsonView
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseKeyJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.native.ObjCName

@JsExportCompat
@JsName("mdocJsonSerializer")
val mdocJsonSerializer = MdocJsonSupport.serializer

@JsExportCompat
@JsName("MdocJsonSupport")
object MdocJsonSupport {
    val module: SerializersModule =
        SerializersModule {
//        include(CborJsonSupport.module)
//        include(CryptoJsonSupport.module)

            // Ensures we can do polymorphic serialization

        /*polymorphic(IOid4VPPresentationDefinition::class) {
            subclass(
                Oid4VPPresentationDefinition::class
            )
        }
        polymorphic(IOid4VPPresentationSubmission::class) {
            subclass(
                Oid4VPPresentationSubmission::class
            )
        }
        polymorphicDefaultDeserializer(IOid4VPPresentationSubmission::class, { Oid4VPPresentationSubmission.serializer() })
        polymorphicDefaultDeserializer(IOid4VPPresentationDefinition::class, { Oid4VPPresentationDefinition.serializer() })*/
            polymorphic(JsonView::class) {
                subclass(CoseKeyJson::class)
            }
        }
    val serializer =
        Json {
            serializersModule = module
            encodeDefaults = false
            isLenient = true
            prettyPrint = true
            ignoreUnknownKeys = true
        }
}
