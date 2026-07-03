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

package com.sphereon.wallet.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.data.device.IssuerSignedCborCodec
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.service.PkceService
import com.sphereon.oauth2.client.util.buildUrl
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.holder.Oid4vciHolder
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.sdjwt.vc.SdJwtVcVerificationOpts
import com.sphereon.sdjwt.vc.VerifySdJwtVcArgs
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.wallet.AuthCodeStart
import com.sphereon.wallet.ObtainCredentialRequest
import com.sphereon.wallet.ObtainCredentialResult
import com.sphereon.wallet.PresentationResult
import com.sphereon.wallet.RefreshCredentialRequest
import com.sphereon.wallet.RefreshCredentialResult
import com.sphereon.wallet.ResumeDeferredIssuanceRequest
import com.sphereon.wallet.TokenSet
import com.sphereon.wallet.Wallet
import com.sphereon.wallet.WalletConfig
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.credential.BodyStorageKind
import com.sphereon.wallet.credential.BodyStorageRef
import com.sphereon.wallet.credential.CredentialBindingRef
import com.sphereon.wallet.credential.CredentialClaimDisplay
import com.sphereon.wallet.credential.CredentialClaimMetadata
import com.sphereon.wallet.credential.CredentialDisplayMetadata
import com.sphereon.wallet.credential.CredentialDisplayProperties
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialImageProperties
import com.sphereon.wallet.credential.CredentialInstance
import com.sphereon.wallet.credential.CredentialLifecycleState
import com.sphereon.wallet.credential.CredentialLogoProperties
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialRefreshMethod
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.CredentialTypeRefKind
import com.sphereon.wallet.credential.CredentialTypeRefSource
import com.sphereon.wallet.credential.CredentialValidityState
import com.sphereon.wallet.credential.CredentialValidityWindow
import com.sphereon.wallet.credential.DeferredIssuanceError
import com.sphereon.wallet.credential.DeferredIssuanceState
import com.sphereon.wallet.credential.IdentifierRef
import com.sphereon.wallet.credential.IssuanceDiagnostic
import com.sphereon.wallet.credential.IssuanceDiagnosticCode
import com.sphereon.wallet.credential.IssuanceNotificationState
import com.sphereon.wallet.credential.IssuanceProvenance
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.KeyRef
import com.sphereon.wallet.credential.RetryPolicy
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import com.sphereon.openid.oid4vc.common.DisplayProperties as Oid4vcDisplayProperties
import com.sphereon.openid.oid4vc.common.ImageProperties as Oid4vcImageProperties
import com.sphereon.openid.oid4vc.common.LogoProperties as Oid4vcLogoProperties
import com.sphereon.openid.oid4vci.common.model.ClaimDisplay as Oid4vciClaimDisplay
import com.sphereon.openid.oid4vci.common.model.CredentialClaim as Oid4vciCredentialClaim
import com.sphereon.openid.oid4vp.holder.WalletConfig as Oid4vpWalletConfig

