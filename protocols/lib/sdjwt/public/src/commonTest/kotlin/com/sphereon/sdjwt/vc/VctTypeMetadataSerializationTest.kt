/*
 * Copyright 2026 Sphereon International B.V.
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
 */

package com.sphereon.sdjwt.vc

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class VctTypeMetadataSerializationTest {
    @Test
    fun localizedMetadataUsesLocaleWireName() {
        val metadata =
            SdJwtVcTypeMetadata(
                vct = "https://issuer.example/vct/pid",
                display = listOf(DisplayInformation(locale = "en-US", name = "PID")),
                claims =
                    listOf(
                        ClaimInformation(
                            path = listOf("given_name"),
                            display = listOf(ClaimDisplayMetadata(locale = "en-US", label = "Given name")),
                        ),
                    ),
            )

        val encoded = Json.encodeToString(metadata)

        assertContains(encoded, "\"locale\":\"en-US\"")
        assertFalse("\"lang\"" in encoded)
    }
}
