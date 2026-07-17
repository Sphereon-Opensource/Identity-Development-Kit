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

package com.sphereon.wallet.wscd.testfixtures

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.wallet.unit.SecureComponentUsage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class WalletBootstrapTest {
    // Verifies WalletBootstrap's composition root wires the local, software-backed WSCA/WSCD
    // stack correctly end to end, reaching the session-scoped Wsca directly via WscaGraph.
    @Test
    fun walletBootstrapCreatesHolderKeyWithLocalWscd() =
        runTest {
            val wsca = WalletBootstrap.create().wsca()
            val key =
                wsca.ensureKey(
                    walletUnitId = "bootstrap-test-wallet",
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertTrue(key.isOk, "ensureKey should succeed: ${if (key.isErr) key.error else ""}")
            val keyId = key.value.keyRef ?: key.value.keyId
            assertTrue(keyId.isNotBlank(), "key identifier should not be blank")
        }
}
