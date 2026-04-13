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

package com.sphereon.openid.oid4vp.auth.impl.provider

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.http.HttpJson
import com.sphereon.openid.oid4vp.auth.claims.WalletAttributeProjector
import com.sphereon.openid.oid4vp.auth.error.Oid4vpAuthErrors
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwtHeader
import com.sphereon.crypto.core.jose.JwtPayload
import com.sphereon.crypto.core.jose.tryGenerateJwkThumbprint
import com.sphereon.crypto.core.x509.certificateFromBase64Der
import com.sphereon.did.methods.key.MultibaseCodec
import com.sphereon.did.methods.key.MulticodecPrefix
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.crypto.ReconciliationCryptoService
import com.sphereon.identity.matching.store.IdentityMatchStore
import com.sphereon.identity.reconciliation.model.KnownHolderState
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionArgs
import com.sphereon.openid.oid4vp.auth.bridge.CreateSessionResult
import com.sphereon.openid.oid4vp.auth.bridge.Oid4vpAuthBridge
import com.sphereon.openid.oid4vp.auth.service.UniversalOid4vpService
import com.sphereon.openid.oid4vp.auth.http.model.Oid4vpAuthStatusResponse
import com.sphereon.openid.oid4vp.auth.config.Oid4vpAuthBridgeConfigProvider
import com.sphereon.openid.oid4vp.auth.input.CreateUserInput
import com.sphereon.openid.oid4vp.auth.model.ClaimSource
import com.sphereon.openid.oid4vp.auth.model.IdvRequirementReason
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthResult
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSession
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthSessionStatus
import com.sphereon.openid.oid4vp.auth.model.ResolvedUser
import com.sphereon.openid.oid4vp.auth.model.UserType
import com.sphereon.openid.oid4vp.auth.orchestration.ResolvedKnownHolder
import com.sphereon.openid.oid4vp.auth.orchestration.ReconciliationOrchestratorApi
import com.sphereon.openid.oid4vp.auth.service.UserService
import com.sphereon.openid.oid4vp.auth.store.Oid4vpAuthSessionStore
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.QrCodeOptions
import com.sphereon.openid.oid4vp.universal.VerifiedCredential
import com.sphereon.openid.oid4vp.universal.VerifiedData
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * OID4VP implementation that provides both [UserAuthenticationProvider] for OAuth2 integration
 * and [Oid4vpAuthBridge] for session creation and management.
 *
 * This is the main entry point for OID4VP authentication in the system. It:
 * - Uses [UniversalOid4vpService] for spec-compliant OID4VP operations
 * - Handles user identity resolution via [UserService]
 * - Projects wallet credential attributes to canonical output via [WalletAttributeProjector]
 * - Manages internal session state via [Oid4vpAuthSessionStore]
 *
 * ## Thread Safety
 *
 * This implementation is thread-safe, using mutexes for internal caches.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpAuthBridge>())
