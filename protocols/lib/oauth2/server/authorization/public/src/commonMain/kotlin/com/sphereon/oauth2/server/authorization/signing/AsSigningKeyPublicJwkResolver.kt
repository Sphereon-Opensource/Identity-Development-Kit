/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.signing

import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding

/**
 * Resolves the public-only JWK for a durable authorization-server signing-key descriptor.
 *
 * Deployments whose signing keys live outside the authorization-server process bind this seam to
 * their routed KMS public-material command. Returning `null` is a closed failure: callers must not
 * retry through a local provider registry when a deployment resolver is installed.
 */
interface AsSigningKeyPublicJwkResolver {
    suspend fun resolve(signingKey: OAuth2SigningKey): Jwk?
}

/** Supplies an absent resolver to standalone IDK graphs. */
@ContributesTo(SessionScope::class)
interface AsSigningKeyPublicJwkResolverOptionalProvider {
    @OptionalBinding
    val optionalAsSigningKeyPublicJwkResolver: AsSigningKeyPublicJwkResolver? get() = null
}
