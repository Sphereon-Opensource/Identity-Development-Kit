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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigBootstrapGuardJvmTest {
    @Test
    fun contextRegistrationStateIsThreadLocalOnJvm() {
        val registrationStarted = CountDownLatch(1)
        val releaseRegistration = CountDownLatch(1)
        val registrationThread =
            Thread {
                ConfigBootstrapGuard.withContextRegistration {
                    registrationStarted.countDown()
                    assertTrue(ConfigBootstrapGuard.isContextRegistrationInProgress())
                    assertTrue(releaseRegistration.await(5, TimeUnit.SECONDS))
                }
            }.apply {
                name = "config-bootstrap-guard-test"
                start()
            }

        assertTrue(registrationStarted.await(5, TimeUnit.SECONDS))
        try {
            assertFalse(ConfigBootstrapGuard.isContextRegistrationInProgress())
        } finally {
            releaseRegistration.countDown()
            registrationThread.join(5_000)
        }
    }
}
