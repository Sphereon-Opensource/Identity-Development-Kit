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

package com.sphereon.oauth2.server.authorization.impl.dpop

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * In-memory [DpopNonceManager] for the IDK authorization server. Holds a small rolling window
 * of recently-issued nonces; older entries age out after the configured TTL. Production
 * deployments behind a load balancer must replace this with a distributed implementation so all
 * AS instances accept the same nonce window.
 *
 * Configuration keys (read from `AppConfigService`):
 * - `oauth2.dpop.nonce.window-size` — number of recent nonces retained (default 5)
 * - `oauth2.dpop.nonce.ttl-seconds` — per-nonce lifetime in seconds (default 300)
 * - `oauth2.dpop.nonce.rotation-seconds` — how often [currentNonce] mints a new active nonce
 *   (default 60). Independent of TTL: a nonce keeps validating after rotation until its TTL
 *   elapses, so client retries with a slightly-stale-but-still-valid nonce succeed.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DpopNonceManager>())
class InMemoryDpopNonceManagerImpl(
    private val appConfigService: AppConfigService,
    private val secureRandom: SecureRandom,
) : DpopNonceManager {
    private val entries = mutableMapOf<String, Instant>()
    private var activeNonce: String? = null
    private var activeNonceMintedAt: Instant? = null

    override suspend fun currentNonce(): String {
        val now = Clock.System.now()
        evictExpired(now)
        val current = activeNonce
        val mintedAt = activeNonceMintedAt
        val rotationInterval = rotationInterval()
        if (current != null && mintedAt != null && now < mintedAt + rotationInterval && entries.containsKey(current)) {
            return current
        }
        return mintAndStore(now)
    }

    override suspend fun rotate(): String {
        val now = Clock.System.now()
        evictExpired(now)
        return mintAndStore(now)
    }

    override suspend fun isValid(nonce: String): Boolean {
        if (nonce.isBlank()) return false
        val now = Clock.System.now()
        val expiresAt = entries[nonce] ?: return false
        if (now >= expiresAt) {
            entries.remove(nonce)
            return false
        }
        return true
    }

    private suspend fun mintAndStore(now: Instant): String {
        val nonce = secureRandom.newToken(lengthBytes = NONCE_BYTES, encoding = Encoding.BASE64URL)
        val ttl = nonceTtl()
        entries[nonce] = now + ttl
        activeNonce = nonce
        activeNonceMintedAt = now
        // Bound the map at the configured window size; drop the soonest-to-expire surplus entries.
        val windowSize = windowSize()
        if (entries.size > windowSize) {
            val ordered = entries.entries.sortedBy { it.value }
            val excess = entries.size - windowSize
            ordered.take(excess).forEach { entries.remove(it.key) }
        }
        return nonce
    }

    private fun evictExpired(now: Instant) {
        val expired = entries.entries.filter { now >= it.value }.map { it.key }
        expired.forEach { entries.remove(it) }
    }

    private fun nonceTtl(): Duration = (appConfigService.getProperty(CONFIG_TTL_SECONDS, Long::class, DEFAULT_TTL_SECONDS) ?: DEFAULT_TTL_SECONDS).seconds

    private fun rotationInterval(): Duration = (appConfigService.getProperty(CONFIG_ROTATION_SECONDS, Long::class, DEFAULT_ROTATION_SECONDS) ?: DEFAULT_ROTATION_SECONDS).seconds

    private fun windowSize(): Int = appConfigService.getProperty(CONFIG_WINDOW_SIZE, Int::class, DEFAULT_WINDOW_SIZE) ?: DEFAULT_WINDOW_SIZE

    private companion object {
        const val NONCE_BYTES = 24
        const val CONFIG_TTL_SECONDS = "oauth2.dpop.nonce.ttl-seconds"
        const val CONFIG_ROTATION_SECONDS = "oauth2.dpop.nonce.rotation-seconds"
        const val CONFIG_WINDOW_SIZE = "oauth2.dpop.nonce.window-size"
        val DEFAULT_TTL_SECONDS: Long = 5.minutes.inWholeSeconds
        val DEFAULT_ROTATION_SECONDS: Long = 1.minutes.inWholeSeconds
        const val DEFAULT_WINDOW_SIZE: Int = 5
    }
}
