/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.QrCodeOptions
import com.sphereon.openid.oid4vp.universal.QrCodeService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Platform-specific QR code generation.
 *
 * This is implemented via expect/actual to allow platform-specific
 * QR code libraries to be used.
 */
expect fun createPlatformQrCodeService(): QrCodeService

/**
 * Fallback QR code service that returns a placeholder data URI.
 *
 * Default DI binding — JVM overrides with JvmQrCodeService for real QR generation.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<QrCodeService>())
class FallbackQrCodeService : QrCodeService {
    override fun generateDataUri(content: String, options: QrCodeOptions): String {
        // Return a minimal 1x1 transparent PNG as a placeholder
        // In production, the JVM implementation with qrcode-kotlin will be used
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
