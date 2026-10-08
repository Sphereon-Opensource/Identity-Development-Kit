package com.sphereon.core.api.json

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonSupportConcurrentRegistrationTest {
    @Test
    fun registrationDuringModuleConstructionUsesAStableSnapshot() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstInvocation = AtomicBoolean(true)
        val lateInvoked = AtomicBoolean(false)
        val registrationId = "concurrent-json-${UUID.randomUUID()}"
        JsonSupport.register(registrationId) {
            if (firstInvocation.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val construction = CompletableFuture.supplyAsync { JsonSupport.module }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS), "module construction reaches the registered builder")
            JsonSupport.register("$registrationId-late") { lateInvoked.set(true) }
        } finally {
            release.countDown()
        }
        assertNotNull(construction.get(10, TimeUnit.SECONDS))
        assertNotNull(JsonSupport.module, "subsequent construction includes the new registration")
        assertTrue(lateInvoked.get())
    }
}
