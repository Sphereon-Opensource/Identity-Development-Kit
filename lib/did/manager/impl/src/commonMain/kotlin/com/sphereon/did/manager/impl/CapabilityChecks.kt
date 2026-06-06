/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.DidProviderRegistry

/**
 * Capability precheck shared between [DidManagerServiceImpl] and the IDK-20 service-command
 * impls in this module. Fails fast with `UNSUPPORTED_OPERATION` (mapped to HTTP 422 by
 * `DefaultRestErrorRenderer`) when the DID method's declared capabilities do not permit the
 * requested operation.
 *
 * Call this from any service-command impl that mutates DID-document-affecting state. Without
 * the precheck, an update against an immutable method would slip past the capability gate and
 * only be rejected later by the provider — or, worse, surface as a persistence-layer error.
 * See VDX-infra-1tx.
 */
internal fun requireCapability(
    providerRegistry: DidProviderRegistry,
    method: String,
    operationName: String,
    check: (DidMethodCapabilities) -> Boolean,
): IdkResult<Unit, IdkError> {
    val caps =
        providerRegistry.getCapabilities(method)
            ?: return Err(
                IdkError.NOT_FOUND_ERROR(message = "No provider registered for DID method: $method"),
            )
    return if (check(caps)) {
        Ok(Unit)
    } else {
        Err(
            IdkError.fromString(
                message = "DID method '$method' does not support $operationName",
                code = "UNSUPPORTED_OPERATION",
                category = ErrorCategory.UNPROCESSABLE_ENTITY,
            ),
        )
    }
}
