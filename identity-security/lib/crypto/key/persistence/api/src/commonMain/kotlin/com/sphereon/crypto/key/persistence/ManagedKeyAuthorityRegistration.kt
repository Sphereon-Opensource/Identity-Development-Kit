/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.key.persistence

/**
 * Optional authority-side registration hook for a persisted external key reference.
 *
 * Plain IDK deployments have no central secret authority and use the no-op implementation. An
 * enterprise KMS deployment replaces it so a reference registered through REST also receives the
 * authority binding required by later permit-backed operations.
 */
fun interface ManagedKeyAuthorityRegistration {
    suspend fun register(providerId: String, keyResourceId: String): Boolean
}
