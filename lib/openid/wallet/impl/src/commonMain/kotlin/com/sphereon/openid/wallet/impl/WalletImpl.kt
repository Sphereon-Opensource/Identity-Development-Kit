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

package com.sphereon.openid.wallet.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.client.OAuth2Client
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.service.PkceService
import com.sphereon.oauth2.client.util.buildUrl
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.holder.Oid4vciHolder
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.SubmissionResult
import com.sphereon.openid.wallet.AuthCodeStart
import com.sphereon.openid.wallet.IdentifierRef
import com.sphereon.openid.wallet.ObtainCredentialRequest
import com.sphereon.openid.wallet.PresentationResult
import com.sphereon.openid.wallet.TokenSet
import com.sphereon.openid.wallet.Wallet
import com.sphereon.openid.wallet.WalletConfig
import com.sphereon.openid.wallet.WalletCredentialInstance
import com.sphereon.openid.wallet.WalletDocument
import com.sphereon.openid.wallet.WalletDocumentStore
import com.sphereon.openid.wallet.WalletIdentityResolver
import com.sphereon.sdjwt.vc.SdJwtVcVerificationOpts
import com.sphereon.sdjwt.vc.VerifySdJwtVcArgs
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import com.sphereon.openid.oid4vp.holder.WalletConfig as Oid4vpWalletConfig

