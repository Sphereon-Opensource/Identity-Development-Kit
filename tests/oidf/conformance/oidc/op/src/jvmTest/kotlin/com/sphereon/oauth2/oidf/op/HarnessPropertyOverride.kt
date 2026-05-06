/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.oidf.op

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource

/**
 * Cleanup-safe wrapper around [DefaultPrincipalMapPropertySource] for OIDF harness tests.
 *
 * `DefaultPrincipalMapPropertySource` is a JVM-global singleton: properties published into it
 * survive across test classes when Gradle reuses a test worker process. A test that throws
 * before reaching its `@AfterTest` cleanup leaks its overrides into the next test, masking
 * subsequent failures or producing flakes that depend on test execution order.
 *
 * `HarnessPropertyOverride` collects every key it publishes and removes them on [close],
 * regardless of whether the test body succeeded. Intended use is a per-test instance held in
 * `@BeforeTest` and closed in `@AfterTest`:
 *
 * ```kotlin
 * private lateinit var overrides: HarnessPropertyOverride
 *
 * @BeforeTest fun setUp() {
 *     overrides = HarnessPropertyOverride(
 *         "oauth2.servers.default.mtls" to "SUPPORTED",
 *         "oauth2.clients.${'$'}clientId.tls-client-auth-subject-dn" to "CN=fixture",
 *     )
 * }
 *
 * @AfterTest fun tearDown() = overrides.close()
 * ```
 *
 * Tests that need to publish keys after construction (e.g. derive a value from a port assigned
 * at fixture startup) call [publish] on the live instance:
 *
 * ```kotlin
 * overrides.publish("oauth2.clients.${'$'}clientId.tls-client-auth-subject-dn", subjectDn)
 * ```
 *
 * For tests that only need a single short-lived publish, [use] runs a block then closes:
 *
 * ```kotlin
 * HarnessPropertyOverride("oauth2.servers.default.jarm" to "REQUIRED").use { ... }
 * ```
 */
class HarnessPropertyOverride private constructor(
    private val source: DefaultPrincipalMapPropertySource,
) : AutoCloseable {
    /**
     * Synchronised so a test can publish from `@BeforeTest` and a fixture callback in parallel
     * without losing entries. The OIDF harness fixtures are single-threaded today, but cheap
     * synchronisation here removes the foot-gun.
     */
    private val keys: MutableSet<String> = mutableSetOf()

    private var closed: Boolean = false

    constructor(initial: Map<String, String> = emptyMap()) : this(DefaultPrincipalMapPropertySource) {
        initial.forEach { (k, v) -> publish(k, v) }
    }

    constructor(vararg pairs: Pair<String, String>) : this(pairs.toMap())

    /**
     * Publish a property to [DefaultPrincipalMapPropertySource] and remember the key for
     * cleanup. Re-publishing the same key is allowed; only the latest value persists.
     */
    fun publish(
        key: String,
        value: String,
    ): HarnessPropertyOverride {
        check(!closed) { "HarnessPropertyOverride is closed; create a fresh instance" }
        synchronized(keys) {
            source.addProperty(key, value)
            keys.add(key)
        }
        return this
    }

    /**
     * Bulk-publish helper: equivalent to calling [publish] for every entry, returns `this` for
     * chaining inside `@BeforeTest`.
     */
    fun publishAll(properties: Map<String, String>): HarnessPropertyOverride {
        properties.forEach { (k, v) -> publish(k, v) }
        return this
    }

    /**
     * Remove every published key from the singleton property source. Idempotent: closing a
     * closed instance is a no-op so finally-blocks remain safe.
     */
    override fun close() {
        if (closed) return
        synchronized(keys) {
            keys.forEach { source.deleteProperty(it) }
            keys.clear()
            closed = true
        }
    }
}
