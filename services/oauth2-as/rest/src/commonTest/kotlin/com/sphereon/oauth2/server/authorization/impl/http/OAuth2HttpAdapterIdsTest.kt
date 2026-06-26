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

package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.session.CommandId
import kotlin.test.Test
import kotlin.test.assertNotNull

class OAuth2HttpAdapterIdsTest {
    @Test
    fun adapterIdsAreValidCommandIds() {
        listOf(
            OAuth2AttestationHttpAdapter.ID,
            OAuth2AuthorizationHttpAdapter.ID,
            OAuth2DeviceAuthorizationHttpAdapter.ID,
            OAuth2DeviceVerificationHttpAdapter.ID,
            OAuth2DiscoveryHttpAdapter.ID,
            OAuth2EndSessionHttpAdapter.ID,
            OAuth2FederationHttpAdapter.ID,
            OAuth2InternalHttpAdapter.ID,
            OAuth2LoginHttpAdapter.ID,
            OAuth2OpenidDiscoveryPathIssuerHttpAdapter.ID,
            OAuth2TokenHttpAdapter.ID,
            OAuth2UserInfoHttpAdapter.ID,
        ).forEach { adapterId ->
            assertNotNull(CommandId.tryParse(adapterId), "Invalid HTTP adapter command id: $adapterId")
        }
    }
}
