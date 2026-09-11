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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.percentDecode
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.percentDecode
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.holder.ParseCredentialOfferArgs
import com.sphereon.openid.oid4vci.holder.ParseCredentialOfferCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.http.isSuccess

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ParseCredentialOfferCommand>())
class ParseCredentialOfferCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<ParseCredentialOfferArgs, CredentialOffer, IdkError>(
        commandId = ParseCredentialOfferCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ParseCredentialOfferArgs>(),
        outputTypeToken = typeToken<CredentialOffer>(),
    ),
    ParseCredentialOfferCommand {
    override val commandId: String get() = ParseCredentialOfferCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ParseCredentialOfferArgs

    override suspend fun doExecute(
        args: ParseCredentialOfferArgs,
        applyDuring: (ParseCredentialOfferArgs) -> ParseCredentialOfferArgs,
    ): IdkResult<CredentialOffer, IdkError> {
        val applied = applyDuring(args)
        val raw = applied.rawOffer.trim()

        val offerJson: String =
            when {
                raw.startsWith("{") -> {
                    raw
                }

                raw.startsWith("openid-credential-offer://") || raw.startsWith("https://") -> {
                    val queryString =
                        extractQueryString(raw)
                            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "No query string found in URI: $raw"))

                    val parameterNames = parseQueryParameterNames(queryString)
                    if (parameterNames.size != 1 || parameterNames.single() !in CREDENTIAL_OFFER_PARAMETER_NAMES) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Credential Offer Endpoint URI must contain exactly one query parameter: credential_offer or credential_offer_uri",
                            ),
                        )
                    }
                    val params = parseQueryParams(queryString)
                    val parameterName = parameterNames.single()
                    val parameterValue = params[parameterName].orEmpty()
                    if (parameterValue.isBlank()) {
                        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "$parameterName query parameter is empty"))
                    }

                    when (parameterName) {
                        "credential_offer_uri" -> {
                            // OID4VCI 1.0 Final Section 4.1.3: fetch the offer by HTTPS reference.
                            val offerUri = parameterValue
                            if (!offerUri.startsWith("https://", ignoreCase = true)) {
                                return Err(
                                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                                        message = "credential_offer_uri must use the https scheme",
                                    ),
                                )
                            }
                            fetchCredentialOfferByReference(offerUri)
                                ?: return Err(
                                    IdkError.fromString(
                                        message = "Failed to fetch credential offer from URI: $offerUri",
                                        code = "CREDENTIAL_OFFER_FETCH_FAILED",
                                    ),
                                )
                        }

                        "credential_offer" -> parameterValue
                        else -> error("Credential Offer parameter name was validated before dispatch")
                    }
                }

                else -> {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unrecognized credential offer format. Expected JSON object, openid-credential-offer:// URI, or HTTPS URL"))
                }
            }

        val offer =
            try {
                Oid4vciJson.lenient.decodeFromString(CredentialOffer.serializer(), offerJson)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to parse credential offer JSON: ${expected.message}", throwable = expected))
            }

        if (offer.credentialIssuer.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "credential_issuer is required and must not be blank"))
        }

        if (offer.credentialConfigurationIds.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "credential_configuration_ids must contain at least one entry"))
        }

        return Ok(offer)
    }

    /**
     * Fetches a credential offer JSON string from a by-reference URI.
     *
     * Per OID4VCI 1.0 Final Section 4.1.3: HTTP GET the credential_offer_uri and return the JSON response body.
     * Returns null on network or HTTP error (caller converts to Err).
     */
    private suspend fun fetchCredentialOfferByReference(offerUri: String): String? {
        val httpClient =
            try {
                httpClientFactory.createClient(HttpClientOptions.createDefault())
            } catch (expected: Exception) {
                log.warn("Failed to create HTTP client for credential_offer_uri fetch: ${expected.message}")
                return null
            }
        return try {
            val response = httpClient.get(offerUri)
            if (!response.status.isSuccess()) {
                log.warn("credential_offer_uri fetch returned HTTP ${response.status.value}: $offerUri")
                return null
            }
            val responseContentType = response.contentType()
            if (responseContentType == null ||
                responseContentType.contentType != "application" ||
                responseContentType.contentSubtype != "json"
            ) {
                log.warn("credential_offer_uri fetch returned non-JSON Content-Type: $responseContentType")
                return null
            }
            response.bodyAsText()
        } catch (expected: Exception) {
            log.warn("Network error fetching credential_offer_uri $offerUri: ${expected.message}")
            null
        } finally {
            httpClient.close()
        }
    }

    private fun extractQueryString(uri: String): String? {
        val qIdx = uri.indexOf('?')
        return if (qIdx >= 0) {
            uri.substring(qIdx + 1)
        } else {
            null
        }
    }

    /**
     * Parses a query string into a map of key → decoded value.
     *
     * KMP commonMain compatible — no java.net.URLDecoder.
     */
    private fun parseQueryParams(queryString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (queryString.isBlank()) {
            return result
        }

        for (pair in queryString.split('&')) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx < 0) {
                result[pair.percentDecode(plusAsSpace = true)] = ""
            } else {
                val key = pair.substring(0, eqIdx).percentDecode(plusAsSpace = true)
                val value = pair.substring(eqIdx + 1).percentDecode(plusAsSpace = true)
                result[key] = value
            }
        }
        return result
    }

    private fun parseQueryParameterNames(queryString: String): List<String> =
        queryString
            .split('&')
            .filter { it.isNotBlank() }
            .map { pair -> pair.substringBefore('=').percentDecode(plusAsSpace = true) }

    private companion object {
        val CREDENTIAL_OFFER_PARAMETER_NAMES = setOf("credential_offer", "credential_offer_uri")
    }
}
