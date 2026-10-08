/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.key.persistence.impl

import com.sphereon.crypto.key.persistence.ManagedKeyAuthorityRegistration

class NoOpManagedKeyAuthorityRegistration : ManagedKeyAuthorityRegistration {
    override suspend fun register(providerId: String, keyResourceId: String): Boolean = true
}
