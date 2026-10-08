/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.core.api.conf

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Greenfield secret-resolution boundary.
 *
 * [secretId] is an opaque server-generated handle. Provider identifiers, environment-variable
 * names, backend paths, partitions, and structured provider keys are deliberately absent.
 */
fun interface OpaqueSecretResolver {
    suspend fun resolve(secretId: String): IdkResult<String, IdkError>
}
