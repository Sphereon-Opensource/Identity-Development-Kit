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

package com.sphereon.wallet.cli

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.ObtainCredentialRequest
import com.sphereon.wallet.ObtainCredentialResult
import com.sphereon.wallet.WalletConfig
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.impl.WalletBootstrap
import kotlinx.coroutines.runBlocking

private const val DEFAULT_WALLET_INSTANCE_ID = "default-wallet"

fun main(args: Array<String>): Unit =
    runBlocking {
        val wallet = WalletBootstrap.create().wallet()

        when (val cmd = args.getOrNull(0)) {
            "create-key" -> {
                val result = wallet.createHolderKey(DEFAULT_WALLET_INSTANCE_ID)
                println(result.orFail("create-key"))
            }

            "accept-preauth" -> {
                val issuer = args.getOrElse(1) { error("Usage: accept-preauth <issuer> <preauth> <configId> [count]") }
                val preauth = args.getOrElse(2) { error("Usage: accept-preauth <issuer> <preauth> <configId> [count]") }
                val configId = args.getOrElse(3) { error("Usage: accept-preauth <issuer> <preauth> <configId> [count]") }
                val count = args.getOrNull(4)?.toIntOrNull() ?: 1

                val tokenSet = wallet.exchangePreAuthorizedCode(issuer, preauth).orFail("exchange pre-authorized code")
                val keyAlias = wallet.createHolderKey(DEFAULT_WALLET_INSTANCE_ID).orFail("create holder key")
                val obtainResult =
                    wallet
                        .obtainCredential(
                            ObtainCredentialRequest(
                                walletInstanceId = DEFAULT_WALLET_INSTANCE_ID,
                                credentialIssuer = issuer,
                                credentialConfigurationId = configId,
                                accessToken = tokenSet.accessToken,
                                cNonce = tokenSet.cNonce,
                                holderKeyAlias = keyAlias,
                                count = count,
                            ),
                        ).orFail("obtain credential")
                println(obtainResult.describe())
            }

            "auth-code" -> {
                val issuer = args.getOrElse(1) { error("Usage: auth-code <issuer> <clientId> <configId> [count]") }
                val clientId = args.getOrElse(2) { error("Usage: auth-code <issuer> <clientId> <configId> [count]") }
                val configId = args.getOrElse(3) { error("Usage: auth-code <issuer> <clientId> <configId> [count]") }
                val count = args.getOrNull(4)?.toIntOrNull() ?: 1

                val server = LoopbackRedirectServer()
                server.start()

                val start =
                    wallet
                        .startAuthorizationCodeFlow(
                            credentialIssuer = issuer,
                            config = WalletConfig(walletInstanceId = DEFAULT_WALLET_INSTANCE_ID, clientId = clientId, redirectUri = server.redirectUri),
                            scope = "openid profile email",
                        ).orFail("start authorization code flow")

                println("Open this URL and log in:\n${start.authorizationUrl}")

                val code =
                    try {
                        server.awaitCode(start.state)
                    } finally {
                        server.stop()
                    }

                val tokenSet = wallet.completeAuthorizationCodeFlow(start, code).orFail("complete authorization code flow")
                val keyAlias = wallet.createHolderKey(DEFAULT_WALLET_INSTANCE_ID).orFail("create holder key")
                val obtainResult =
                    wallet
                        .obtainCredential(
                            ObtainCredentialRequest(
                                walletInstanceId = DEFAULT_WALLET_INSTANCE_ID,
                                credentialIssuer = issuer,
                                credentialConfigurationId = configId,
                                accessToken = tokenSet.accessToken,
                                cNonce = tokenSet.cNonce,
                                holderKeyAlias = keyAlias,
                                count = count,
                            ),
                        ).orFail("obtain credential")
                println(obtainResult.describe())
            }

            "present" -> {
                val requestUri = args.getOrElse(1) { error("Usage: present <requestUri> <clientId>") }
                val clientId = args.getOrElse(2) { error("Usage: present <requestUri> <clientId>") }

                val result =
                    wallet
                        .present(
                            requestUri = requestUri,
                            config = WalletConfig(walletInstanceId = DEFAULT_WALLET_INSTANCE_ID, clientId = clientId, redirectUri = "http://localhost:8765/callback"),
                        ).orFail("present")
                println("Submitted: ${result.submitted}, redirectUri: ${result.redirectUri}")
            }

            "auth-code-present" -> {
                // Obtain a credential via the authorization-code flow AND present it to a
                // verifier in a single process. The wallet's credential store and holder KMS
                // are in-memory and per-process, so issuance and presentation must share one
                // process for the held credential (and its holder key) to survive into the
                // presentation step.
                val issuer =
                    args.getOrElse(1) {
                        error("Usage: auth-code-present <issuer> <clientId> <configId> <requestUri> <verifierClientId> [count]")
                    }
                val clientId =
                    args.getOrElse(2) {
                        error("Usage: auth-code-present <issuer> <clientId> <configId> <requestUri> <verifierClientId> [count]")
                    }
                val configId =
                    args.getOrElse(3) {
                        error("Usage: auth-code-present <issuer> <clientId> <configId> <requestUri> <verifierClientId> [count]")
                    }
                val requestUri =
                    args.getOrElse(4) {
                        error("Usage: auth-code-present <issuer> <clientId> <configId> <requestUri> <verifierClientId> [count]")
                    }
                val verifierClientId =
                    args.getOrElse(5) {
                        error("Usage: auth-code-present <issuer> <clientId> <configId> <requestUri> <verifierClientId> [count]")
                    }
                val count = args.getOrNull(6)?.toIntOrNull() ?: 1

                val server = LoopbackRedirectServer()
                server.start()

                val start =
                    wallet
                        .startAuthorizationCodeFlow(
                            credentialIssuer = issuer,
                            config = WalletConfig(walletInstanceId = DEFAULT_WALLET_INSTANCE_ID, clientId = clientId, redirectUri = server.redirectUri),
                            scope = "openid profile email",
                        ).orFail("start authorization code flow")

                println("Open this URL and log in:\n${start.authorizationUrl}")

                val code =
                    try {
                        server.awaitCode(start.state)
                    } finally {
                        server.stop()
                    }

                val tokenSet = wallet.completeAuthorizationCodeFlow(start, code).orFail("complete authorization code flow")
                val keyAlias = wallet.createHolderKey(DEFAULT_WALLET_INSTANCE_ID).orFail("create holder key")
                val obtainResult =
                    wallet
                        .obtainCredential(
                            ObtainCredentialRequest(
                                walletInstanceId = DEFAULT_WALLET_INSTANCE_ID,
                                credentialIssuer = issuer,
                                credentialConfigurationId = configId,
                                accessToken = tokenSet.accessToken,
                                cNonce = tokenSet.cNonce,
                                holderKeyAlias = keyAlias,
                                count = count,
                            ),
                        ).orFail("obtain credential")
                val doc = obtainResult.requireStored("auth-code-present")
                println("Obtained: ${doc.describe()}")

                val result =
                    wallet
                        .present(
                            requestUri = requestUri,
                            config = WalletConfig(walletInstanceId = DEFAULT_WALLET_INSTANCE_ID, clientId = verifierClientId, redirectUri = server.redirectUri),
                        ).orFail("present")
                println("Submitted: ${result.submitted}, redirectUri: ${result.redirectUri}")
            }

            "auth-code-present-multi" -> {
                // Obtain SEVERAL credential configurations via one authorization-code flow and then
                // present them to a verifier, all in a single process. Same per-process constraint as
                // auth-code-present (the wallet store + holder key are in-memory), extended to a
                // multi-credential presentation: the verifier's DCQL query references more than one
                // credential definition, so the wallet must hold all of them before presenting.
                //
                // configIds is a comma-separated list (e.g. "EmployeeCredential,BusinessCardMdoc").
                // Each configuration is obtained against its own fresh holder key. The module's
                // wallet.obtainCredential pulls a fresh c_nonce from the issuer's nonce endpoint per
                // obtain when one is advertised, so the proof-of-possession stays valid across obtains;
                // the token-set c_nonce is the fallback seed. This branch only sequences the calls.
                val issuer =
                    args.getOrElse(1) {
                        error("Usage: auth-code-present-multi <issuer> <clientId> <configIdsCsv> <requestUri> <verifierClientId> [count]")
                    }
                val clientId =
                    args.getOrElse(2) {
                        error("Usage: auth-code-present-multi <issuer> <clientId> <configIdsCsv> <requestUri> <verifierClientId> [count]")
                    }
                val configIds =
                    args
                        .getOrElse(3) {
                            error("Usage: auth-code-present-multi <issuer> <clientId> <configIdsCsv> <requestUri> <verifierClientId> [count]")
                        }.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                val requestUri =
                    args.getOrElse(4) {
                        error("Usage: auth-code-present-multi <issuer> <clientId> <configIdsCsv> <requestUri> <verifierClientId> [count]")
                    }
                val verifierClientId =
                    args.getOrElse(5) {
                        error("Usage: auth-code-present-multi <issuer> <clientId> <configIdsCsv> <requestUri> <verifierClientId> [count]")
                    }
                val count = args.getOrNull(6)?.toIntOrNull() ?: 1

                val server = LoopbackRedirectServer()
                server.start()

                val start =
                    wallet
                        .startAuthorizationCodeFlow(
                            credentialIssuer = issuer,
                            config = WalletConfig(walletInstanceId = DEFAULT_WALLET_INSTANCE_ID, clientId = clientId, redirectUri = server.redirectUri),
                            scope = "openid profile email",
                        ).orFail("start authorization code flow")

                println("Open this URL and log in:\n${start.authorizationUrl}")

                val code =
                    try {
                        server.awaitCode(start.state)
                    } finally {
                        server.stop()
                    }

                val tokenSet = wallet.completeAuthorizationCodeFlow(start, code).orFail("complete authorization code flow")
                for (configId in configIds) {
                    val keyAlias = wallet.createHolderKey(DEFAULT_WALLET_INSTANCE_ID).orFail("create holder key for $configId")
                    val obtainResult =
                        wallet
                            .obtainCredential(
                                ObtainCredentialRequest(
                                    walletInstanceId = DEFAULT_WALLET_INSTANCE_ID,
                                    credentialIssuer = issuer,
                                    credentialConfigurationId = configId,
                                    accessToken = tokenSet.accessToken,
                                    cNonce = tokenSet.cNonce,
                                    holderKeyAlias = keyAlias,
                                    count = count,
                                ),
                            ).orFail("obtain credential $configId")
                    val doc = obtainResult.requireStored("auth-code-present-multi $configId")
                    println("Obtained: ${doc.describe()}")
                }

                val result =
                    wallet
                        .present(
                            requestUri = requestUri,
                            config = WalletConfig(walletInstanceId = DEFAULT_WALLET_INSTANCE_ID, clientId = verifierClientId, redirectUri = server.redirectUri),
                        ).orFail("present")
                println("Submitted: ${result.submitted}, redirectUri: ${result.redirectUri}")
            }

            "list" -> {
                val metadataList = wallet.credentials.listMetadata(DEFAULT_WALLET_INSTANCE_ID).orFail("list credentials")
                if (metadataList.isEmpty()) {
                    println("No credentials in wallet.")
                } else {
                    metadataList.forEach { meta ->
                        println(meta.describe())
                    }
                }
            }

            else -> {
                println(
                    """
Sphereon Wallet CLI

Commands:
  create-key
      Create a new holder key and print its alias.

  accept-preauth <issuer> <preauth> <configId> [count]
      Exchange a pre-authorized code for a credential.

  auth-code <issuer> <clientId> <configId> [count]
      Run an authorization code flow (opens browser redirect server on port 8765).

  present <requestUri> <clientId>
      Respond to a verifier's authorization request URI.

  auth-code-present <issuer> <clientId> <configId> <requestUri> <verifierClientId> [count]
      Obtain a credential via the authorization-code flow and immediately present it
      to a verifier, in a single process (required: the wallet store + holder key are
      in-memory and per-process).

  auth-code-present-multi <issuer> <clientId> <configIdsCsv> <requestUri> <verifierClientId> [count]
      Obtain SEVERAL credential configurations (comma-separated config ids) via one
      authorization-code flow and present them together to a verifier whose DCQL query
      references more than one credential definition. Single process, same as above.

  list
      List all stored credentials.
                    """.trimIndent(),
                )
            }
        }
    }

