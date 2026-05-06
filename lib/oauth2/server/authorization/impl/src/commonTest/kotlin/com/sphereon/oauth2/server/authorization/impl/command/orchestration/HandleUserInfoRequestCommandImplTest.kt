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
 */

package com.sphereon.oauth2.server.authorization.impl.command.orchestration

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.command.UserInfoResponse
import com.sphereon.oauth2.server.authorization.command.userinfo.HandleUserInfoRequestArgs
import com.sphereon.oauth2.server.authorization.impl.command.userinfo.HandleUserInfoRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandleUserInfoRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("handle-userinfo-test", this)

    @Test
    fun forwardsAccessTokenToGetUserInfoCommand() =
        runTest {
            var seenAccessToken: String? = null
            val expected =
                UserInfoResponse(
                    buildJsonObject { put("sub", JsonPrimitive("user-1")) },
                )
            val service =
                StubAuthorizationServerService(
                    getUserInfoStub =
                        stubGetUserInfo { args ->
                            seenAccessToken = args.accessToken
                            Ok(expected)
                        },
                )
            val command = HandleUserInfoRequestCommandImpl(ctx.execution, service)

            val result = command.execute(HandleUserInfoRequestArgs(accessToken = "tok-abc"))

            assertTrue(result.isOk)
            assertEquals("tok-abc", seenAccessToken)
            assertEquals(expected, result.value)
        }

    @Test
    fun propagatesUserInfoError() =
        runTest {
            val service =
                StubAuthorizationServerService(
                    getUserInfoStub =
                        stubGetUserInfo {
                            Err(IdkError.fromString(code = "invalid_request", message = "bad token"))
                        },
                )
            val command = HandleUserInfoRequestCommandImpl(ctx.execution, service)

            val result = command.execute(HandleUserInfoRequestArgs(accessToken = "tok"))

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }
}
