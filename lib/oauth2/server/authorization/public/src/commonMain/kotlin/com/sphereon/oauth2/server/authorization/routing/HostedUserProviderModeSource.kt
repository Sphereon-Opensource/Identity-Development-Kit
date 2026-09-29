/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.routing

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Durable source of the interactive user-provider mode of a hosted authorization server.
 *
 * A runtime that keeps a typed resource per hosted server (with its declared authentication mode)
 * implements this so the user-provider selection follows that resource instead of free-form
 * configuration. The returned value is a key of the user-provider multibinding:
 * [FEDERATED_USER_PROVIDER_MODE] for a server that authenticates users only through an upstream
 * provider, [LOCAL_USER_PROVIDER_MODE] for a server that keeps local accounts.
 *
 * `Ok(null)` means this source has no authority over the server (for example a server that is not
 * backed by a resource), and the caller falls back to configuration. An error means the source is
 * authoritative but could not determine a mode; callers must not fall back to local login then.
 */
interface HostedUserProviderModeSource {
    suspend fun userProviderMode(asInstanceId: String): IdkResult<String?, IdkError>
}

const val FEDERATED_USER_PROVIDER_MODE: String = "federated"
const val LOCAL_USER_PROVIDER_MODE: String = "local"
