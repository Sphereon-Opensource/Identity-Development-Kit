/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.CredentialIssuerAudienceResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default for assemblies without a credential-issuer registry: no implicit audience, so a JWT
 * access token still needs a resource indicator or a registered client default.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialIssuerAudienceResolver>())
class NoCredentialIssuerAudienceResolver : CredentialIssuerAudienceResolver {
    override suspend fun defaultAudiences(): List<String> = emptyList()
}