private const val UNKNOWN_FORMAT = "unknown"

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Wallet>())
class WalletImpl(
    override val documents: WalletDocumentStore,
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
    override suspend fun createHolderKey(alias: String?): IdkResult<String, IdkError> {
        val result = keyManagerService.generateKeyResult(alias = alias)
        if (result.isErr) return Err(result.error)
        val keyPair =
            result.value.keyPair
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "generateKeyResult returned no key pair"))
        return Ok(keyPair.kid ?: keyPair.alias)
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

    override suspend fun obtainCredential(request: ObtainCredentialRequest): IdkResult<WalletDocument, IdkError> {
        val metadataResult = oid4vciHolder.resolveIssuerMetadata(request.credentialIssuer)
        if (metadataResult.isErr) return Err(metadataResult.error)
        val metadata = metadataResult.value

        val credConfig =
            metadata.credentialConfigurationsSupported[request.credentialConfigurationId]
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Unknown credentialConfigurationId: ${request.credentialConfigurationId}"))

        // Pool (and look up) held credentials under the (issuer, credential-type) identity, where the
        // credential type is the stable OID4VCI credential_configuration_id. A vct / doctype is NOT a
        // valid store key: the same vct is shared across issuers and across distinct credentials (and
        // pooled instances), so it cannot identify a held document — the issuer + configuration id can.
        // Instances of the same (issuer, configuration) pool into one document; different issuers or
        // configurations get distinct documents.
        val storedCredentialTypeId = request.credentialConfigurationId

        // Find or create the document for this issuer + credential type combination.
        // Use the metadata path to avoid loading full bodies of all matching documents.
        val existingMetaResult = documents.findMetadataByCredentialType(storedCredentialTypeId)
        if (existingMetaResult.isErr) return Err(existingMetaResult.error)
        val matchingMeta = existingMetaResult.value.firstOrNull { it.issuer.value == request.credentialIssuer }
        val existingDoc: WalletDocument? =
            if (matchingMeta != null) {
                val getResult = documents.get(matchingMeta.documentId)
                if (getResult.isErr) return Err(getResult.error)
                getResult.value
            } else {
                null
            }

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

        // Request all credentials in a single call.
        val credentialResult =
            oid4vciHolder.requestCredential(
                credentialEndpoint = metadata.credentialEndpoint,
                accessToken = request.accessToken,
                credentialConfigurationId = request.credentialConfigurationId,
                proofs = proofs,
            )
        if (credentialResult.isErr) return Err(credentialResult.error)
        val credentialResponse = credentialResult.value
        val responseItems =
            credentialResponse.credentials
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential response contained no credentials"))

        val format = credConfig.format ?: UNKNOWN_FORMAT
        val newInstances =
            responseItems.map { item ->
                val raw =
                    (item.credential as? JsonPrimitive)?.content
                        ?: return Err(IdkError.UNKNOWN_ERROR(message = "Credential item is not a primitive string"))
                WalletCredentialInstance(
                    credentialId = Uuid.v4String(),
                    format = format,
                    raw = raw,
                    holderKeyAlias = request.holderKeyAlias,
                )
            }

        // Verify the issued credential BEFORE storing it. For SD-JWT VC formats the wallet
        // verifies the issuer signature by resolving the issuer key from the JWS protected
        // header `kid` (e.g. did:jwk:<...>#0), reaching the same conclusion the verifier
        // would later. A credential the wallet cannot validate is never accepted into the store.
        // Type-metadata + status checks are disabled here: the wallet trusts the offer flow for
        // the credential type and only asserts cryptographic issuer-signature integrity on receipt.
        val credentialFormatOnReceipt = CredentialFormat.fromValueLenient(format)
        if (credentialFormatOnReceipt?.isSdJwt == true) {
            for (instance in newInstances) {
                val verifyResult =
                    verifySdJwtVcCommand.execute(
                        VerifySdJwtVcArgs(
                            sdJwt = instance.raw,
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
                                "Refusing to store issued credential '${request.credentialConfigurationId}': " +
                                    "issuer-signature verification failed: ${verifyResult.error.message.defaultMessage}",
                        ),
                    )
                }
            }
        }

        val issuerType = if (request.credentialIssuer.startsWith("did:")) IdentifierType.DID else IdentifierType("https")
        val rawIssuerRef = IdentifierRef(type = issuerType, value = request.credentialIssuer)
        val updatedDoc =
            if (existingDoc != null) {
                newInstances.fold(existingDoc) { doc, inst -> doc.withAddedInstance(inst) }
            } else {
                val resolvedIssuerResult = identityResolver.resolve(rawIssuerRef, IdentityRole.ISSUER)
                if (resolvedIssuerResult.isErr) return Err(resolvedIssuerResult.error)

                // Extract credential subject(s) from the first instance and resolve each
                // through the identity book (no-op in IDK; EDK enriches with identityIdentifierId).
                val firstInstance = newInstances.first()
                val credentialFormat = CredentialFormat.fromValueLenient(firstInstance.format)
                val rawSubjectRefs =
                    if (credentialFormat != null) {
                        subjectExtractor.extractSubjects(credentialFormat, firstInstance.raw)
                    } else {
                        emptyList()
                    }
                val resolvedSubjects = mutableListOf<IdentifierRef>()
                for (subjectRef in rawSubjectRefs) {
                    val resolvedResult = identityResolver.resolve(subjectRef, IdentityRole.HOLDER)
                    if (resolvedResult.isErr) return Err(resolvedResult.error)
                    resolvedSubjects += resolvedResult.value
                }

                WalletDocument(
                    id = Uuid.v4String(),
                    issuer = resolvedIssuerResult.value,
                    subjects = resolvedSubjects,
                    credentialTypeId = storedCredentialTypeId,
                    issuerDisplay = metadata.display ?: emptyList(),
                    credentialDisplay = credConfig.display ?: emptyList(),
                    claims = credConfig.claims ?: emptyList(),
                    credentials = newInstances,
                )
            }

        val upsertResult = documents.upsert(updatedDoc)
        if (upsertResult.isErr) return Err(upsertResult.error)
        return Ok(updatedDoc)
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

        // Resolve the verifier identity once for all queries in this presentation.
        val verifierClientId = resolvedRequest.verifierInfo.clientId
        val verifierType = if (verifierClientId.startsWith("did:")) IdentifierType.DID else IdentifierType("https")
        val rawVerifierRef = IdentifierRef(type = verifierType, value = verifierClientId)
        val resolvedVerifierResult = identityResolver.resolve(rawVerifierRef, IdentityRole.VERIFIER)
        if (resolvedVerifierResult.isErr) return Err(resolvedVerifierResult.error)
        val verifierRef = resolvedVerifierResult.value

        for (query in queries) {
            val typeId = credentialTypeIdForQuery(query)
            // A credential type can be held in more than one document — the (issuer, credentialTypeId)
            // key means different issuers (or distinct credentials) of the same type are separate
            // documents. Match on metadata (cheap, no body load), then pick the first candidate that
            // actually has an available instance; never assume the first document is presentable.
            // (Richer per-RP / per-presentation instance selection is deferred to later config.)
            val metaResult = documents.findMetadataByCredentialType(typeId)
            if (metaResult.isErr) return Err(metaResult.error)
            var selected: Pair<WalletDocument, WalletCredentialInstance>? = null
            for (candidate in metaResult.value) {
                val docResult = documents.get(candidate.documentId)
                if (docResult.isErr) return Err(docResult.error)
                val candidateDoc = docResult.value ?: continue
                val candidateInstance = candidateDoc.unusedInstance() ?: continue
                selected = candidateDoc to candidateInstance
                break
            }
            val (doc, instance) =
                selected
                    ?: return Err(IdkError.NOT_FOUND_ERROR(message = "no held credential matches the request for type '$typeId'"))

            // Mark this instance as bound to the verifier and persist the update so
            // WalletDocumentMetadata.boundInstanceCount reflects the presentation.
            val boundInstance = instance.copy(boundTo = verifierRef)
            val updatedDoc =
                doc.copy(
                    credentials = doc.credentials.map { if (it.credentialId == instance.credentialId) boundInstance else it }
                )
            val upsertResult = documents.upsert(updatedDoc)
            if (upsertResult.isErr) return Err(upsertResult.error)

            selectedCredentials +=
                SelectedCredential(
                    credentialQueryId = query.id,
                    credentialId = boundInstance.credentialId,
                    presentation = boundInstance.raw,
                    format = boundInstance.format,
                    // Carry the holder key so the OID4VP holder can produce a Key Binding JWT for
                    // SD-JWT presentations (binds to verifier client_id + request nonce).
                    holderKeyAlias = boundInstance.holderKeyAlias,
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

        return Ok(PresentationResult(submitted = true, redirectUri = redirectUri))
    }

    private fun credentialTypeIdForQuery(query: DcqlCredentialQuery): String {
        val meta = query.meta ?: return query.id
        val vctValues = meta["vct_values"]
        if (vctValues is JsonArray) {
            val first = vctValues.firstOrNull()
            if (first is JsonPrimitive && first.isString) return first.content
        }
        val doctypeValue = meta["doctype_value"]
        if (doctypeValue is JsonPrimitive && doctypeValue.isString) return doctypeValue.content
        return query.id
    }
}