// ---------------------------------------------------------------------------
// Tiny inline helpers — avoids pulling in extra libs for CLI error handling
// ---------------------------------------------------------------------------

private fun <V> IdkResult<V, IdkError>.orFail(operation: String): V {
    if (isErr) {
        System.err.println("ERROR during $operation: ${error.message}")
        kotlin.system.exitProcess(1)
    }
    return value
}

private fun CredentialRecord.describe(): String {
    val name = displayName() ?: "(no display name)"
    val typeRefs = credentialTypeRefs.joinToString { "${it.kind}:${it.value}" }
    return "CredentialRecord[id=$id, types=$typeRefs, name=$name, instances=${instances.size}]"
}

private fun ObtainCredentialResult.describe(): String =
    when (this) {
        is ObtainCredentialResult.Stored -> record.describe()
        is ObtainCredentialResult.Deferred -> "DeferredIssuance[id=${session.id}, transactionId=${session.deferred?.transactionId}]"
    }

private fun ObtainCredentialResult.requireStored(operation: String): CredentialRecord =
    when (this) {
        is ObtainCredentialResult.Stored -> record
        is ObtainCredentialResult.Deferred -> error("$operation returned deferred issuance session ${session.id}; poll it before presentation")
    }

private fun CredentialMetadata.describe(): String {
    val name = display.credentialDisplay.firstOrNull()?.name ?: "(no display name)"
    val typeRefs = credentialTypeRefs.joinToString { "${it.kind}:${it.value}" }
    return "CredentialRecord[id=$credentialRecordId, types=$typeRefs, name=$name, instances=$instanceCount, status=${lifecycleSummary.lifecycleState}]"
}