class Oid4vpUserAuthenticationProviderImpl(
    private val universalOid4vpClient: UniversalOid4vpService,
    private val sessionStore: Oid4vpAuthSessionStore,
    private val userService: UserService,
    private val walletAttributeProjector: WalletAttributeProjector,
    private val configProvider: Oid4vpAuthBridgeConfigProvider,
    private val execution: SessionExecution,
    private val reconciliationCryptoService: ReconciliationCryptoService,
    private val reconciliationOrchestrator: ReconciliationOrchestratorApi,
    private val identityMatchStore: IdentityMatchStore
) : UserAuthenticationProvider, Oid4vpAuthBridge {

    private val log = execution.log
    private val config by lazy { configProvider.getConfig() }

    // Thread-safe access to session and cache maps
    private val sessionMutex = Mutex()
    private val cacheMutex = Mutex()

    // Map OAuth session ID to OID4VP session ID (guarded by sessionMutex)
    private val oauthToOid4vpSession = mutableMapOf<String, String>()

    // Map user ID to cached user info (guarded by cacheMutex)
    private val userInfoCache = mutableMapOf<String, UserInfo>()

    // ========================================================================
    // UniversalOid4vpApi implementation
    // ========================================================================

    override suspend fun createSession(args: CreateSessionArgs): IdkResult<CreateSessionResult, IdkError> {
        // 1. Resolve query - prefer queryId, fall back to inline DCQL query from config
        val queryId = args.queryId ?: config.defaultQueryId
        val inlineDcqlQuery = if (queryId == null) {
            config.defaultDcqlQuery?.let { dcqlJson ->
                try {
                    com.sphereon.core.api.http.HttpJson.restApi.decodeFromString(DcqlQuery.serializer(), dcqlJson)
                } catch (e: Exception) {
                    log.error("Failed to parse default DCQL query from config: ${e.message}", e)
                    return Err(Oid4vpAuthErrors.queryIdRequired())
                }
            } ?: return Err(Oid4vpAuthErrors.queryIdRequired())
        } else null
        log.debug("Creating OID4VP auth session with queryId=$queryId, inlineDcql=${inlineDcqlQuery != null}")

        // 2. Generate internal session ID
        val sessionId = generateSessionId()
        val ttlSeconds = args.ttlSeconds ?: config.sessionTtlSeconds

        // 3. Create authorization request via Universal OID4VP client
        // For redirect_uri scheme, client_id IS the response URI (per OID4VP 1.0 spec)
        val resolvedClientId = config.clientId ?: "portal-auth-bridge"
        log.debug("Using clientId=$resolvedClientId (config=${config.clientId})")

        // Detect DID-based client_id and set scheme accordingly
        val resolvedScheme = if (resolvedClientId.startsWith("did:")) {
            ClientIdScheme.DECENTRALIZED_IDENTIFIER
        } else null

        val input = CreateAuthorizationRequestInput(
            queryId = queryId,
            dcqlQuery = inlineDcqlQuery,
            clientId = resolvedClientId,
            clientIdScheme = resolvedScheme,
            ttlSeconds = ttlSeconds,
            qrCodeOptions = QrCodeOptions()
        )

        val output = universalOid4vpClient.createAuthorizationRequest(input).getOrElse { error ->
            return Err(error)
        }

        val correlationId = output.correlationId

        // 4. Create our auth session
        val now = Clock.System.now()
        val expiresAt = now + ttlSeconds.seconds

        val session = Oid4vpAuthSession(
            sessionId = sessionId,
            correlationId = correlationId,
            oauthSessionId = args.oauthSessionId,
            queryId = queryId ?: "inline-dcql",
            status = Oid4vpAuthSessionStatus.PENDING,
            verifiedData = null,
            resolvedUserId = null,
            errorMessage = null,
            requestedProjection = args.requestedProjection,
            forceReconciliation = args.forceReconciliation,
            createdAt = now,
            updatedAt = now,
            expiresAt = expiresAt
        )

        // 5. Store the session
        sessionStore.put(sessionId, session, ttlSeconds.seconds).getOrElse { error ->
            return Err(error)
        }

        log.info("Created OID4VP auth session: $sessionId -> $correlationId")

        return Ok(CreateSessionResult(
            session = session,
            qrCodeDataUri = output.qrUri ?: "",
            requestUri = output.requestUri ?: "",
            statusUri = "/auth/oid4vp/sessions/$sessionId/status",
            qrPageUri = "/auth/oid4vp/sessions/$sessionId/qr"
        ))
    }

    // ========================================================================
    // UserAuthenticationProvider implementation (for OAuth2)
    // ========================================================================

    override suspend fun getAuthenticatedUser(
        sessionId: String
    ): IdkResult<AuthenticatedUser?, AuthenticationError> {
        log.debug("Checking authenticated user for OAuth session: $sessionId")

        // 1. Find corresponding OID4VP session
        val oid4vpSessionId = sessionMutex.withLock { oauthToOid4vpSession[sessionId] }
            ?: return Ok(null) // No OID4VP session started yet

        // 2. Get session status
        val session = pollAndUpdateSession(oid4vpSessionId).getOrElse { error ->
            log.warn("Failed to get OID4VP session status: ${error.message.defaultMessage}")
            return Ok(null)
        }

        // 3. Check if verified
        if (session.status != Oid4vpAuthSessionStatus.VERIFIED &&
            session.status != Oid4vpAuthSessionStatus.COMPLETED) {
            return Ok(null) // Not yet verified
        }

        // 4. Complete authentication (idempotent — works for both VERIFIED and COMPLETED sessions)
        val authResult = completeAuthentication(oid4vpSessionId).getOrElse { error ->
            log.error("Failed to complete authentication: ${error.message.defaultMessage}")
            return Err(AuthenticationError.Generic(message = error.message.defaultMessage))
        }

        // 5. Cache user info
        cacheUserInfoFromClaims(authResult.userId, authResult.claims)

        log.info("User authenticated via OID4VP: ${authResult.userId}")

        return Ok(AuthenticatedUser(
            userId = authResult.userId,
            authenticatedAt = authResult.authenticatedAt,
            authenticationMethod = AuthenticationMethod.CUSTOM,
            acr = authResult.acr,
            amr = authResult.amr
        ))
    }

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?
    ): IdkResult<String, AuthenticationError> {
        log.debug("Initiating OID4VP authentication for OAuth session: $sessionId")

        // 1. Create OID4VP session
        val createResult = createSession(CreateSessionArgs(
            oauthSessionId = sessionId,
            returnUrl = returnUrl,
            requestedProjection = "sts"
        )).getOrElse { error ->
            log.error("Failed to create OID4VP session: ${error.message.defaultMessage}")
            return Err(AuthenticationError.Generic(message = error.message.defaultMessage))
        }

        // 2. Map OAuth session to OID4VP session
        sessionMutex.withLock { oauthToOid4vpSession[sessionId] = createResult.session.sessionId }

        log.info("Created OID4VP session ${createResult.session.sessionId} for OAuth session $sessionId")

        // 3. Return QR page URL with return URL parameter
        val qrPageUrl = buildQrPageUrl(createResult.qrPageUri, returnUrl)
        return Ok(qrPageUrl)
    }

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials
    ): IdkResult<String?, AuthenticationError> {
        // OID4VP doesn't support direct credential authentication
        return Err(AuthenticationError.MethodUnavailable(
            method = AuthenticationMethod.CUSTOM,
            message = "OID4VP authentication requires wallet presentation, direct credentials not supported"
        ))
    }

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> {
        log.debug("Logging out user: $userId")

        // Clean up any cached user info
        cacheMutex.withLock { userInfoCache.remove(userId) }

        // Find and remove any sessions for this user
        sessionMutex.withLock {
            val sessionsToRemove = oauthToOid4vpSession.entries
                .filter { (_, oid4vpSessionId) ->
                    getSession(oid4vpSessionId).getOrNull()?.resolvedUserId == userId
                }
                .map { it.key }

            sessionsToRemove.forEach { oauthToOid4vpSession.remove(it) }
        }

        return Ok(Unit)
    }

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        // Check cache first
        val cached = cacheMutex.withLock { userInfoCache[userId] }
        if (cached != null) return Ok(cached)

        // If not in cache, return basic info
        return Ok(UserInfo(
            userId = userId,
            displayName = "OID4VP User"
        ))
    }

    override suspend fun isAuthenticationMethodAvailable(
        method: AuthenticationMethod
    ): IdkResult<Boolean, AuthenticationError> {
        // OID4VP is always available (as a CUSTOM method)
        return Ok(method == AuthenticationMethod.CUSTOM)
    }

    // ========================================================================
    // Oid4vpAuthBridge implementation (continued) - Status & Complete
    // ========================================================================

    override suspend fun getSessionStatus(sessionId: String): IdkResult<Oid4vpAuthStatusResponse, IdkError> {
        val session = pollAndUpdateSession(sessionId).getOrElse { return Err(it) }
        // Apply claims mapping when verified so consumers (e.g. Keycloak) receive OIDC-standard claims
        val mappedClaims = session.verifiedData
            ?.takeIf { session.status == Oid4vpAuthSessionStatus.VERIFIED }
            ?.let { mapCredentialClaims(it.credentials) }

        return Ok(Oid4vpAuthStatusResponse.from(session, mappedClaims))
    }

    override suspend fun completeAuthentication(sessionId: String): IdkResult<Oid4vpAuthResult, IdkError> {
        log.debug("Completing authentication for session: $sessionId")

        // 1. Get session and validate status
        val session = pollAndUpdateSession(sessionId).getOrElse { return Err(it) }

        if (session.status == Oid4vpAuthSessionStatus.COMPLETED) {
            // Idempotent: return cached result for already-completed sessions.
            // This is needed because both the frontend BFF and the STS call /complete.
            val userId = session.resolvedUserId
            if (userId != null) {
                log.info("Session $sessionId already completed, returning cached result for user $userId")
                val now = Clock.System.now()

                // If the session was reconciled (has a holder hash), retrieve canonical attributes
                // from the binding so repeated /complete calls return the same canonical attributes.
                val knownHolder = session.holderIdentifierHash?.let { hash ->
                    reconciliationOrchestrator.resolveKnownHolder(hash, "default").getOrNull()
                }

                val (claims, acr, amr, claimSource) = if (knownHolder != null) {
                    log.debug("Idempotent /complete: using canonical attributes from binding ${knownHolder.bindingId}")
                    ResolvedClaimsBag(
                        claims = knownHolder.canonicalAttributes,
                        acr = knownHolder.assurance?.oidcAcr ?: Oid4vpAuthResult.RECONCILED_ACR,
                        amr = knownHolder.assurance?.oidcAmr ?: Oid4vpAuthResult.RECONCILED_AMR,
                        claimSource = ClaimSource.CANONICAL_BINDING
                    )
                } else {
                    // No binding — fall back to wallet canonical projection
                    val verifiedData = session.verifiedData
                    val walletAttributes = if (verifiedData != null) {
                        walletAttributeProjector.projectWalletAttributes(verifiedData.credentials)
                    } else emptyMap()
                    ResolvedClaimsBag(
                        claims = walletAttributes,
                        acr = Oid4vpAuthResult.DEFAULT_ACR,
                        amr = Oid4vpAuthResult.DEFAULT_AMR,
                        claimSource = ClaimSource.WALLET_ONLY
                    )
                }

                return Ok(Oid4vpAuthResult(
                    userId = userId,
                    claims = claims,
                    jwtClaims = buildJwtClaims(userId, claims, session.updatedAt ?: now, sessionId, acr = acr, amr = amr),
                    isNewUser = false,
                    authenticatedAt = session.updatedAt ?: now,
                    acr = acr,
                    amr = amr,
                    claimSource = claimSource
                ))
            }
            return Err(Oid4vpAuthErrors.sessionAlreadyCompleted(sessionId))
        }

        if (session.status != Oid4vpAuthSessionStatus.VERIFIED) {
            return Err(Oid4vpAuthErrors.sessionNotVerified(sessionId))
        }

        val verifiedData = session.verifiedData
            ?: return Err(Oid4vpAuthErrors.verifiedDataMissing(sessionId))

        // 2. Extract holder key fingerprint from VP token JWT header.
        // The fingerprint is the JWK thumbprint (RFC 7638) of the holder's public key,
        // providing a stable, cryptographic identifier that doesn't depend on mutable profile claims.
        val tenantId = "default"
        val holderFingerprintResult = extractHolderKeyFingerprint(verifiedData)
        val holderFingerprint = holderFingerprintResult.getOrElse { error ->
            // Extraction was attempted (key material found) but failed
            log.error("Holder key extraction failed: ${error.message.defaultMessage}")
            return Err(error)
        }

        val rawHolderKey: String
        val holderKeyHash = if (holderFingerprint != null) {
            // Cryptographic holder binding found — use it
            rawHolderKey = holderFingerprint
            reconciliationCryptoService.hashHolderKey(holderFingerprint)
        } else if (config.requireReconciliation) {
            // Fail closed: reconciliation requires a cryptographic holder binding.
            // A mutable claim (e.g. email) is NOT acceptable as a holder identifier when
            // reconciliation is enabled, because it would allow holder-binding spoofing.
            log.error("No cryptographic holder key found in VP token and require-reconciliation=true. " +
                "The VP must contain a holder binding via embedded jwk, did:jwk, did:key, or x5c header.")
            return Err(Oid4vpAuthErrors.holderKeyExtractionFailed(
                "No cryptographic holder key found in VP token. " +
                "Reconciliation requires a holder binding via jwk, did, or x5c."
            ))
        } else {
            // Fallback allowed: reconciliation not required, use the configured claim path (e.g., emailAddress)
            log.warn("No cryptographic holder key in VP token; falling back to claim-based identifier " +
                "(path=${config.userIdentifierClaimPath}). This is acceptable because require-reconciliation=false.")
            val userIdentifier = extractUserIdentifier(verifiedData, config.userIdentifierClaimPath)
                ?: return Err(Oid4vpAuthErrors.userIdentifierNotFound(config.userIdentifierClaimPath))
            rawHolderKey = userIdentifier
            reconciliationCryptoService.hashHolderKey(userIdentifier)
        }

        // 3. Fast path: delegate to orchestrator to check for existing identity link binding
        //    Skip if force_reconciliation is set on the session (forces fresh OIDC reconciliation)
        log.info("Fast path check: forceReconciliation=${session.forceReconciliation}, holderKeyHash=${holderKeyHash.hash.take(16)}..., tenantId=$tenantId")
        val knownHolder = if (session.forceReconciliation) {
            log.info("Skipping fast path: force_reconciliation=true for session $sessionId")
            null
        } else {
            val extractedWalletAttributes = verifiedData.credentials
                .flatMap { it.claims.entries }
                .associate { it.key to it.value }
                .ifEmpty { null }
            val holder = reconciliationOrchestrator.resolveKnownHolder(holderKeyHash.hash, tenantId, rawHolderKey, extractedWalletAttributes)
                .getOrNull()
            if (holder == null) {
                log.info("Fast path: no existing binding found for holder hash ${holderKeyHash.hash.take(16)}...")
            }
            holder
        }

        if (knownHolder != null) {
            if (knownHolder.state != KnownHolderState.MATCHED_HOLDER_KEY) {
                val now = Clock.System.now()
                val idvRequirementReason = idvRequirementReasonForKnownHolderState(knownHolder.state)
                val idvSession = session.copy(
                    status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                    holderIdentifierHash = holderKeyHash.hash,
                    holderHashKeyVersion = holderKeyHash.keyVersion,
                    knownHolderState = knownHolder.state,
                    idvRequirementReason = idvRequirementReason,
                    rawHolderKeyFingerprint = rawHolderKey,
                    idvMessage = idvMessageFor(idvRequirementReason),
                    updatedAt = now
                )
                sessionStore.put(sessionId, idvSession, (session.expiresAt - now).coerceAtLeast(60.seconds))
                log.info(
                    "Session $sessionId transitioned to IDV_REQUIRED from fast path " +
                        "(knownHolderState=${knownHolder.state})"
                )
                return Err(Oid4vpAuthErrors.idvRequired(sessionId))
            }

            log.info("Fast path: found existing identity link binding for holder in session $sessionId")
            val now = Clock.System.now()

            val knownAcr = knownHolder.assurance?.oidcAcr ?: Oid4vpAuthResult.RECONCILED_ACR
            val knownAmr = knownHolder.assurance?.oidcAmr ?: Oid4vpAuthResult.RECONCILED_AMR
            val jwtClaims = buildJwtClaims(
                userId = knownHolder.userId,
                mappedClaims = knownHolder.canonicalAttributes,
                authenticatedAt = now,
                sessionId = sessionId,
                acr = knownAcr,
                amr = knownAmr
            )

            val completedSession = session.copy(
                status = Oid4vpAuthSessionStatus.COMPLETED,
                resolvedUserId = knownHolder.userId,
                holderIdentifierHash = holderKeyHash.hash,
                holderHashKeyVersion = holderKeyHash.keyVersion,
                knownHolderState = knownHolder.state,
                idvRequirementReason = null,
                rawHolderKeyFingerprint = rawHolderKey,
                idvMessage = null,
                updatedAt = now
            )
            sessionStore.put(sessionId, completedSession, 300.seconds)
            cacheUserInfoFromClaims(knownHolder.userId, knownHolder.canonicalAttributes)

            log.info("Fast path authentication completed for session $sessionId, user ${knownHolder.userId}")
            return Ok(buildResultFromKnownHolder(knownHolder, now, sessionId, jwtClaims))
        }

        // 4. Extract user identifier from claims for user resolution
        val userIdentifier = extractUserIdentifier(verifiedData, config.userIdentifierClaimPath)

        // 5. Resolve user — check reconciliation result first, then identity matching
        val isReconciled = session.resolvedUserId != null && session.reconciliationSessionId != null
        val (user, isNewUser) = if (isReconciled) {
            log.info("Using reconciled identity for session $sessionId: ${session.resolvedUserId}")
            ResolvedUser(
                partyId = session.resolvedUserId!!,
                username = userIdentifier ?: session.resolvedUserId!!,
                userType = UserType.EXTERNAL
            ) to false
        } else if (config.requireReconciliation) {
            // When reconciliation is required, finding a user by wallet attribute (e.g., email)
            // is NOT sufficient — the holder must be linked via a cryptographic binding.
            // The fast path above already checked for an existing binding and didn't find one,
            // so we must require IDV to establish the link.
            log.info("Reconciliation required but no binding found for holder. Transitioning to IDV_REQUIRED.")
            val now = Clock.System.now()
            val idvRequirementReason = if (session.forceReconciliation) {
                IdvRequirementReason.FORCED_RECONCILIATION
            } else {
                IdvRequirementReason.FIRST_TIME_LINK
            }
            val idvSession = session.copy(
                status = Oid4vpAuthSessionStatus.IDV_REQUIRED,
                holderIdentifierHash = holderKeyHash.hash,
                holderHashKeyVersion = holderKeyHash.keyVersion,
                knownHolderState = KnownHolderState.NOT_FOUND,
                idvRequirementReason = idvRequirementReason,
                rawHolderKeyFingerprint = rawHolderKey,
                idvMessage = idvMessageFor(idvRequirementReason),
                updatedAt = now
            )
            sessionStore.put(sessionId, idvSession, (session.expiresAt - now).coerceAtLeast(60.seconds))
            log.info("Session $sessionId transitioned to IDV_REQUIRED (reconciliation required, no binding)")
            return Err(Oid4vpAuthErrors.idvRequired(sessionId))
        } else {
            // Normal flow (no reconciliation required): lookup user via identity resolution pipeline
            val existingUser = if (userIdentifier != null) {
                userService.lookupUser(userIdentifier).getOrElse { error ->
                    return Err(error)
                }
            } else null

            if (existingUser != null) {
                existingUser to false
            } else if (config.autoCreateUser && userIdentifier != null) {
                val newUser = userService.createUser(
                    CreateUserInput(
                        username = userIdentifier,
                        userType = UserType.EXTERNAL,
                        displayName = extractDisplayName(verifiedData),
                        email = extractEmail(verifiedData)
                    )
                ).getOrElse { error ->
                    return Err(error)
                }
                newUser to true
            } else {
                return Err(Oid4vpAuthErrors.userNotFound("Auto-creation is disabled"))
            }
        }

        log.info("Resolved user: ${user.partyId} (new=$isNewUser)")

        val now = Clock.System.now()

        // 6. Resolve output claims: canonical from binding (reconciled) or wallet-only (non-reconciled)
        val (outputClaims, acr, amr, claimSource) = if (isReconciled && session.holderIdentifierHash != null) {
            // Reconciled session: retrieve canonical attributes from the identity link binding.
            // These were persisted during handleCallback and represent the merged wallet + OIDC canonical attributes.
            val bindingHolder = reconciliationOrchestrator.resolveKnownHolder(
                session.holderIdentifierHash!!, tenantId
            ).getOrNull()

            if (bindingHolder != null) {
                log.info("Using canonical attributes from binding ${bindingHolder.bindingId} for reconciled session $sessionId")
                ResolvedClaimsBag(
                    claims = bindingHolder.canonicalAttributes,
                    acr = bindingHolder.assurance?.oidcAcr ?: Oid4vpAuthResult.RECONCILED_ACR,
                    amr = bindingHolder.assurance?.oidcAmr ?: Oid4vpAuthResult.RECONCILED_AMR,
                    claimSource = ClaimSource.CANONICAL_BINDING
                )
            } else {
                log.error("Reconciled session $sessionId has no binding — cannot complete without canonical attributes")
                return Err(Oid4vpAuthErrors.reconciliationFailed(
                    "Identity binding not found after reconciliation. Please retry identity verification."
                ))
            }
        } else {
            // Non-reconciled flow: map wallet attributes directly
            walletAttributesBag(verifiedData).getOrElse { return Err(it) }
        }

        // 7. Build JWT claims with resolved assurance values
        val jwtClaims = buildJwtClaims(
            userId = user.partyId,
            mappedClaims = outputClaims,
            authenticatedAt = now,
            sessionId = sessionId,
            acr = acr,
            amr = amr
        )

        // 8. Update session to COMPLETED
        val completedSession = session.copy(
            status = Oid4vpAuthSessionStatus.COMPLETED,
            resolvedUserId = user.partyId,
            idvRequirementReason = null,
            idvMessage = null,
            updatedAt = now
        )
        sessionStore.put(sessionId, completedSession, 300.seconds)

        // 9. Build result
        val result = Oid4vpAuthResult(
            userId = user.partyId,
            claims = outputClaims,
            jwtClaims = jwtClaims,
            isNewUser = isNewUser,
            authenticatedAt = now,
            acr = acr,
            amr = amr,
            claimSource = claimSource
        )

        log.info("Authentication completed for session $sessionId, user ${user.partyId} (claimSource=$claimSource)")

        return Ok(result)
    }

    // ========================================================================
    // Private helpers (internal session management)
    // ========================================================================

    /**
     * Internal bag for resolved claims with their associated ACR, AMR, and source.
     * Used by destructuring declarations in completeAuthentication.
     */
    private data class ResolvedClaimsBag(
        val claims: Map<String, JsonElement>,
        val acr: String,
        val amr: List<String>,
        val claimSource: ClaimSource
    )

    /**
     * Build a wallet-only [ResolvedClaimsBag] by projecting credential claims through
     * the canonical attribute pipeline.
     */
    private suspend fun walletAttributesBag(
        verifiedData: VerifiedData,
    ): IdkResult<ResolvedClaimsBag, IdkError> {
        val projectedClaims = walletAttributeProjector.projectWalletAttributes(verifiedData.credentials)
        log.debug("Projected ${projectedClaims.size} canonical wallet attributes")
        return Ok(ResolvedClaimsBag(
            claims = projectedClaims,
            acr = Oid4vpAuthResult.DEFAULT_ACR,
            amr = Oid4vpAuthResult.DEFAULT_AMR,
            claimSource = ClaimSource.WALLET_ONLY
        ))
    }

    /**
     * Build an [Oid4vpAuthResult] from a [ResolvedKnownHolder] (fast-path or reconciled binding).
     * Uses assurance metadata from the binding when available for ACR/AMR.
     */
    private fun buildResultFromKnownHolder(
        knownHolder: ResolvedKnownHolder,
        authenticatedAt: Instant,
        sessionId: String,
        jwtClaims: String
    ): Oid4vpAuthResult {
        val acr = knownHolder.assurance?.oidcAcr ?: Oid4vpAuthResult.RECONCILED_ACR
        val amr = knownHolder.assurance?.oidcAmr ?: Oid4vpAuthResult.RECONCILED_AMR
        return Oid4vpAuthResult(
            userId = knownHolder.userId,
            claims = knownHolder.canonicalAttributes,
            jwtClaims = jwtClaims,
            isNewUser = false,
            authenticatedAt = authenticatedAt,
            acr = acr,
            amr = amr,
            claimSource = ClaimSource.CANONICAL_BINDING
        )
    }

    /**
     * Poll and update session status - polls Universal OID4VP for current state.
     * Used internally by both interface methods and UserAuthenticationProvider methods.
     */
    private suspend fun pollAndUpdateSession(sessionId: String): IdkResult<Oid4vpAuthSession, IdkError> {
        // 1. Get our session
        val session = sessionStore.get(sessionId).getOrElse { return Err(it) }
            ?: return Err(Oid4vpAuthErrors.sessionNotFound(sessionId))

        // 2. Check if already completed, errored, or waiting for IDV
        if (session.status == Oid4vpAuthSessionStatus.COMPLETED ||
            session.status == Oid4vpAuthSessionStatus.ERROR ||
            session.status == Oid4vpAuthSessionStatus.IDV_REQUIRED) {
            return Ok(session)
        }

        // 3. Check expiration
        val now = Clock.System.now()
        if (now >= session.expiresAt) {
            val expiredSession = session.copy(
                status = Oid4vpAuthSessionStatus.EXPIRED,
                idvRequirementReason = null,
                idvMessage = null,
                updatedAt = now
            )
            sessionStore.put(sessionId, expiredSession, 60.seconds)
            return Ok(expiredSession)
        }

        // 4. Poll Universal OID4VP client for status
        val statusOutput = universalOid4vpClient.getAuthorizationRequestStatus(session.correlationId)
            .getOrElse { error ->
                log.error("Failed to get status from Universal OID4VP for session $sessionId (correlationId=${session.correlationId}): ${error.message.defaultMessage}")
                val errorSession = session.copy(
                    status = Oid4vpAuthSessionStatus.ERROR,
                    errorMessage = "Status poll failed: ${error.message.defaultMessage}",
                    idvRequirementReason = null,
                    idvMessage = null,
                    updatedAt = now
                )
                sessionStore.put(sessionId, errorSession, (session.expiresAt - now).coerceAtLeast(60.seconds))
                return Ok(errorSession)
            }

        // 5. Map Universal OID4VP status to our status
        val newStatus = mapAuthorizationSessionStatus(statusOutput.status)

        // 6. Build verified data if status is VERIFIED
        val verifiedData = if (newStatus == Oid4vpAuthSessionStatus.VERIFIED) {
            statusOutput.verifiedData
        } else {
            null
        }

        // 7. Update session if status changed
        if (newStatus != session.status || verifiedData != null) {
            val updatedSession = session.copy(
                status = newStatus,
                verifiedData = verifiedData,
                errorMessage = statusOutput.error?.message,
                idvRequirementReason = if (newStatus == Oid4vpAuthSessionStatus.IDV_REQUIRED) session.idvRequirementReason else null,
                idvMessage = if (newStatus == Oid4vpAuthSessionStatus.IDV_REQUIRED) session.idvMessage else null,
                updatedAt = now
            )
            sessionStore.put(sessionId, updatedSession, (session.expiresAt - now).coerceAtLeast(60.seconds))
            return Ok(updatedSession)
        }

        return Ok(session)
    }

    /**
     * Get a session by ID.
     */
    private suspend fun getSession(sessionId: String): IdkResult<Oid4vpAuthSession, IdkError> {
        return sessionStore.get(sessionId).getOrElse { return Err(it) }?.let { Ok(it) }
            ?: Err(Oid4vpAuthErrors.sessionNotFound(sessionId))
    }

    /**
     * Extract the holder's JWK thumbprint (RFC 7638) from the VP token JWT header.
     *
     * Inspects the first credential's `presentation` field (the raw JWT string):
     * 1. If the header contains a `jwk` field — uses it directly
     * 2. If the header `kid` is a `did:jwk:` DID — decodes the base64url JWK
     * 3. If the header `kid` is a `did:key:` DID — decodes the multibase-encoded public key
     * 4. If the header contains an `x5c` chain — extracts the leaf certificate's public key
     *
     * Returns Ok(thumbprint) on success, Ok(null) if no holder key material is present
     * in the VP token, or Err if key material was found but extraction failed.
     */
    private fun extractHolderKeyFingerprint(verifiedData: VerifiedData): IdkResult<String?, IdkError> {
        val presentation = verifiedData.credentialClaims
            ?.firstNotNullOfOrNull { it.presentation }
        if (presentation == null) {
            log.debug("No presentation JWT found in verified data; no holder key to extract")
            return Ok(null)
        }

        try {
            // Split JWT and decode header
            val parts = presentation.split(".")
            if (parts.size < 2) {
                log.debug("Presentation is not a valid JWT (fewer than 2 parts); no holder key to extract")
                return Ok(null)
            }

            val headerBytes = parts[0].decodeFromBase64Url()
            val headerJson = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(headerBytes.decodeToString()).jsonObject
            val header = JwtHeader(headerJson)

            // 1. Embedded JWK in header
            header.jwk?.let { jwk ->
                val thumbprint = tryGenerateJwkThumbprint(jwk).getOrElse { error ->
                    log.warn("Embedded JWK found in VP header but thumbprint generation failed: ${error.message.defaultMessage}")
                    return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("JWK thumbprint generation failed: ${error.message.defaultMessage}"))
                }
                log.info("Holder key fingerprint extracted via embedded JWK header")
                return Ok(thumbprint)
            }

            // 2. did:jwk kid — the method-specific ID is the base64url-encoded JWK
            header.kid?.let { kid ->
                if (kid.startsWith("did:jwk:")) {
                    val base64UrlJwk = kid.removePrefix("did:jwk:")
                        .substringBefore("#") // strip fragment if present
                    try {
                        val jwkJson = base64UrlJwk.decodeFromBase64Url().decodeToString()
                        val jwk = Jwk.fromJsonObject(
                            Json { ignoreUnknownKeys = true }
                                .parseToJsonElement(jwkJson).jsonObject
                        )
                        val thumbprint = tryGenerateJwkThumbprint(jwk).getOrElse { error ->
                            log.warn("did:jwk kid decoded but thumbprint generation failed: ${error.message.defaultMessage}")
                            return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("did:jwk thumbprint failed: ${error.message.defaultMessage}"))
                        }
                        log.info("Holder key fingerprint extracted via did:jwk kid")
                        return Ok(thumbprint)
                    } catch (e: Exception) {
                        log.warn("Failed to decode did:jwk kid '$kid': ${e.message}")
                        return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("did:jwk decode failed for kid '$kid': ${e.message}"))
                    }
                }

                // 3. did:key kid — decode the multibase-encoded multicodec public key
                if (kid.startsWith("did:key:")) {
                    try {
                        val methodSpecificId = kid.removePrefix("did:key:")
                            .substringBefore("#") // strip fragment if present
                        val keyBytes = MultibaseCodec.decode(methodSpecificId)
                        val (codecCode, bytesRead) = MulticodecPrefix.decodeVarint(keyBytes)
                        val codecPrefix = MulticodecPrefix.fromCode(codecCode)
                            ?: return Err(Oid4vpAuthErrors.holderKeyExtractionFailed(
                                "did:key uses unsupported multicodec prefix: 0x${codecCode.toString(16)}"
                            ))
                        val rawKeyBytes = keyBytes.sliceArray(bytesRead until keyBytes.size)
                        if (rawKeyBytes.size != codecPrefix.keyLength) {
                            return Err(Oid4vpAuthErrors.holderKeyExtractionFailed(
                                "did:key raw key length mismatch: expected ${codecPrefix.keyLength}, got ${rawKeyBytes.size}"
                            ))
                        }
                        val jwk = buildJwkFromDidKey(codecPrefix, rawKeyBytes)
                        val thumbprint = tryGenerateJwkThumbprint(jwk).getOrElse { error ->
                            log.warn("did:key decoded but thumbprint generation failed: ${error.message.defaultMessage}")
                            return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("did:key thumbprint failed: ${error.message.defaultMessage}"))
                        }
                        log.info("Holder key fingerprint extracted via did:key kid")
                        return Ok(thumbprint)
                    } catch (e: Exception) {
                        log.warn("Failed to decode did:key kid '$kid': ${e.message}")
                        return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("did:key decode failed for kid '$kid': ${e.message}"))
                    }
                }
                // Other DID methods not yet supported — fall through to x5c check
            }

            // 4. x5c certificate chain — extract public key from leaf certificate
            header.x5c?.let { x5cChain ->
                if (x5cChain.isNotEmpty()) {
                    try {
                        val leafCertBase64 = x5cChain.first()
                        val leafCert = certificateFromBase64Der(leafCertBase64)
                        val publicKeyJwk = leafCert.getPublicKeyJwk()
                        val thumbprint = tryGenerateJwkThumbprint(publicKeyJwk).getOrElse { error ->
                            log.warn("x5c leaf certificate public key extracted but thumbprint generation failed: ${error.message.defaultMessage}")
                            return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("x5c thumbprint failed: ${error.message.defaultMessage}"))
                        }
                        log.info("Holder key fingerprint extracted via x5c certificate chain (leaf certificate)")
                        return Ok(thumbprint)
                    } catch (e: Exception) {
                        log.warn("Failed to extract public key from x5c leaf certificate: ${e.message}")
                        return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("x5c public key extraction failed: ${e.message}"))
                    }
                }
            }

            // No holder key material found in the VP token header
            log.debug("VP token header contains no holder key material (no jwk, no did:jwk/did:key kid, no x5c)")
            return Ok(null)
        } catch (e: Exception) {
            log.warn("Failed to extract holder key fingerprint from VP token: ${e.message}")
            return Err(Oid4vpAuthErrors.holderKeyExtractionFailed("VP token header parsing failed: ${e.message}"))
        }
    }

    /**
     * Build a JWK from raw key bytes decoded from a did:key DID.
     *
     * This replicates the key construction logic from [com.sphereon.did.methods.key.KeyDidResolverImpl]
     * but operates synchronously without requiring DID resolution infrastructure.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun buildJwkFromDidKey(codecPrefix: MulticodecPrefix, rawKeyBytes: ByteArray): Jwk {
        return when (codecPrefix.keyType) {
            KeyTypeMapping.OKP -> {
                val crv = when (codecPrefix.curve) {
                    Curve.Ed25519 -> JwaCurve.Ed25519
                    Curve.X25519 -> JwaCurve.X25519
                    else -> throw IllegalArgumentException("Unsupported OKP curve: ${codecPrefix.curve}")
                }
                Jwk(
                    kty = JwaKeyType.OKP,
                    crv = crv,
                    x = kotlin.io.encoding.Base64.UrlSafe.encode(rawKeyBytes).trimEnd('=')
                )
            }
            KeyTypeMapping.EC -> {
                // For EC keys, the raw bytes are in compressed point format (02/03 prefix + x coordinate)
                val prefix = rawKeyBytes[0].toInt() and 0xFF
                require(prefix == 0x02 || prefix == 0x03) {
                    "Invalid compressed EC point prefix: $prefix"
                }
                val x = rawKeyBytes.sliceArray(1 until rawKeyBytes.size)
                val crv = when (codecPrefix.curve) {
                    Curve.Secp256k1 -> JwaCurve.Secp256k1
                    Curve.P_256 -> JwaCurve.P_256
                    Curve.P_384 -> JwaCurve.P_384
                    else -> throw IllegalArgumentException("Unsupported EC curve: ${codecPrefix.curve}")
                }
                // Note: y is derived from compressed form using the same placeholder approach
                // as KeyDidResolverImpl. The thumbprint will be consistent for the same did:key,
                // even though proper EC point decompression is not yet implemented.
                Jwk(
                    kty = JwaKeyType.EC,
                    crv = crv,
                    x = kotlin.io.encoding.Base64.UrlSafe.encode(x).trimEnd('='),
                    y = kotlin.io.encoding.Base64.UrlSafe.encode(x).trimEnd('=')
                )
            }
            else -> throw IllegalArgumentException("Unsupported key type: ${codecPrefix.keyType}")
        }
    }

    /**
     * Map Universal OID4VP status to our internal status.
     */
    private fun mapAuthorizationSessionStatus(status: AuthorizationSessionStatus): Oid4vpAuthSessionStatus {
        return when (status) {
            AuthorizationSessionStatus.AUTHORIZATION_REQUEST_CREATED ->
                Oid4vpAuthSessionStatus.PENDING
            AuthorizationSessionStatus.AUTHORIZATION_REQUEST_RETRIEVED ->
                Oid4vpAuthSessionStatus.INTERACTION_STARTED
            AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_RECEIVED ->
                Oid4vpAuthSessionStatus.INTERACTION_STARTED
            AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED ->
                Oid4vpAuthSessionStatus.VERIFIED
            AuthorizationSessionStatus.ERROR ->
                Oid4vpAuthSessionStatus.ERROR
        }
    }

    /**
     * Build JWT claims using JwtPayload.
     */
    private fun buildJwtClaims(
        userId: String,
        mappedClaims: Map<String, JsonElement>,
        authenticatedAt: Instant,
        sessionId: String,
        acr: String = Oid4vpAuthResult.DEFAULT_ACR,
        amr: List<String> = Oid4vpAuthResult.DEFAULT_AMR
    ): String {
        val payload = JwtPayload()

        // Standard JWT/OIDC claims
        payload.sub = userId
        payload.iat = authenticatedAt.epochSeconds.toInt()

        // OIDC-specific claims — use the passed-in assurance values
        payload.put("auth_time", JsonPrimitive(authenticatedAt.epochSeconds))
        payload.put("acr", JsonPrimitive(acr))
        payload.put("amr", buildJsonArray {
            amr.forEach { add(JsonPrimitive(it)) }
        })

        // OID4VP-specific claim for session correlation
        payload.put("oid4vp_session_id", JsonPrimitive(sessionId))

        // Include all mapped claims from credential presentation
        mappedClaims.forEach { (key, value) ->
            if (key !in setOf("sub", "iat", "auth_time", "acr", "amr")) {
                payload.put(key, value)
            }
        }

        return payload.toJsonString()
    }

    /**
     * Build the QR page URL with return URL parameter.
     */
    private fun buildQrPageUrl(qrPageUri: String, returnUrl: String): String {
        val encodedReturnUrl = returnUrl.encodeURLQueryComponent()
        return if (qrPageUri.contains("?")) {
            "$qrPageUri&return_url=$encodedReturnUrl"
        } else {
            "$qrPageUri?return_url=$encodedReturnUrl"
        }
    }

    /**
     * Cache user info from authentication result claims.
     */
    private suspend fun cacheUserInfoFromClaims(userId: String, claims: Map<String, JsonElement>) {
        val displayName = (claims["name"] as? JsonPrimitive)?.contentOrNull
        val email = (claims["email"] as? JsonPrimitive)?.contentOrNull
        val givenName = (claims["given_name"] as? JsonPrimitive)?.contentOrNull
        val familyName = (claims["family_name"] as? JsonPrimitive)?.contentOrNull

        val computedDisplayName = displayName
            ?: listOfNotNull(givenName, familyName).takeIf { it.isNotEmpty() }?.joinToString(" ")
            ?: "OID4VP User"

        val userInfo = UserInfo(
            userId = userId,
            displayName = computedDisplayName,
            email = email
        )
        cacheMutex.withLock { userInfoCache[userId] = userInfo }
    }

    private fun generateSessionId(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        return bytes.joinToString("") { byte ->
            val hex = (byte.toInt() and 0xFF).toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }

    // ========================================================================
    // Credential claim extraction helpers
    // ========================================================================

    /**
     * Extract user identifier from verified credentials.
     *
     * Looks for the specified claim path in all credentials and returns
     * the first non-null value found.
     */
    private fun extractUserIdentifier(
        verifiedData: VerifiedData,
        claimPath: String
    ): String? {
        for (credential in verifiedData.credentials) {
            val identifier = extractClaimValue(credential, claimPath)
            if (identifier != null) {
                return identifier
            }
        }
        return null
    }

    /**
     * Extract display name from verified credentials.
     *
     * Tries to find "name", "given_name", or "family_name" claims.
     */
    private fun extractDisplayName(verifiedData: VerifiedData): String? {
        // Try "name" first
        for (credential in verifiedData.credentials) {
            val name = extractClaimValue(credential, "name")
            if (name != null) return name
        }

        // Try to build from given_name and family_name
        var givenName: String? = null
        var familyName: String? = null

        for (credential in verifiedData.credentials) {
            if (givenName == null) {
                givenName = extractClaimValue(credential, "given_name")
            }
            if (familyName == null) {
                familyName = extractClaimValue(credential, "family_name")
            }
        }

        return listOfNotNull(givenName, familyName).takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /**
     * Extract email from verified credentials.
     */
    private fun extractEmail(verifiedData: VerifiedData): String? {
        for (credential in verifiedData.credentials) {
            val email = extractClaimValue(credential, "email")
            if (email != null) return email
        }
        return null
    }

    /**
     * Extract a claim value from a credential.
     *
     * Supports simple paths like "sub" and nested paths like "address.country".
     */
    private fun extractClaimValue(credential: VerifiedCredential, path: String): String? {
        val parts = path.split(".")
        var current: JsonElement? = credential.claims[parts.first()]

        for (i in 1 until parts.size) {
            if (current == null) return null
            val obj = current as? JsonObject ?: return null
            current = obj[parts[i]]
        }

        return (current as? JsonPrimitive)?.contentOrNull
    }

    private suspend fun mapCredentialClaims(credentials: List<VerifiedCredential>): Map<String, JsonElement>? {
        return walletAttributeProjector.projectWalletAttributes(credentials).takeIf { it.isNotEmpty() }
    }

    private fun idvRequirementReasonForKnownHolderState(state: KnownHolderState): IdvRequirementReason = when (state) {
        KnownHolderState.MATCHED_CLAIM_TUPLE -> IdvRequirementReason.CANDIDATE_MATCH_CONFIRMATION
        KnownHolderState.EXPIRED_BINDING -> IdvRequirementReason.EXPIRED_BINDING
        KnownHolderState.NOT_FOUND -> IdvRequirementReason.FIRST_TIME_LINK
        KnownHolderState.MATCHED_HOLDER_KEY ->
            error("Known holder fast path should not route MATCHED_HOLDER_KEY through IDV_REQUIRED")
    }

    private fun idvMessageFor(reason: IdvRequirementReason): String = when (reason) {
        IdvRequirementReason.CANDIDATE_MATCH_CONFIRMATION ->
            "Identity verification is required to confirm the existing wallet candidate match"
        IdvRequirementReason.EXPIRED_BINDING ->
            "Identity verification is required because your existing wallet binding has expired"
        IdvRequirementReason.FIRST_TIME_LINK ->
            "Identity verification is required to link your wallet"
        IdvRequirementReason.FORCED_RECONCILIATION ->
            "Identity verification is required because fresh reconciliation was requested"
    }

    @ContributesTo(SessionScope::class)
    interface Component {
        val oid4vpUserAuthenticationProvider: UserAuthenticationProvider
        val oid4vpAuthBridge: Oid4vpAuthBridge
    }
}

/**
 * URL encoding for query parameters.
 */
private fun String.encodeURLQueryComponent(): String {
    val sb = StringBuilder()
    for (char in this) {
        when {
            char.isLetterOrDigit() || char in "-._~" -> sb.append(char)
            else -> {
                val bytes = char.toString().encodeToByteArray()
                for (byte in bytes) {
                    sb.append('%')
                    sb.append(HEX_CHARS[(byte.toInt() shr 4) and 0xF])
                    sb.append(HEX_CHARS[byte.toInt() and 0xF])
                }
            }
        }
    }
    return sb.toString()
}

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
