package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.core.api.Ok
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityGateRequest
import com.sphereon.wallet.interaction.WalletSecurityGateResult
import com.sphereon.wallet.interaction.WalletSecurityOperation
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaUserAuthentication
import com.sphereon.wallet.wsca.WscaUserAuthenticationFactor
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WscaWalletSecurityGateTest {
    @Test
    fun `enrolled and enrollment challenges preserve the exact operation key and binding`() = runTest {
        for (factor in listOf(null, WscaUserAuthenticationFactor.PASSKEY)) {
            val gate = gate(factor)
            val request = WalletSecurityGateRequest(
                operationId = "interaction-proof-1",
                operation = WalletSecurityOperation.PRESENT_PROOF,
                walletUnitId = "wallet-unit-1",
                keyRef = "credential-proof-key-1",
                operationBinding = "prepared-operation-1",
                operationHash = "sha256:proof-digest-1",
                nonce = "issuer-nonce-1",
                audience = "https://issuer.example/oid4vci/issuer-1",
                requiredAssurance = WalletSecurityAssurance.PASSKEY,
            )

            val result = assertIs<WalletSecurityGateResult.ChallengeRequired>(gate.authorize(request))
            val arguments = result.challenge.arguments
            assertEquals(request.keyRef, arguments["operationKeyRef"])
            assertEquals(request.operationBinding, arguments["operation_binding"])
            assertEquals(request.operationHash, arguments["operationHash"])
            assertEquals(request.nonce, arguments["nonce"])
            assertEquals(request.audience, arguments["audience"])
        }
    }

    private fun gate(factor: WscaUserAuthenticationFactor?): WscaWalletSecurityGate {
        val authentication = proxy<WscaUserAuthentication> { method ->
            check(method == "primaryFactor") { "Unexpected authentication call: $method" }
            Ok(factor)
        }
        val wsca = proxy<Wsca> { method ->
            check(method == "getUserAuthentication") { "Unexpected WSCA call: $method" }
            authentication
        }
        return WscaWalletSecurityGate(wsca)
    }

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            call(method.name)
        } as T
}
