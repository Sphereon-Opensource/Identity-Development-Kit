/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.conf

import kotlinx.atomicfu.atomic

internal actual object ConfigBootstrapRegistrationState {
    private val activeRegistrations = atomic(0)

    actual fun isInProgress(): Boolean = activeRegistrations.value > 0

    actual fun <T> withRegistration(block: () -> T): T {
        activeRegistrations.incrementAndGet()
        return try {
            block()
        } finally {
            activeRegistrations.decrementAndGet()
        }
    }
}
