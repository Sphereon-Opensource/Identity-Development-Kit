/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.provider

/**
 * Audiences an access token carries when a token request names no resource indicator and the
 * client registers no default audience.
 *
 * OpenID for Verifiable Credential Issuance only recommends the `resource` parameter, so a wallet
 * may complete the authorization code flow without it. The authorization server then answers for
 * the credential issuers it is bound to: their identifiers become the token audience. A deployment
 * without credential issuers contributes no audiences and the token request keeps failing closed.
 */
interface CredentialIssuerAudienceResolver {
    suspend fun defaultAudiences(): List<String>
}
