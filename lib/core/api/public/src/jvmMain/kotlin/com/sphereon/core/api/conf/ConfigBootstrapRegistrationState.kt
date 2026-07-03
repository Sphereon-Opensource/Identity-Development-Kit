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

internal actual object ConfigBootstrapRegistrationState {
    private val activeRegistrations = ThreadLocal.withInitial { 0 }

    actual fun isInProgress(): Boolean = activeRegistrations.get() > 0

    actual fun <T> withRegistration(block: () -> T): T {
        activeRegistrations.set(activeRegistrations.get() + 1)
        return try {
            block()
        } finally {
            val next = activeRegistrations.get() - 1
            if (next <= 0) {
                activeRegistrations.remove()
            } else {
                activeRegistrations.set(next)
            }
        }
    }
}
