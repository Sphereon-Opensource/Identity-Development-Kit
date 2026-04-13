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

package com.sphereon.crypto.core.jose

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.Jwt
import com.sphereon.crypto.core.jose.JwtHeader
import com.sphereon.crypto.core.jose.JwtPayload
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JWTTest {
    @Test
    fun shouldMakeWorkingWithJwtFunctionsPossible(): TestResult =
        runTest {
            val header = JwtHeader()
            header.putString("kid", "kid-example")
            header.epk = Jwk(alg = JwaAlgorithm.A256GCMKW, kty = JwaKeyType.EC)

            val payload = JwtPayload()
            payload.iss = "http://test"
            assertEquals("http://test", payload.iss)

            payload.put("test", JsonPrimitive(6))
            assertEquals(6, payload["test"]?.jsonPrimitive?.int)

            val jwt = Jwt(header, payload)
            assertEquals(jwtPayload, jwt.toJsonString())
            println(jwt.toJsonString())
        }

    val jwtPayload = """{"header":{"kid":"kid-example","epk":{"alg":"A256GCMKW","kty":"EC"}},"payload":{"iss":"http://test","test":6}}"""
}
