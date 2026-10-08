/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.oauth2.server.authorization

/**
 * Source-compatible alias for the issuer value now owned by the neutral OAuth2 common contract.
 * JWT validation imports the common package directly and therefore no longer depends on the
 * authorization-server implementation/public resource API.
 */
typealias CanonicalAuthorizationServerIssuer = com.sphereon.oauth2.common.model.CanonicalAuthorizationServerIssuer
