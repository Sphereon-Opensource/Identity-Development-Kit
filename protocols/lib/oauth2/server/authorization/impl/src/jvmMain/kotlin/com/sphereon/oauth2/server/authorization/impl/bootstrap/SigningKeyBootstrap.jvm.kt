/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.bootstrap

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AppConsoleLogServiceImpl
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.session.AppCommandInvoker
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.context.PrincipalInput
import com.sphereon.di.context.TenantInput
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import kotlinx.coroutines.runBlocking

/**
 * Blocking convenience wrapper around [ensureActiveSigningKey] for
 * non-suspending boot threads (typical service `main()` / Spring
 * `@PostConstruct` / similar). Wraps the suspend version in
 * `runBlocking`; safe here because the work runs once at startup, off
 * the request path.
 */
fun ensureActiveSigningKeyBlocking(
    appCommandInvoker: AppCommandInvoker,
    signingKeyStore: SigningKeyStore,
    tenantInput: TenantInput,
    principalInput: PrincipalInput,
    tenantId: String = "default",
    algorithm: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
    logService: LogService = AppConsoleLogServiceImpl(),
): IdkResult<String, IdkError> =
    runBlocking {
        ensureActiveSigningKey(
            appCommandInvoker = appCommandInvoker,
            signingKeyStore = signingKeyStore,
            tenantInput = tenantInput,
            principalInput = principalInput,
            tenantId = tenantId,
            algorithm = algorithm,
            logService = logService,
        )
    }
