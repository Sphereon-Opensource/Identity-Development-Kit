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

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.model.ClientRegistration

internal fun ClientRegistration.toVerifiedClientAuthorization(): VerifiedClientAuthorization =
    VerifiedClientAuthorization(
        clientId = clientId,
        grantTypes = grantTypes,
        allowedScopes = allowedScopes,
        defaultAccessTokenAudience = defaultAccessTokenAudience,
        allowedAccessTokenAudiences = allowedAccessTokenAudiences,
        principalRoles = principalRoles,
        requirePkce = requirePkce,
        tlsClientCertificateBoundAccessTokens = tlsClientCertificateBoundAccessTokens,
        tenantId = additionalMetadata[TENANT_ID_CLAIM] as? String,
    )

private const val TENANT_ID_CLAIM = "tenant_id"
