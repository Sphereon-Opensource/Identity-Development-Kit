/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.wallet.runner.oidf

import com.sphereon.oidf.conformance.OidfSuiteConfigPreprocessor
import com.sphereon.wallet.runner.oauth.Rfc7523PrivateKeyJwtClientAssertionProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.readText

internal fun oidfSuitePrivateKeyJwtClientAssertionProvider(
    suiteDir: Path,
    relativePath: String = OidfSuiteConfigPreprocessor.VCI_WALLET_CLIENT_AUTH_CONFIG,
    clock: Clock = Clock.systemUTC(),
): Rfc7523PrivateKeyJwtClientAssertionProvider {
    val root = Json.parseToJsonElement(suiteDir.resolve(relativePath).readText()).jsonObject
    val jwk =
        root
            .getValue("client")
            .jsonObject
            .getValue("jwks")
            .jsonObject
            .getValue("keys")
            .jsonArray
            .first()
            .jsonObject
    return Rfc7523PrivateKeyJwtClientAssertionProvider.fromP256Jwk(jwk, clock)
}
