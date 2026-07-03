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

/**
 * Marks synchronous context/config bootstrap sections.
 *
 * Property sources that need remote data must avoid starting a first remote fetch
 * while user/session scopes are still being registered. Remote fetches commonly
 * need those scopes themselves, so fetching during registration can self-block
 * under native images.
 */
object ConfigBootstrapGuard {
    fun isContextRegistrationInProgress(): Boolean = ConfigBootstrapRegistrationState.isInProgress()

    fun <T> withContextRegistration(block: () -> T): T = ConfigBootstrapRegistrationState.withRegistration(block)
}

internal expect object ConfigBootstrapRegistrationState {
    fun isInProgress(): Boolean

    fun <T> withRegistration(block: () -> T): T
}