private val walletIssuerSignedCborCodec: IssuerSignedCborCodec = IssuerSignedCborCodecImpl()
private val walletMobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec = MobileSecurityObjectCborCodecImpl()

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Wallet>())
class WalletImpl(
    override val credentials: WalletCredentialStore,
    override val issuanceSessions: WalletIssuanceSessionStore,
    private val oid4vciHolder: Oid4vciHolder,
    private val oid4vpHolder: Oid4vpHolder,
    private val oauth2Client: OAuth2Client,
    private val pkceService: PkceService,
    private val keyManagerService: KeyManagerService,
    private val identityResolver: WalletIdentityResolver,
    private val subjectExtractor: CredentialSubjectExtractor,
    /**
     * SD-JWT VC verification command (`sdjwt.vc.verify`). The wallet verifies the ISSUER
     * signature of an issued `dc+sd-jwt`/`vc+sd-jwt` credential on receipt — resolving the
     * issuer key from the JWS protected-header `kid` (e.g. `did:jwk:<...>#0`) and checking
     * the signature — before storing it, reaching the SAME conclusion a verifier would.
     */
    private val verifySdJwtVcCommand: VerifySdJwtVcCommand,
) : Wallet {
    override suspend fun createHolderKey(
        walletInstanceId: String,
        alias: String?,
    ): IdkResult<String, IdkError> {
        if (walletInstanceId.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = "walletInstanceId", message = "walletInstanceId must not be blank"))
        }
        val managedAlias = holderKeyAlias(walletInstanceId = walletInstanceId, alias = alias)
        val result = keyManagerService.generateKeyResult(alias = managedAlias)
        if (result.isErr) return Err(result.error)
        val keyPair =
            result.value.keyPair
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "generateKeyResult returned no key pair"))
        return Ok(keyPair.alias)
    }

    override suspend fun startAuthorizationCodeFlow(
        credentialIssuer: String,
        config: WalletConfig,
        scope: String?,
    ): IdkResult<AuthCodeStart, IdkError> {
        val metadataResult = oid4vciHolder.resolveIssuerMetadata(credentialIssuer)
        if (metadataResult.isErr) return Err(metadataResult.error)

        val asResult = oid4vciHolder.selectAuthorizationServer(metadataResult.value)
        if (asResult.isErr) return Err(asResult.error)
        val resolvedAs = asResult.value

        val authorizationEndpoint =
            resolvedAs.authorizationEndpoint
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Authorization server has no authorization_endpoint"))
        val tokenEndpoint =
            resolvedAs.tokenEndpoint
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Authorization server has no token_endpoint"))

        val pkceResult = pkceService.createPkce(CreatePkceArgs(allowedMethods = listOf(PkceMethod.S256)))
        if (pkceResult.isErr) return Err(pkceResult.error)
        val pkce = pkceResult.value

        val state = Uuid.v4String()

        val authorizationUrl =
            buildUrl(
                authorizationEndpoint,
                mapOf(
                    "response_type" to "code",
                    "client_id" to config.clientId,
                    "redirect_uri" to config.redirectUri,
                    "scope" to scope,
                    "state" to state,
                    "code_challenge" to pkce.codeChallenge,
                    "code_challenge_method" to pkce.codeChallengeMethod.value,
                ),
            )

        return Ok(
            AuthCodeStart(
                authorizationUrl = authorizationUrl,
                state = state,
                codeVerifier = pkce.codeVerifier,
                redirectUri = config.redirectUri,
                tokenEndpoint = tokenEndpoint,
                clientId = config.clientId,
            ),
        )
    }

    override suspend fun completeAuthorizationCodeFlow(
        start: AuthCodeStart,
        code: String,
    ): IdkResult<TokenSet, IdkError> {
        val tokenResult =
            oid4vciHolder.exchangeAuthorizationCode(
                tokenEndpoint = start.tokenEndpoint,
                code = code,
                codeVerifier = start.codeVerifier,
                redirectUri = start.redirectUri,
                clientId = start.clientId,
            )
        if (tokenResult.isErr) return Err(tokenResult.error)
        val tokenResponse = tokenResult.value

        return Ok(
            TokenSet(
                accessToken = tokenResponse.accessToken,
                cNonce = tokenResponse.cNonce,
            ),
        )
    }

    override suspend fun exchangePreAuthorizedCode(
        credentialIssuer: String,
        preAuthorizedCode: String,
        txCode: String?,
        config: WalletConfig?,
    ): IdkResult<TokenSet, IdkError> {
        val metadataResult = oid4vciHolder.resolveIssuerMetadata(credentialIssuer)
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value

        val asResult = oid4vciHolder.selectAuthorizationServer(metadata)
        if (asResult.isErr) return Err(asResult.error)
        val resolvedAs = asResult.value

        val tokenEndpoint =
            resolvedAs.tokenEndpoint
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Authorization server has no token_endpoint"))

        val tokenResult =
            oid4vciHolder.exchangePreAuthorizedCode(
                tokenEndpoint = tokenEndpoint,
                preAuthorizedCode = preAuthorizedCode,
                txCode = txCode,
                clientId = config?.clientId,
                redirectUri = config?.redirectUri,
            )
        if (tokenResult.isErr) return Err(tokenResult.error)
        val tokenResponse = tokenResult.value

        return Ok(
            TokenSet(
                accessToken = tokenResponse.accessToken,
                cNonce = tokenResponse.cNonce,
            ),
        )
    }

    override suspend fun obtainCredential(request: ObtainCredentialRequest): IdkResult<ObtainCredentialResult, IdkError> {
        val metadataResult = oid4vciHolder.resolveIssuerMetadata(request.credentialIssuer)
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value

        val credConfig =
            metadata.credentialConfigurationsSupported[request.credentialConfigurationId]
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Unknown credentialConfigurationId: ${request.credentialConfigurationId}"))

        // Pool held credentials by issuer + OID4VCI configuration provenance, while type lookup stays
        // based on payload/metadata refs such as vct, mdoc doctype, or W3C VC type.
        val credentialFormat =
            CredentialFormat.fromValueLenient(credConfig.format)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported credential format '${credConfig.format}'"))
        val expectedTypeRefs = expectedCredentialTypeRefs(credentialFormat, credConfig)

        val issuerType = if (request.credentialIssuer.startsWith("did:")) IdentifierType.DID else IdentifierType("https")
        val rawIssuerRef = IdentifierRef(type = issuerType, value = request.credentialIssuer)
        val resolvedIssuerResult = identityResolver.resolve(rawIssuerRef, IdentityRole.ISSUER)
        if (resolvedIssuerResult.isErr) return Err(resolvedIssuerResult.error)
        val issuerRef = resolvedIssuerResult.value
        val authorizationServerUrl = request.authorizationServerUrl ?: resolveAuthorizationServerUrl(metadata)

        // Fetch a fresh c_nonce from the issuer's nonce endpoint when available.
        // The OID4VCI spec issues proof nonces via /nonce, not the token response.
        val nonceEndpoint = metadata.nonceEndpoint
        val cNonce: String? =
            if (nonceEndpoint != null) {
                val nonceResult = oid4vciHolder.requestNonce(nonceEndpoint)
                if (nonceResult.isErr) return Err(nonceResult.error)
                nonceResult.value.cNonce
            } else {
                request.cNonce
            }

        // Build the proof(s) for batch issuance.
        val proofResult =
            oid4vciHolder.createCredentialRequestProof(
                issuerUrl = request.credentialIssuer,
                cNonce = cNonce,
                signingKeyId = request.holderKeyAlias,
                signingAlgorithm = request.signingAlgorithm,
                count = request.count,
                keyInclusionMode = request.keyInclusionMode,
            )
        if (proofResult.isErr) return Err(proofResult.error)
        val proofs = proofResult.value.proofs

        val sessionNow = Clock.System.now()
        val issuanceSession =
            IssuanceSession(
                id = Uuid.v4String(),
                walletInstanceId = request.walletInstanceId,
                issuerRef = issuerRef,
                credentialIssuerUrl = request.credentialIssuer,
                authorizationServerUrl = authorizationServerUrl,
                credentialConfigurationId = request.credentialConfigurationId,
                credentialIdentifier = request.credentialIdentifier,
                expectedCredentialTypeRefs = expectedTypeRefs,
                holderKeyRef = KeyRef(alias = request.holderKeyAlias),
                status = IssuanceSessionStatus.TOKEN_GRANTED,
                createdAt = sessionNow,
                updatedAt = sessionNow,
            )
        val sessionResult = issuanceSessions.putSession(request.walletInstanceId, issuanceSession)
        if (sessionResult.isErr) return Err(sessionResult.error)

        // Request all credentials in a single call.
        val credentialResult =
            oid4vciHolder.requestCredential(
                credentialEndpoint = metadata.credentialEndpoint,
                accessToken = request.accessToken,
                credentialConfigurationId = request.credentialConfigurationId,
                credentialIdentifier = request.credentialIdentifier,
                proofs = proofs,
            )
        if (credentialResult.isErr) return Err(credentialResult.error)
        val credentialResponse = credentialResult.value

        if (credentialResponse.isDeferredResponse()) {
            val deferredResult =
                persistDeferredSession(
                    session = issuanceSession,
                    metadata = metadata,
                    credentialResponse = credentialResponse,
                    accessToken = request.accessToken,
                )
            if (deferredResult.isErr) return Err(deferredResult.error)
            return Ok(ObtainCredentialResult.Deferred(deferredResult.value))
        }

        val storedResult =
            storeIssuedCredentials(
                walletInstanceId = request.walletInstanceId,
                credentialIssuer = request.credentialIssuer,
                credentialConfigurationId = request.credentialConfigurationId,
                credentialIdentifier = request.credentialIdentifier,
                holderKeyAlias = request.holderKeyAlias,
                credentialFormat = credentialFormat,
                issuerRef = issuerRef,
                expectedTypeRefs = expectedTypeRefs,
                metadata = metadata,
                credConfig = credConfig,
                credentialResponse = credentialResponse,
                issuanceSession = issuanceSession,
            )
        if (storedResult.isErr) return Err(storedResult.error)

        val completedSession =
            issuanceSession.copy(
                status = IssuanceSessionStatus.COMPLETED,
                notification = credentialResponse.notificationId?.let { IssuanceNotificationState(notificationId = it) },
                updatedAt = storedResult.value.updatedAt,
            )
        val completeResult = issuanceSessions.putSession(request.walletInstanceId, completedSession)
        if (completeResult.isErr) return Err(completeResult.error)
        return Ok(ObtainCredentialResult.Stored(storedResult.value))
    }

    private fun holderKeyAlias(
        walletInstanceId: String,
        alias: String?,
    ): String {
        val localAlias = alias?.takeIf { it.isNotBlank() } ?: Uuid.v4String()
        return "wallet-${keyAliasSegment(walletInstanceId)}-holder-${keyAliasSegment(localAlias)}"
    }

    private fun keyAliasSegment(value: String): String =
        value
            .trim()
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char == '-' || char == '_' || char == '.' -> char
                    else -> '_'
                }
            }.joinToString("")
            .ifBlank { Uuid.v4String() }

    override suspend fun resumeDeferredIssuance(request: ResumeDeferredIssuanceRequest): IdkResult<ObtainCredentialResult, IdkError> {
        val sessionResult = issuanceSessions.getSession(request.walletInstanceId, request.issuanceSessionId)
        if (sessionResult.isErr) return Err(sessionResult.error)
        val session =
            sessionResult.value
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Issuance session '${request.issuanceSessionId}' was not found"))
        val deferred =
            session.deferred
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Issuance session '${session.id}' is not deferred"))
        val maxAttempts = deferred.retryPolicy.maxAttempts
        if (maxAttempts != null && deferred.attempts >= maxAttempts) {
            val failedSession =
                session.withDeferredFailure(
                    deferred = deferred,
                    code = "DEFERRED_ISSUANCE_MAX_ATTEMPTS_EXCEEDED",
                    message = "Deferred issuance session '${session.id}' exceeded $maxAttempts polling attempts",
                    incrementAttempt = false,
                )
            val putResult = issuanceSessions.putSession(request.walletInstanceId, failedSession)
            if (putResult.isErr) return Err(putResult.error)
            return Err(deferredMaxAttemptsError(session.id, maxAttempts))
        }
        val accessTokenResult = issuanceSessions.getDeferredAccessToken(request.walletInstanceId, session.id)
        if (accessTokenResult.isErr) return Err(accessTokenResult.error)
        val accessToken =
            accessTokenResult.value
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Deferred access token for issuance session '${session.id}' was not found"))

        val credentialResult =
            oid4vciHolder.requestDeferredCredential(
                deferredCredentialEndpoint = deferred.deferredCredentialEndpoint,
                accessToken = accessToken,
                transactionId = deferred.transactionId,
            )
        if (credentialResult.isErr) {
            val failedSession = session.withDeferredFailure(deferred, credentialResult.error.code, credentialResult.error.message.defaultMessage)
            val putResult = issuanceSessions.putSession(request.walletInstanceId, failedSession)
            if (putResult.isErr) return Err(putResult.error)
            return Err(credentialResult.error)
        }
        val credentialResponse = credentialResult.value
        if (!credentialResponse.hasIssuedCredentials()) {
            val updatedTransactionId = credentialResponse.transactionId
            if (updatedTransactionId == null) {
                return Err(IdkError.UNKNOWN_ERROR(message = "Deferred credential response contained neither credentials nor transaction_id"))
            }
            val now = Clock.System.now()
            val attempts = deferred.attempts + 1
            val maxReached = deferred.retryPolicy.maxAttempts?.let { attempts >= it } ?: false
            val updatedDeferred =
                deferred.copy(
                    transactionId = updatedTransactionId,
                    attempts = attempts,
                    nextPollAt =
                        credentialResponse.interval
                            ?.let { now + it.seconds }
                            ?: (now + deferred.retryPolicy.nextDelaySeconds(attempts).seconds),
                    lastError =
                        if (maxReached) {
                            DeferredIssuanceError(
                                code = "DEFERRED_ISSUANCE_MAX_ATTEMPTS_EXCEEDED",
                                message = "Deferred issuance session '${session.id}' exceeded ${deferred.retryPolicy.maxAttempts} polling attempts",
                                observedAt = now,
                            )
                        } else {
                            deferred.lastError
                        },
                )
            val updatedSession =
                session.copy(
                    status = if (maxReached) IssuanceSessionStatus.FAILED else IssuanceSessionStatus.DEFERRED,
                    deferred = updatedDeferred,
                    updatedAt = now,
                )
            val putResult = issuanceSessions.putSession(request.walletInstanceId, updatedSession)
            if (putResult.isErr) return Err(putResult.error)
            if (maxReached) return Err(deferredMaxAttemptsError(session.id, deferred.retryPolicy.maxAttempts ?: attempts))
            return Ok(ObtainCredentialResult.Deferred(updatedSession))
        }

        val metadataResult = oid4vciHolder.resolveIssuerMetadata(session.credentialIssuerUrl)
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value
        val credConfig =
            metadata.credentialConfigurationsSupported[session.credentialConfigurationId]
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Unknown credentialConfigurationId: ${session.credentialConfigurationId}"))
        val credentialFormat =
            CredentialFormat.fromValueLenient(credConfig.format)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported credential format '${credConfig.format}'"))

        val storedResult =
            storeIssuedCredentials(
                walletInstanceId = session.walletInstanceId,
                credentialIssuer = session.credentialIssuerUrl,
                credentialConfigurationId = session.credentialConfigurationId,
                credentialIdentifier = session.credentialIdentifier,
                holderKeyAlias =
                    session.holderKeyRef?.alias
                        ?: return Err(IdkError.UNKNOWN_ERROR(message = "Deferred issuance session '${session.id}' has no holder key reference")),
                credentialFormat = credentialFormat,
                issuerRef = session.issuerRef,
                expectedTypeRefs = session.expectedCredentialTypeRefs,
                metadata = metadata,
                credConfig = credConfig,
                credentialResponse = credentialResponse,
                issuanceSession = session,
            )
        if (storedResult.isErr) return Err(storedResult.error)

        val completedSession =
            session.copy(
                status = IssuanceSessionStatus.COMPLETED,
                notification = credentialResponse.notificationId?.let { IssuanceNotificationState(notificationId = it) },
                updatedAt = storedResult.value.updatedAt,
            )
        val completeResult = issuanceSessions.putSession(request.walletInstanceId, completedSession)
        if (completeResult.isErr) return Err(completeResult.error)
        return Ok(ObtainCredentialResult.Stored(storedResult.value))
    }

    private suspend fun persistDeferredSession(
        session: IssuanceSession,
        metadata: CredentialIssuerMetadata,
        credentialResponse: CredentialResponse,
        accessToken: String,
    ): IdkResult<IssuanceSession, IdkError> {
        val transactionId =
            credentialResponse.transactionId
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential response contained neither credentials nor transaction_id"))
        val deferredEndpoint =
            metadata.deferredCredentialEndpoint
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Issuer metadata has no deferred_credential_endpoint"))
        val secretRefResult = issuanceSessions.storeDeferredAccessToken(session.walletInstanceId, session.id, accessToken)
        if (secretRefResult.isErr) return Err(secretRefResult.error)

        val now = Clock.System.now()
        val deferredSession =
            session.copy(
                status = IssuanceSessionStatus.DEFERRED,
                deferred =
                    DeferredIssuanceState(
                        transactionId = transactionId,
                        deferredCredentialEndpoint = deferredEndpoint,
                        accessTokenRef = secretRefResult.value,
                        nextPollAt = credentialResponse.interval?.let { now + it.seconds },
                    ),
                notification = credentialResponse.notificationId?.let { IssuanceNotificationState(notificationId = it) },
                updatedAt = now,
            )
        val putResult = issuanceSessions.putSession(session.walletInstanceId, deferredSession)
        return if (putResult.isOk) Ok(deferredSession) else Err(putResult.error)
    }

    private fun CredentialResponse.hasIssuedCredentials(): Boolean = !credentials.isNullOrEmpty()

    private fun CredentialResponse.isDeferredResponse(): Boolean = !hasIssuedCredentials() && transactionId != null

    private fun IssuanceSession.withDeferredFailure(
        deferred: DeferredIssuanceState,
        code: String,
        message: String,
        incrementAttempt: Boolean = true,
    ): IssuanceSession {
        val now = Clock.System.now()
        return copy(
            status = IssuanceSessionStatus.FAILED,
            deferred =
                deferred.copy(
                    attempts = if (incrementAttempt) deferred.attempts + 1 else deferred.attempts,
                    lastError =
                        DeferredIssuanceError(
                            code = code,
                            message = message,
                            observedAt = now,
                        ),
                ),
            updatedAt = now,
        )
    }

    private fun RetryPolicy.nextDelaySeconds(attempts: Int): Long {
        var delay = initialDelaySeconds
        repeat((attempts - 1).coerceAtLeast(0)) {
            delay = (delay * 2).coerceAtMost(maxDelaySeconds)
        }
        return delay.coerceAtMost(maxDelaySeconds)
    }

    private fun deferredMaxAttemptsError(
        issuanceSessionId: String,
        maxAttempts: Int,
    ): IdkError =
        IdkError.fromString(
            code = "DEFERRED_ISSUANCE_MAX_ATTEMPTS_EXCEEDED",
            message = "Deferred issuance session '$issuanceSessionId' exceeded $maxAttempts polling attempts",
            category = ErrorCategory.UNAVAILABLE,
        )

    private suspend fun storeIssuedCredentials(
        walletInstanceId: String,
        credentialIssuer: String,
        credentialConfigurationId: String,
        credentialIdentifier: String?,
        holderKeyAlias: String,
        credentialFormat: CredentialFormat,
        issuerRef: IdentifierRef,
        expectedTypeRefs: Set<CredentialTypeRef>,
        metadata: CredentialIssuerMetadata,
        credConfig: CredentialConfigurationSupported,
        credentialResponse: CredentialResponse,
        issuanceSession: IssuanceSession,
    ): IdkResult<CredentialRecord, IdkError> {
        val responseItems =
            credentialResponse.credentials
                ?.takeIf { it.isNotEmpty() }
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential response contained no credentials"))

        val existingMetaResult =
            credentials.listMetadata(
                walletInstanceId = walletInstanceId,
                filter =
                    CredentialMetadataFilter(
                        issuerRef = issuerRef,
                        credentialConfigurationId = credentialConfigurationId,
                    ),
            )
        if (existingMetaResult.isErr) return Err(existingMetaResult.error)
        val matchingMeta = existingMetaResult.value.firstOrNull()
        val existingRecord: CredentialRecord? =
            if (matchingMeta != null) {
                val getResult = credentials.getCredential(walletInstanceId, matchingMeta.credentialRecordId)
                if (getResult.isErr) return Err(getResult.error)
                getResult.value
            } else {
                null
            }

        val now = Clock.System.now()
        val credentialRecordId = existingRecord?.id ?: Uuid.v4String()
        val newInstances =
            responseItems.map { item ->
                val raw =
                    (item.credential as? JsonPrimitive)?.content
                        ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential item is not a primitive string"))
                val credentialInstanceId = Uuid.v4String()
                CredentialInstance(
                    id = credentialInstanceId,
                    walletInstanceId = walletInstanceId,
                    credentialRecordId = credentialRecordId,
                    format = credentialFormat,
                    raw = raw,
                    bodyStorageRef =
                        BodyStorageRef(
                            kind = BodyStorageKind.WALLET_STORE,
                            path = credentialBodyPath(walletInstanceId, credentialRecordId, credentialInstanceId),
                        ),
                    holderKeyRef = KeyRef(alias = holderKeyAlias),
                    lifecycleState = CredentialLifecycleState.ACTIVE,
                    validity = CredentialValidityWindow(),
                    issuedAt = now,
                    storedAt = now,
                    updatedAt = now,
                )
            }

        val verificationResult = verifyIssuedCredentialInstances(credentialConfigurationId, credentialFormat, newInstances)
        if (verificationResult.isErr) return Err(verificationResult.error)

        val actualTypeRefsResult = actualCredentialTypeRefs(credentialFormat, newInstances, credentialConfigurationId, "issued")
        if (actualTypeRefsResult.isErr) return Err(actualTypeRefsResult.error)
        val actualTypeRefs = actualTypeRefsResult.value
        val diagnostics = issuanceDiagnostics(expectedTypeRefs, actualTypeRefs, now)

        val updatedRecord =
            if (existingRecord != null) {
                val existingProvenance = existingRecord.issuanceProvenance
                val recordWithRefs =
                    existingRecord.copy(
                        credentialTypeRefs = existingRecord.credentialTypeRefs + actualTypeRefs,
                        issuanceProvenance =
                            existingProvenance?.copy(
                                diagnostics = existingProvenance.diagnostics + diagnostics,
                            ),
                    )
                newInstances.fold(recordWithRefs) { record, instance ->
                    record.withAddedInstance(instance)
                }
            } else {
                val firstInstance = newInstances.first()
                val rawSubjectRefs = subjectExtractor.extractSubjects(credentialFormat, firstInstance.requireRaw())
                val resolvedSubjects = mutableListOf<IdentifierRef>()
                for (subjectRef in rawSubjectRefs) {
                    val resolvedResult = identityResolver.resolve(subjectRef, IdentityRole.HOLDER)
                    if (resolvedResult.isErr) return Err(resolvedResult.error)
                    resolvedSubjects += resolvedResult.value
                }

                CredentialRecord(
                    id = credentialRecordId,
                    walletInstanceId = walletInstanceId,
                    issuerRef = issuerRef,
                    subjectRefs = resolvedSubjects,
                    format = credentialFormat,
                    credentialTypeRefs = actualTypeRefs,
                    display =
                        CredentialDisplayMetadata(
                            issuerDisplay = metadata.display.toWalletDisplayProperties(),
                            credentialDisplay = credConfig.display.toWalletDisplayProperties(),
                            claims = credConfig.claims.toWalletClaimMetadata(),
                        ),
                    instances = newInstances,
                    issuanceProvenance =
                        IssuanceProvenance(
                            issuanceSessionId = issuanceSession.id,
                            credentialIssuerUrl = credentialIssuer,
                            authorizationServerUrl = issuanceSession.authorizationServerUrl,
                            credentialConfigurationId = credentialConfigurationId,
                            credentialIdentifier = credentialIdentifier,
                            expectedCredentialTypeRefs = expectedTypeRefs,
                            diagnostics = diagnostics,
                            issuedAt = now,
                            notificationId = credentialResponse.notificationId,
                        ),
                    createdAt = now,
                    updatedAt = now,
                )
            }

        val upsertResult = credentials.putCredential(walletInstanceId, updatedRecord)
        if (upsertResult.isErr) return Err(upsertResult.error)
        return Ok(updatedRecord)
    }

    private suspend fun storeRefreshedCredentials(
        record: CredentialRecord,
        credentialConfigurationId: String,
        holderKeyAlias: String,
        credentialFormat: CredentialFormat,
        expectedTypeRefs: Set<CredentialTypeRef>,
        credentialResponse: CredentialResponse,
    ): IdkResult<RefreshCredentialResult, IdkError> {
        val responseItems =
            credentialResponse.credentials
                ?.takeIf { it.isNotEmpty() }
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential refresh response contained no credentials"))

        val now = Clock.System.now()
        val replacesInstanceId =
            record.presentableInstance(now)?.id
                ?: record.instances.lastOrNull { it.lifecycleState == CredentialLifecycleState.ACTIVE }?.id
                ?: record.instances.lastOrNull()?.id
        val newInstances =
            responseItems.map { item ->
                val raw =
                    (item.credential as? JsonPrimitive)?.content
                        ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential item is not a primitive string"))
                val credentialInstanceId = Uuid.v4String()
                CredentialInstance(
                    id = credentialInstanceId,
                    walletInstanceId = record.walletInstanceId,
                    credentialRecordId = record.id,
                    format = credentialFormat,
                    raw = raw,
                    bodyStorageRef =
                        BodyStorageRef(
                            kind = BodyStorageKind.WALLET_STORE,
                            path = credentialBodyPath(record.walletInstanceId, record.id, credentialInstanceId),
                        ),
                    holderKeyRef = KeyRef(alias = holderKeyAlias),
                    lifecycleState = CredentialLifecycleState.ACTIVE,
                    validity = CredentialValidityWindow(),
                    replacesInstanceId = replacesInstanceId,
                    issuedAt = now,
                    storedAt = now,
                    updatedAt = now,
                )
            }

        val verificationResult = verifyIssuedCredentialInstances(credentialConfigurationId, credentialFormat, newInstances)
        if (verificationResult.isErr) return Err(verificationResult.error)

        val actualTypeRefsResult = actualCredentialTypeRefs(credentialFormat, newInstances, credentialConfigurationId, "refreshed")
        if (actualTypeRefsResult.isErr) return Err(actualTypeRefsResult.error)
        val actualTypeRefs = actualTypeRefsResult.value

        val refreshedRecord =
            newInstances.fold(record) { current, instance ->
                current.withRefreshedInstance(instance, actualTypeRefs)
            }
        val upsertResult = credentials.putCredential(record.walletInstanceId, refreshedRecord)
        if (upsertResult.isErr) return Err(upsertResult.error)
        return Ok(
            RefreshCredentialResult(
                record = upsertResult.value,
                refreshedInstanceIds = newInstances.map { it.id },
            ),
        )
    }

    private suspend fun verifyIssuedCredentialInstances(
        credentialConfigurationId: String,
        credentialFormat: CredentialFormat,
        instances: List<CredentialInstance>,
    ): IdkResult<Unit, IdkError> {
        if (!credentialFormat.isSdJwt) return Ok(Unit)
        for (instance in instances) {
            val verifyResult =
                verifySdJwtVcCommand.execute(
                    VerifySdJwtVcArgs(
                        sdJwt = instance.requireRaw(),
                        opts =
                            SdJwtVcVerificationOpts(
                                validateTypeMetadata = false,
                                validateStatus = false,
                            ),
                    ),
                )
            if (verifyResult.isErr) {
                return Err(
                    IdkError.fromString(
                        code = "ISSUED_CREDENTIAL_VERIFICATION_FAILED",
                        message =
                            "Refusing to store issued credential '$credentialConfigurationId': " +
                                "issuer-signature verification failed: ${verifyResult.error.message.defaultMessage}",
                    ),
                )
            }
        }
        return Ok(Unit)
    }

    private suspend fun resolveAuthorizationServerUrl(metadata: CredentialIssuerMetadata): String? {
        val result = oid4vciHolder.selectAuthorizationServer(metadata)
        return if (result.isOk) result.value.authorizationServerUrl else null
    }

    override suspend fun refreshCredential(request: RefreshCredentialRequest): IdkResult<RefreshCredentialResult, IdkError> {
        val recordResult = credentials.getCredential(request.walletInstanceId, request.credentialRecordId)
        if (recordResult.isErr) return Err(recordResult.error)
        val record =
            recordResult.value
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential record '${request.credentialRecordId}' was not found"))
        val refreshState =
            record.refreshState
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential record '${record.id}' has no refresh state"))
        if (refreshState.refreshMethod != CredentialRefreshMethod.OID4VCI_REISSUANCE) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported refresh method '${refreshState.refreshMethod}' for wallet OID4VCI reissuance",
                ),
            )
        }

        val credentialIssuer =
            request.credentialIssuer
                ?: record.issuanceProvenance?.credentialIssuerUrl
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "credentialIssuer is required when the record has no issuance provenance"))
        val credentialConfigurationId =
            request.credentialConfigurationId
                ?: record.issuanceProvenance?.credentialConfigurationId
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "credentialConfigurationId is required when the record has no issuance provenance"))
        val holderKeyAlias =
            request.holderKeyAlias
                ?: record.instances
                    .lastOrNull { it.lifecycleState == CredentialLifecycleState.ACTIVE }
                    ?.holderKeyRef
                    ?.alias
                ?: record.instances
                    .lastOrNull()
                    ?.holderKeyRef
                    ?.alias
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "holderKeyAlias is required when the record has no holder key reference"))

        val metadataResult = oid4vciHolder.resolveIssuerMetadata(credentialIssuer)
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value
        val credConfig =
            metadata.credentialConfigurationsSupported[credentialConfigurationId]
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Unknown credentialConfigurationId: $credentialConfigurationId"))
        val credentialFormat =
            CredentialFormat.fromValueLenient(credConfig.format)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported credential format '${credConfig.format}'"))
        val expectedTypeRefs = expectedCredentialTypeRefs(credentialFormat, credConfig)

        val nonceEndpoint = metadata.nonceEndpoint
        val cNonce: String? =
            if (nonceEndpoint != null) {
                val nonceResult = oid4vciHolder.requestNonce(nonceEndpoint)
                if (nonceResult.isErr) return Err(nonceResult.error)
                nonceResult.value.cNonce
            } else {
                request.cNonce
            }

        val proofResult =
            oid4vciHolder.createCredentialRequestProof(
                issuerUrl = credentialIssuer,
                cNonce = cNonce,
                signingKeyId = holderKeyAlias,
                signingAlgorithm = request.signingAlgorithm,
                count = 1,
                keyInclusionMode = request.keyInclusionMode,
            )
        if (proofResult.isErr) return Err(proofResult.error)

        val credentialResult =
            oid4vciHolder.requestCredential(
                credentialEndpoint = refreshState.refreshEndpoint ?: metadata.credentialEndpoint,
                accessToken = request.accessToken,
                credentialConfigurationId = credentialConfigurationId,
                credentialIdentifier = request.credentialIdentifier ?: record.issuanceProvenance?.credentialIdentifier,
                proofs = proofResult.value.proofs,
            )
        if (credentialResult.isErr) return Err(credentialResult.error)
        val credentialResponse = credentialResult.value
        if (credentialResponse.isDeferredResponse()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Deferred refresh/reissuance is not supported by refreshCredential"))
        }

        return storeRefreshedCredentials(
            record = record,
            credentialConfigurationId = credentialConfigurationId,
            holderKeyAlias = holderKeyAlias,
            credentialFormat = credentialFormat,
            expectedTypeRefs = expectedTypeRefs,
            credentialResponse = credentialResponse,
        )
    }

    override suspend fun present(
        requestUri: String,
        config: WalletConfig,
    ): IdkResult<PresentationResult, IdkError> {
        val oid4vpConfig = Oid4vpWalletConfig(audience = config.clientId)

        val parseResult = oid4vpHolder.parseAuthorizationRequest(requestUri, oid4vpConfig)
        if (parseResult.isErr) return Err(parseResult.error)

        val resolveResult = oid4vpHolder.resolveAuthorizationRequest(parseResult.value)
        if (resolveResult.isErr) return Err(resolveResult.error)
        val resolvedRequest = resolveResult.value

        val queries = resolvedRequest.dcqlQuery?.credentials.orEmpty()
        val selectedCredentials = mutableListOf<SelectedCredential>()
        val presentationBindingUpdates = linkedMapOf<String, CredentialRecord>()

        // Resolve the verifier identity once for all queries in this presentation.
        val verifierClientId = resolvedRequest.verifierInfo.clientId
        val verifierType = if (verifierClientId.startsWith("did:")) IdentifierType.DID else IdentifierType("https")
        val rawVerifierRef = IdentifierRef(type = verifierType, value = verifierClientId)
        val resolvedVerifierResult = identityResolver.resolve(rawVerifierRef, IdentityRole.VERIFIER)
        if (resolvedVerifierResult.isErr) return Err(resolvedVerifierResult.error)
        val verifierRef = resolvedVerifierResult.value

        for (query in queries) {
            val requestedTypeRefs = credentialTypeRefsForQuery(query)
            // A credential type can be held by multiple credential records. Match on metadata
            // first, then open records until a presentable instance is found.
            // (Richer per-RP / per-presentation instance selection is deferred to later config.)
            val candidateMetadata =
                if (requestedTypeRefs.isNotEmpty()) {
                    val collected = linkedMapOf<String, CredentialMetadata>()
                    for (ref in requestedTypeRefs) {
                        val metaResult = credentials.findByCredentialTypeRef(config.walletInstanceId, ref)
                        if (metaResult.isErr) return Err(metaResult.error)
                        for (metadata in metaResult.value) {
                            collected[metadata.credentialRecordId] = metadata
                        }
                    }
                    collected.values.filter { it.isPresentationCandidate() }
                } else {
                    val format = query.format?.let { CredentialFormat.fromValueLenient(it) }
                    val metaResult =
                        credentials.listMetadata(
                            walletInstanceId = config.walletInstanceId,
                            filter =
                                CredentialMetadataFilter(
                                    formats = format?.let { setOf(it) } ?: emptySet(),
                                    lifecycleStates = setOf(CredentialLifecycleState.ACTIVE),
                                ),
                        )
                    if (metaResult.isErr) return Err(metaResult.error)
                    metaResult.value.filter { it.isPresentationCandidate() }
                }
            var selected: Pair<CredentialRecord, CredentialInstance>? = null
            val selectionTime = Clock.System.now()
            for (candidate in candidateMetadata) {
                val recordResult = credentials.getCredential(config.walletInstanceId, candidate.credentialRecordId)
                if (recordResult.isErr) return Err(recordResult.error)
                val candidateRecord = recordResult.value ?: continue
                val candidateInstance = candidateRecord.presentableInstance(selectionTime) ?: continue
                selected = candidateRecord to candidateInstance
                break
            }
            val (record, instance) =
                selected
                    ?: return Err(IdkError.NOT_FOUND_ERROR(message = "no held credential matches query '${query.id}'"))

            // Prepare the binding update, but persist it only after response creation and
            // submission succeed so failed presentations do not mutate wallet history.
            val boundAt = Clock.System.now()
            val recordForUpdate = presentationBindingUpdates[record.id] ?: record
            val updatedRecord =
                recordForUpdate.copy(
                    instances =
                        recordForUpdate.instances.map {
                            if (it.id == instance.id) {
                                it.copy(
                                    bindingRefs =
                                        it.bindingRefs +
                                            CredentialBindingRef(
                                                verifierRef = verifierRef,
                                                boundAt = boundAt,
                                                presentationId = query.id,
                                            ),
                                    updatedAt = boundAt,
                                )
                            } else {
                                it
                            }
                        },
                    updatedAt = boundAt,
                    syncState = recordForUpdate.syncState.copy(localRevision = recordForUpdate.syncState.localRevision + 1),
                )
            presentationBindingUpdates[record.id] = updatedRecord

            selectedCredentials +=
                SelectedCredential(
                    credentialQueryId = query.id,
                    credentialId = instance.id,
                    presentation = instance.requireRaw(),
                    format = instance.format.value,
                    // Carry the holder key so the OID4VP holder can produce a Key Binding JWT for
                    // SD-JWT presentations (binds to verifier client_id + request nonce).
                    holderKeyAlias =
                        instance.holderKeyRef?.alias
                            ?: return Err(IdkError.UNKNOWN_ERROR(message = "Selected credential has no holder key reference")),
                )
        }

        if (selectedCredentials.isEmpty()) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "no held credential matches the request"))
        }

        val responseResult = oid4vpHolder.createAuthorizationResponse(resolvedRequest, selectedCredentials)
        if (responseResult.isErr) return Err(responseResult.error)

        val submitResult = oid4vpHolder.submitAuthorizationResponse(resolvedRequest, responseResult.value)
        if (submitResult.isErr) return Err(submitResult.error)

        val redirectUri =
            when (val submission = submitResult.value) {
                is SubmissionResult.Success -> submission.redirectUri
                is SubmissionResult.Redirect -> submission.redirectUri
                is SubmissionResult.Error -> return Err(IdkError.UNKNOWN_ERROR(message = "Presentation submission failed: ${submission.error}"))
            }

        for (updatedRecord in presentationBindingUpdates.values) {
            val upsertResult = credentials.putCredential(config.walletInstanceId, updatedRecord)
            if (upsertResult.isErr) return Err(upsertResult.error)
        }

        return Ok(PresentationResult(submitted = true, redirectUri = redirectUri))
    }

    private fun expectedCredentialTypeRefs(
        format: CredentialFormat,
        configuration: CredentialConfigurationSupported,
    ): Set<CredentialTypeRef> =
        buildSet {
            when {
                format.isSdJwt -> {
                    configuration.vct?.takeIf { it.isNotBlank() }?.let {
                        add(
                            CredentialTypeRef(
                                format = format,
                                kind = CredentialTypeRefKind.SD_JWT_VCT,
                                value = it,
                                source = CredentialTypeRefSource.ISSUER_METADATA,
                                primary = true,
                            ),
                        )
                    }
                }

                format.isMdoc -> {
                    configuration.doctype?.takeIf { it.isNotBlank() }?.let {
                        add(
                            CredentialTypeRef(
                                format = format,
                                kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                                value = it,
                                source = CredentialTypeRefSource.ISSUER_METADATA,
                                primary = true,
                            ),
                        )
                    }
                }

                else -> {
                    addAll(w3cTypeRefs(format, configuration.credentialDefinition?.type.orEmpty(), CredentialTypeRefSource.ISSUER_METADATA))
                }
            }
        }

    private fun credentialPayloadTypeRefs(
        format: CredentialFormat,
        raw: String,
    ): Set<CredentialTypeRef> =
        when {
            format.isSdJwt -> sdJwtVct(raw, format)
            format.isMdoc -> mdocDoctype(raw, format)
            format.isJwt || format == CredentialFormat.VC_LD_JSON_JWT -> w3cTypeRefs(format, w3cTypesFromJwt(raw), CredentialTypeRefSource.CREDENTIAL_PAYLOAD)
            else -> emptySet()
        }

    private fun actualCredentialTypeRefs(
        credentialFormat: CredentialFormat,
        instances: List<CredentialInstance>,
        credentialConfigurationId: String,
        operation: String,
    ): IdkResult<Set<CredentialTypeRef>, IdkError> {
        val actualTypeRefs = mutableSetOf<CredentialTypeRef>()
        for (instance in instances) {
            val instanceRefs = credentialPayloadTypeRefs(credentialFormat, instance.requireRaw())
            if (instanceRefs.isEmpty()) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "No credentialTypeRefs could be derived from $operation credential payload '${instance.id}' " +
                                "for '$credentialConfigurationId'",
                    ),
                )
            }
            actualTypeRefs += instanceRefs
        }
        return Ok(actualTypeRefs)
    }

    private fun issuanceDiagnostics(
        expectedTypeRefs: Set<CredentialTypeRef>,
        actualTypeRefs: Set<CredentialTypeRef>,
        observedAt: kotlin.time.Instant,
    ): List<IssuanceDiagnostic> {
        if (expectedTypeRefs.isEmpty()) return emptyList()
        if (expectedTypeRefs.referenceKeys() == actualTypeRefs.referenceKeys()) return emptyList()
        return listOf(
            IssuanceDiagnostic(
                code = IssuanceDiagnosticCode.CREDENTIAL_TYPE_REF_MISMATCH,
                message = "Issued credential type references differ from issuer metadata expectations",
                expectedCredentialTypeRefs = expectedTypeRefs,
                actualCredentialTypeRefs = actualTypeRefs,
                observedAt = observedAt,
            ),
        )
    }

    private fun Set<CredentialTypeRef>.referenceKeys(): Set<Triple<CredentialFormat, CredentialTypeRefKind, String>> = map { Triple(it.format, it.kind, it.value) }.toSet()

    private fun credentialTypeRefsForQuery(query: DcqlCredentialQuery): Set<CredentialTypeRef> {
        val meta = query.meta ?: return emptySet()
        val format = query.format?.let { CredentialFormat.fromValueLenient(it) }

        return buildSet {
            val sdJwtFormat = format?.takeIf { it.isSdJwt } ?: CredentialFormat.SD_JWT_DC
            val vctValues = meta["vct_values"]
            if (vctValues is JsonArray) {
                for (entry in vctValues) {
                    if (entry is JsonPrimitive && entry.isString) {
                        add(
                            CredentialTypeRef(
                                format = sdJwtFormat,
                                kind = CredentialTypeRefKind.SD_JWT_VCT,
                                value = entry.content,
                                source = CredentialTypeRefSource.IMPORT_METADATA,
                                primary = true,
                            ),
                        )
                    }
                }
            }

            val doctypeValue = meta["doctype_value"]
            if (doctypeValue is JsonPrimitive && doctypeValue.isString) {
                add(
                    CredentialTypeRef(
                        format = CredentialFormat.MSO_MDOC,
                        kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                        value = doctypeValue.content,
                        source = CredentialTypeRefSource.IMPORT_METADATA,
                        primary = true,
                    ),
                )
            }

            val w3cFormat = format?.takeUnless { it.isSdJwt || it.isMdoc } ?: CredentialFormat.JWT_VC_JSON
            addAll(w3cTypeRefs(w3cFormat, jsonStringList(meta["type_values"]) + jsonStringList(meta["types"]), CredentialTypeRefSource.IMPORT_METADATA))
        }
    }

    private fun CredentialMetadata.isPresentationCandidate(): Boolean =
        activeInstanceCount > 0 &&
            lifecycleSummary.lifecycleState == CredentialLifecycleState.ACTIVE &&
            lifecycleSummary.validityState != CredentialValidityState.NOT_YET_VALID &&
            lifecycleSummary.validityState != CredentialValidityState.EXPIRED

    private fun sdJwtVct(
        raw: String,
        format: CredentialFormat,
    ): Set<CredentialTypeRef> {
        val parseResult = SdJwtCodec.parse(raw)
        if (parseResult.isErr) return emptySet()
        val vct =
            parseResult.value.payload.fullPayload["vct"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: return emptySet()
        return setOf(
            CredentialTypeRef(
                format = format,
                kind = CredentialTypeRefKind.SD_JWT_VCT,
                value = vct,
                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                primary = true,
            ),
        )
    }

    private fun mdocDoctype(
        raw: String,
        format: CredentialFormat,
    ): Set<CredentialTypeRef> {
        val issuerSignedBytes =
            try {
                raw.decodeFromBase64Url()
            } catch (_: Exception) {
                return emptySet()
            }
        val issuerSignedResult = walletIssuerSignedCborCodec.decode(issuerSignedBytes)
        if (issuerSignedResult.isErr) return emptySet()

        val msoPayload =
            issuerSignedResult.value.value.issuerAuth.payload
                ?.value ?: return emptySet()
        val msoResult = walletMobileSecurityObjectCborCodec.decode(msoPayload)
        if (msoResult.isErr) return emptySet()

        val doctype =
            msoResult.value.value.docType
                .toString()
                .takeIf { it.isNotBlank() } ?: return emptySet()
        return setOf(
            CredentialTypeRef(
                format = format,
                kind = CredentialTypeRefKind.MDOC_DOCTYPE,
                value = doctype,
                source = CredentialTypeRefSource.CREDENTIAL_PAYLOAD,
                primary = true,
            ),
        )
    }

    private fun w3cTypesFromJwt(raw: String): List<String> {
        val parts = raw.split(".")
        if (parts.size < 2) return emptyList()
        return try {
            val payload = JwsUtils.decodeBase64UrlToJson(parts[1])
            val vcClaim = payload["vc"]
            if (vcClaim is JsonObject) {
                val vcTypes = jsonStringList(vcClaim["type"])
                if (vcTypes.isNotEmpty()) return vcTypes
            }
            jsonStringList(payload["type"])
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun w3cTypeRefs(
        format: CredentialFormat,
        types: List<String>,
        source: CredentialTypeRefSource,
    ): Set<CredentialTypeRef> {
        val cleanTypes = types.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val primaryType = cleanTypes.firstOrNull { it != "VerifiableCredential" } ?: cleanTypes.firstOrNull()
        return cleanTypes
            .map {
                CredentialTypeRef(
                    format = format,
                    kind = CredentialTypeRefKind.W3C_VC_TYPE,
                    value = it,
                    source = source,
                    primary = it == primaryType,
                )
            }.toSet()
    }

    private fun jsonStringList(element: JsonElement?): List<String> =
        when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> element.contentOrNull?.let { listOf(it) }.orEmpty()
            else -> emptyList()
        }

    private fun List<Oid4vcDisplayProperties>?.toWalletDisplayProperties(): List<CredentialDisplayProperties> =
        orEmpty().map {
            CredentialDisplayProperties(
                name = it.name,
                locale = it.locale,
                logo = it.logo.toWalletLogoProperties(),
                description = it.description,
                backgroundColor = it.backgroundColor,
                backgroundImage = it.backgroundImage.toWalletImageProperties(),
                textColor = it.textColor,
            )
        }

    private fun Oid4vcLogoProperties?.toWalletLogoProperties(): CredentialLogoProperties? =
        this?.let {
            CredentialLogoProperties(
                uri = it.uri,
                altText = it.altText,
            )
        }

    private fun Oid4vcImageProperties?.toWalletImageProperties(): CredentialImageProperties? =
        this?.let {
            CredentialImageProperties(uri = it.uri)
        }

    private fun List<Oid4vciCredentialClaim>?.toWalletClaimMetadata(): List<CredentialClaimMetadata> =
        orEmpty().map {
            CredentialClaimMetadata(
                path = it.path,
                mandatory = it.mandatory,
                valueType = it.valueType,
                display = it.display.toWalletClaimDisplay(),
            )
        }

    private fun List<Oid4vciClaimDisplay>?.toWalletClaimDisplay(): List<CredentialClaimDisplay> =
        orEmpty().map {
            CredentialClaimDisplay(
                name = it.name,
                locale = it.locale,
            )
        }

    private fun credentialBodyPath(
        walletInstanceId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
    ): String = "wallet-instances/$walletInstanceId/credentials/$credentialRecordId/instances/$credentialInstanceId/body"
}
