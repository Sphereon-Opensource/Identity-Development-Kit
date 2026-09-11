/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.statuslist.impl

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.cache.CacheModule
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.CryptoServices
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.X509VerifyServiceImpl
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.context.PrincipalType
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.MdocCwtStatusListSigningArgs
import com.sphereon.statuslist.MdocStatusListPayload
import com.sphereon.statuslist.MdocStatusListProfile
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.UpdateEntryStatusArgs
import com.sphereon.statuslist.impl.codec.MdocRevocationCwtClaimsCodecImpl
import com.sphereon.statuslist.impl.driver.InMemoryStatusListDriver
import com.sphereon.statuslist.impl.driver.InMemoryStatusListStore
import com.sphereon.statuslist.impl.resolve.StatusListResolverImpl
import com.sphereon.statuslist.impl.sign.CwtStatusListSigner
import com.sphereon.statuslist.impl.sign.JwsStatusListSigner
import com.sphereon.statuslist.impl.sign.LocalStatusListJwsSigningService
import com.sphereon.statuslist.impl.sign.MdocCwtStatusListSigner
import com.sphereon.statuslist.spi.StatusListSigner
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Crypto-resolver integration coverage for ISO/IEC 18013-5 status and revocation:
 * real software KMS key + certificate -> real mdoc CWT signer -> in-memory status allocation/update
 * -> mock-hosted bytes -> resolver certificate/signature validation. REST, persistence, and deployed
 * product coverage are intentionally outside this test.
 */
class MdocStatusListCryptoResolverIntegrationTest {
    private val app = createJvmStatusListTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session =
        context.sessionContextManager.createOrGetFromId(
            "mdoc-status-e2e",
            principalType = PrincipalType.USER,
        )
    private lateinit var kms: KeyManagerService
    private lateinit var jwtService: JwtService
    private lateinit var coseCrypto: com.sphereon.crypto.core.CoseCryptoService
    private lateinit var execution: SessionExecution
    private lateinit var issuerCertificate: String
    private lateinit var issuerCertificateDer: ByteArray
    private lateinit var hosted: MutableMap<String, ByteArray>
    private lateinit var hostedContentTypes: MutableMap<String, String>

    @BeforeTest
    fun setUp() {
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
                SoftwareKmsProviderConfig(
                    id = "mdoc-status-e2e-provider",
                    autoCreateCertificate = true,
                ),
                session.asCoreApiServiceGraph().serviceExecution,
            )
        kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
        execution = (session.graph as SessionExecution.Graph).sessionExecution
        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
        coseCrypto = (session.graph as CryptoServices.Graph).cryptoServices.cose
        hosted = mutableMapOf()
        hostedContentTypes = mutableMapOf()
    }

    @Test
    fun genericTokenStatusListCwtRejectsExpiredTokenThroughResolver() =
        runBlocking {
            val alias = createCertificateBearingKey("generic-status-expired")
            val uri = "https://issuer.example/statuslists/generic-expired"
            val driver = newDriver()
            driver.createStatusList(
                CreateStatusListArgs(
                    correlationId = "generic-expired",
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    purposes = listOf(StatusPurpose.REVOCATION),
                    proofFormat = StatusProofFormat.CWT,
                    issuer = "https://issuer.example",
                    statusListUri = uri,
                    length = 256,
                    bitsPerStatus = 1,
                    signingKeyAlias = alias,
                    signingKeyMode = "x5c",
                    validUntil = Clock.System.now().plus(-1, DateTimeUnit.HOUR),
                ),
            ).getOrElse { fail("create generic expired status list: $it") }

            val token =
                driver.getStatusListToken(StatusListRef(correlationId = "generic-expired"))
                    .getOrElse { fail("get generic expired token: $it") }
                    ?: fail("generic expired token was not published")
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, token.contentType)
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, decodeGenericCwt(token).protectedHeader.typ?.value)
            assertTrue(
                decodeGenericCwtExpiry(token) < Clock.System.now().epochSeconds,
                "generic CWT claim 4 must be before the current time",
            )
            hosted[uri] = token.rawBytes()
            hostedContentTypes[uri] = token.contentType

            val result = resolveResult(uri, 0)
            val error = (result as? com.sphereon.core.api.Err)?.error ?: fail("expired generic CWT must fail through the public resolver path")
            assertTrue(
                error.message.defaultMessage.contains("status-list CWT is expired"),
                "resolver must identify the expired generic CWT: ${error.message.defaultMessage}",
            )
        }

    @Test
    fun genericTokenStatusListCwtResolvesBeforeExpiryThroughResolver() =
        runBlocking {
            val alias = createCertificateBearingKey("generic-status-future")
            val uri = "https://issuer.example/statuslists/generic-future"
            val driver = newDriver()
            driver.createStatusList(
                CreateStatusListArgs(
                    correlationId = "generic-future",
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    purposes = listOf(StatusPurpose.REVOCATION),
                    proofFormat = StatusProofFormat.CWT,
                    issuer = "https://issuer.example",
                    statusListUri = uri,
                    length = 256,
                    bitsPerStatus = 1,
                    signingKeyAlias = alias,
                    signingKeyMode = "x5c",
                    validUntil = Clock.System.now().plus(24, DateTimeUnit.HOUR),
                ),
            ).getOrElse { fail("create generic future status list: $it") }

            val token =
                driver.getStatusListToken(StatusListRef(correlationId = "generic-future"))
                    .getOrElse { fail("get generic future token: $it") }
                    ?: fail("generic future token was not published")
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, token.contentType)
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, decodeGenericCwt(token).protectedHeader.typ?.value)
            assertTrue(
                decodeGenericCwtExpiry(token) > Clock.System.now().epochSeconds,
                "generic CWT claim 4 must be after the current time",
            )
            hosted[uri] = token.rawBytes()
            hostedContentTypes[uri] = token.contentType

            val resolved = resolveResult(uri, 0).getOrElse { fail("resolve future generic CWT: $it") }
            assertEquals(StatusValues.VALID, resolved.value)
        }

    @Test
    fun tokenStatusListCwtIsSignedHostedResolvedAndFailsClosedAfterRevocation() =
        runBlocking {
            val alias = createCertificateBearingKey("mdoc-status-token")
            val uri = "https://issuer.example/statuslists/mdoc-token"
            val driver = newDriver()
            driver.createStatusList(
                CreateStatusListArgs(
                    correlationId = "mdoc-token",
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    purposes = listOf(StatusPurpose.REVOCATION),
                    proofFormat = StatusProofFormat.CWT,
                    issuer = "https://issuer.example",
                    statusListUri = uri,
                    length = 256,
                    bitsPerStatus = 1,
                    signingKeyAlias = alias,
                    validUntil = Clock.System.now().plus(24, DateTimeUnit.HOUR),
                    mdocProfile = MdocStatusListProfile.STATUS_LIST,
                ),
            ).getOrElse { fail("create ISO Token Status List: $it") }
            driver.allocateEntry(
                AllocateEntryArgs(StatusListRef(correlationId = "mdoc-token"), explicitIndex = 42, credentialId = "mdl-42"),
            ).getOrElse { fail("allocate ISO status entry: $it") }

            val first = publish(driver, uri)
            val claims = decodeClaims(first)
            assertTrue(claims.payload is MdocStatusListPayload.Token)
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, first.contentType)
            assertEquals(StatusValues.VALID, resolve(uri, 42, first))

            driver.updateEntryStatus(
                UpdateEntryStatusArgs(EntryRef(correlationId = "mdoc-token", credentialId = "mdl-42"), StatusValues.INVALID),
            ).getOrElse { fail("revoke ISO status entry: $it") }
            val revoked = publish(driver, uri)
            assertEquals(StatusValues.INVALID, resolve(uri, 42, revoked))
            // An unavailable publication cannot turn into a valid credential.
            hosted.remove(uri)
            assertTrue(resolveResult(uri, 42).isErr)
        }

    @Test
    fun identifierListCwtAllocatesUniqueIdentifiersAndRejectsAfterRevocation() =
        runBlocking {
            val alias = createCertificateBearingKey("mdoc-status-identifier")
            val uri = "https://issuer.example/statuslists/mdoc-identifiers"
            val identifier = "mdl-unique-identifier".encodeToByteArray()
            val driver = newDriver()
            driver.createStatusList(
                CreateStatusListArgs(
                    correlationId = "mdoc-identifiers",
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    purposes = listOf(StatusPurpose.REVOCATION),
                    proofFormat = StatusProofFormat.CWT,
                    issuer = "https://issuer.example",
                    statusListUri = uri,
                    length = 256,
                    bitsPerStatus = 1,
                    signingKeyAlias = alias,
                    validUntil = Clock.System.now().plus(24, DateTimeUnit.HOUR),
                    mdocProfile = MdocStatusListProfile.IDENTIFIER_LIST,
                ),
            ).getOrElse { fail("create ISO Identifier List: $it") }
            driver.allocateEntry(
                AllocateEntryArgs(
                    StatusListRef(correlationId = "mdoc-identifiers"),
                    identifier = identifier,
                    credentialId = "mdl-identifier",
                ),
            ).getOrElse { fail("allocate identifier: $it") }
            val duplicate = driver.allocateEntry(
                AllocateEntryArgs(StatusListRef(correlationId = "mdoc-identifiers"), identifier = identifier.copyOf()),
            )
            assertTrue(duplicate.isErr, "identifier allocation must be unique")

            val active = publish(driver, uri)
            assertEquals(StatusValues.VALID, resolve(uri, 0, active, identifier))
            driver.updateEntryStatus(
                UpdateEntryStatusArgs(EntryRef(correlationId = "mdoc-identifiers", credentialId = "mdl-identifier"), StatusValues.INVALID),
            ).getOrElse { fail("revoke identifier: $it") }
            val revoked = publish(driver, uri)
            val revokedClaims = decodeClaims(revoked)
            val payload = revokedClaims.payload as? MdocStatusListPayload.IdentifierList
                ?: fail("expected ISO Identifier List payload")
            assertTrue(payload.identifiers.any { it.contentEquals(identifier) })
            assertEquals(StatusValues.INVALID, resolve(uri, 0, revoked, identifier))
            assertTrue(resolveResult(uri, 1, identifier = identifier).isErr, "Identifier List must reject non-zero index")
        }

    @Test
    fun mdocResolverAcceptsLongAndShortProtectedTypesPerProfileAndRejectsUnsupportedType() =
        runBlocking {
            val alias = createCertificateBearingKey("mdoc-status-typ")
            val expiresAt = Clock.System.now().epochSeconds + 3_600

            val statusLongUri = "https://issuer.example/statuslists/mdoc-typ-status-long"
            val statusLong = signMdocToken(alias, statusLongUri, null, null, expiresAt)
            assertEquals(StatusListContentTypes.STATUSLIST_CWT, decodeGenericCwt(statusLong).protectedHeader.typ?.value)
            assertEquals(StatusValues.VALID, resolve(statusLongUri, 0, statusLong))

            val statusShortUri = "https://issuer.example/statuslists/mdoc-typ-status-short"
            val statusShortSource = signMdocToken(alias, statusShortUri, null, null, expiresAt)
            val statusShort = resignWithProtectedType(statusShortSource, alias, "statuslist+cwt")
            assertEquals("statuslist+cwt", decodeGenericCwt(statusShort).protectedHeader.typ?.value)
            assertEquals(StatusValues.VALID, resolve(statusShortUri, 0, statusShort))

            val identifier = "mdoc-typ-identifier".encodeToByteArray()
            val identifierLongUri = "https://issuer.example/statuslists/mdoc-typ-identifier-long"
            val identifierLong =
                signMdocToken(
                    alias,
                    identifierLongUri,
                    null,
                    null,
                    expiresAt,
                    MdocStatusListPayload.IdentifierList(listOf(identifier)),
                )
            assertEquals(StatusListContentTypes.IDENTIFIERLIST_CWT, decodeGenericCwt(identifierLong).protectedHeader.typ?.value)
            assertEquals(StatusValues.INVALID, resolve(identifierLongUri, 0, identifierLong, identifier))

            val identifierShortUri = "https://issuer.example/statuslists/mdoc-typ-identifier-short"
            val identifierShortSource =
                signMdocToken(
                    alias,
                    identifierShortUri,
                    null,
                    null,
                    expiresAt,
                    MdocStatusListPayload.IdentifierList(listOf(identifier)),
                )
            val identifierShort = resignWithProtectedType(identifierShortSource, alias, "identifierlist+cwt")
            assertEquals("identifierlist+cwt", decodeGenericCwt(identifierShort).protectedHeader.typ?.value)
            assertEquals(StatusValues.INVALID, resolve(identifierShortUri, 0, identifierShort, identifier))

            val unsupportedUri = "https://issuer.example/statuslists/mdoc-typ-unsupported"
            val unsupportedSource = signMdocToken(alias, unsupportedUri, null, null, expiresAt)
            val unsupported = resignWithProtectedType(unsupportedSource, alias, "application/unsupported+cwt")
            assertResolverError(unsupportedUri, unsupported, "status-list CWT has an unsupported protected typ")

            val genericUri = "https://issuer.example/statuslists/generic-typ-short"
            val genericDriver = newDriver()
            genericDriver.createStatusList(
                CreateStatusListArgs(
                    correlationId = "generic-typ-short",
                    spec = StatusListSpec.TOKEN_STATUS_LIST,
                    purposes = listOf(StatusPurpose.REVOCATION),
                    proofFormat = StatusProofFormat.CWT,
                    issuer = "https://issuer.example",
                    statusListUri = genericUri,
                    length = 256,
                    bitsPerStatus = 1,
                    signingKeyAlias = alias,
                    signingKeyMode = "x5c",
                    validUntil = Clock.System.now().plus(24, DateTimeUnit.HOUR),
                ),
            ).getOrElse { fail("create generic status list for short typ rejection: $it") }
            val genericLong =
                genericDriver.getStatusListToken(StatusListRef(correlationId = "generic-typ-short"))
                    .getOrElse { fail("get generic status-list token for short typ rejection: $it") }
                    ?: fail("generic status-list token for short typ rejection was not published")
            val genericShort = resignWithProtectedType(genericLong, alias, "statuslist+cwt")
            assertResolverError(genericUri, genericShort, "status-list CWT has an unsupported protected typ")
        }

    @Test
    fun mdocResolverRequiresAnAllowedAlgorithmInTheProtectedHeaderBeforeCryptoVerification() =
        runBlocking {
            val alias = createCertificateBearingKey("mdoc-status-protected-alg")
            val now = Clock.System.now().epochSeconds
            val valid = signMdocToken(alias, "https://issuer.example/statuslists/mdoc-protected-alg-valid", now, null, now + 3_600)
            assertEquals(CoseAlgorithm.ES256, decodeGenericCwt(valid).protectedHeader.alg)
            assertEquals(StatusValues.VALID, resolve(validSubject(valid), 0, valid))

            val missingUri = "https://issuer.example/statuslists/mdoc-protected-alg-missing"
            val missing = rewriteHeaders(valid, protectedAlgorithm = null)
            assertResolverError(missingUri, missing, "mdoc revocation CWT requires protected alg")

            val unprotectedOnlyUri = "https://issuer.example/statuslists/mdoc-protected-alg-unprotected-only"
            val unprotectedOnly =
                rewriteHeaders(
                    valid,
                    protectedAlgorithm = null,
                    unprotectedAlgorithm = CoseAlgorithm.ES256,
                )
            assertResolverError(unprotectedOnlyUri, unprotectedOnly, "mdoc revocation CWT requires protected alg")

            listOf(
                CoseAlgorithm.ES256K,
                CoseAlgorithm.HS256,
                CoseAlgorithm.RS256,
                CoseAlgorithm.PS256,
                CoseAlgorithm.A128GCM,
            )
                .forEach { disallowed ->
                    val uri = "https://issuer.example/statuslists/mdoc-protected-alg-${disallowed.name.lowercase()}"
                    val token = rewriteHeaders(valid, protectedAlgorithm = disallowed)
                    assertResolverError(uri, token, "mdoc revocation CWT protected alg is unsupported")
                }
        }

    @Test
    fun mdocResolverEnforcesSignedIatAndTtlPublicationFreshnessClaims() =
        runBlocking {
            val alias = createCertificateBearingKey("mdoc-status-temporal")
            val now = Clock.System.now().epochSeconds
            val cases =
                listOf(
                    SignedTemporalCase(
                        name = "expired-exp",
                        issuedAt = null,
                        ttl = null,
                        expiresAt = now - 1,
                        expectedError = "mdoc revocation CWT is expired",
                    ),
                    SignedTemporalCase(
                        name = "ttl-without-iat",
                        issuedAt = null,
                        ttl = 60,
                        expiresAt = now + 3_600,
                        expectedError = "mdoc revocation CWT ttl requires iat",
                    ),
                    SignedTemporalCase(
                        name = "zero-ttl",
                        issuedAt = now,
                        ttl = 0,
                        expiresAt = now + 3_600,
                        expectedError = "mdoc revocation CWT ttl must be greater than zero",
                    ),
                    SignedTemporalCase(
                        name = "derived-expired",
                        issuedAt = now - 120,
                        ttl = 60,
                        expiresAt = now + 3_600,
                        expectedError = "mdoc revocation CWT publication freshness is expired",
                    ),
                    SignedTemporalCase(
                        name = "overflow",
                        issuedAt = Long.MAX_VALUE - 5,
                        ttl = 6,
                        expiresAt = Long.MAX_VALUE,
                        expectedError = "mdoc revocation CWT iat plus ttl overflows",
                    ),
                    SignedTemporalCase(
                        name = "future-iat",
                        issuedAt = now + 3_600,
                        ttl = 60,
                        expiresAt = Long.MAX_VALUE,
                        expectedError = "mdoc revocation CWT iat is in the future",
                    ),
                )

            cases.forEach { case ->
                val uri = "https://issuer.example/statuslists/mdoc-temporal-${case.name}"
                val token = signMdocToken(alias, uri, case.issuedAt, case.ttl, case.expiresAt)
                assertResolverError(uri, token, case.expectedError)
            }

            val validUri = "https://issuer.example/statuslists/mdoc-temporal-valid"
            val valid = signMdocToken(alias, validUri, now, 3_600, now + 7_200)
            assertEquals(StatusValues.VALID, resolve(validUri, 0, valid))
        }

    private suspend fun createCertificateBearingKey(alias: String): String {
        val sourceAlias = "$alias-source"
        val generated =
            kms.generateKey(
                alias = sourceAlias,
                alg = SignatureAlgorithm.ECDSA_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
            )
        val generatedCertificate = generated.jose.publicJwk.x5c?.firstOrNull()
            ?: fail("auto-created signing key has no x5c")
        val privateKeyInfo = ManagedKeyInfo.build(generated.joseToManagedKeyInfo(KeyVisibility.PRIVATE), alias = alias, providerId = generated.providerId)
        kms.storeKeyResult(
            keyInfo = privateKeyInfo,
            providerId = generated.providerId,
            alias = alias,
            certChain = arrayOf(Certificate.fromDer(generatedCertificate.decodeFrom(Encoding.BASE64))),
        ).getOrElse { fail("store certificate-bearing signing key: $it") }
        val key = kms.getKeyResult(KeyInfo<Nothing>(alias = alias)).getOrElse { fail("get signing key: $it") }.key
            ?: fail("missing signing key")
        val jwk = key.key as? com.sphereon.crypto.core.jose.Jwk ?: fail("signing key is not JWK")
        issuerCertificate = jwk.x5c?.firstOrNull() ?: fail("auto-created signing key has no x5c")
        issuerCertificateDer = issuerCertificate.decodeFrom(Encoding.BASE64)
        return alias
    }

    private fun newDriver(): InMemoryStatusListDriver {
        val mdocSigner = MdocCwtStatusListSigner(coseCrypto, CoseSign1CborCodecImpl(), kms)
        val signer: StatusListSigner =
            JwsStatusListSigner(
                LocalStatusListJwsSigningService(jwtService, kms),
                NoopDidProviderRegistry,
                NoopDidResolverRegistry,
                CwtStatusListSigner(coseCrypto, CoseSign1CborCodecImpl(), kms, NoopDidProviderRegistry),
                mdocSigner,
            )
        return InMemoryStatusListDriver(InMemoryStatusListStore(), signer, execution)
    }

    private suspend fun signMdocToken(
        alias: String,
        uri: String,
        issuedAt: Long?,
        ttl: Long?,
        expiresAt: Long,
        payload: MdocStatusListPayload = MdocStatusListPayload.Token(bits = 1, list = byteArrayOf(0)),
    ): StatusListToken =
        MdocCwtStatusListSigner(coseCrypto, CoseSign1CborCodecImpl(), kms)
            .sign(
                MdocCwtStatusListSigningArgs(
                    issuer = "https://issuer.example",
                    statusListUri = uri,
                    signingKeyName = alias,
                    expiresAtEpochSeconds = expiresAt,
                    issuedAtEpochSeconds = issuedAt,
                    ttlSeconds = ttl,
                    payload = payload,
                ),
            ).getOrElse { fail("sign mdoc status token for $uri: $it") }

    private suspend fun resignWithProtectedType(
        source: StatusListToken,
        alias: String,
        protectedType: String,
    ): StatusListToken {
        val managed =
            kms.getKeyResult(KeyInfo<Nothing>(alias = alias)).getOrElse { fail("get signing key for protected typ: $it") }.key
                ?: fail("missing signing key for protected typ")
        val codec = CoseSign1CborCodecImpl()
        val decoded = codec.decode(source.rawBytes()).getOrElse { fail("decode source mdoc CWT: $it") }.value
        val payload = decoded.payload?.value ?: fail("source mdoc CWT has detached payload")
        val input =
            CoseSign1Input
                .Builder()
                .withPayload(payload)
                .withProtectedHeader(decoded.protectedHeader.copy(typ = CborString(protectedType)))
                .withUnprotectedHeader(decoded.unprotectedHeader)
                .build()
        val signed =
            coseCrypto.sign1<Any>(
                input = input,
                keyInfo = KeyInfo<Nothing>(alias = managed.alias ?: alias, providerId = managed.providerId),
                requireX5Chain = true,
            )
        val encoded = codec.encode(signed.coseSign1).getOrElse { fail("encode re-signed mdoc CWT: $it") }
        return source.copy(token = encoded.encodeToBase64Url(), tokenBytes = encoded)
    }

    private fun rewriteHeaders(
        source: StatusListToken,
        protectedAlgorithm: CoseAlgorithm?,
        unprotectedAlgorithm: CoseAlgorithm? = null,
    ): StatusListToken {
        val codec = CoseSign1CborCodecImpl()
        val decoded = codec.decode(source.rawBytes()).getOrElse { fail("decode signed mdoc CWT: $it") }.value
        val rewritten =
            decoded.copy(
                protectedHeader = decoded.protectedHeader.copy(alg = protectedAlgorithm),
                unprotectedHeader =
                    unprotectedAlgorithm?.let { algorithm ->
                        (decoded.unprotectedHeader ?: CoseHeaderCbor()).copy(alg = algorithm)
                    },
            )
        val encoded = codec.encode(rewritten).getOrElse { fail("encode rewritten mdoc CWT: $it") }
        return source.copy(tokenBytes = encoded)
    }

    private fun validSubject(token: StatusListToken): String =
        decodeClaims(token).subject ?: fail("signed mdoc CWT has no subject")

    private suspend fun assertResolverError(
        uri: String,
        token: StatusListToken,
        expectedMessage: String,
    ) {
        hosted[uri] = token.rawBytes()
        hostedContentTypes[uri] = token.contentType
        val error = (resolveResult(uri, 0) as? com.sphereon.core.api.Err)?.error
            ?: fail("resolver must reject $uri")
        assertTrue(
            error.message.defaultMessage.contains(expectedMessage),
            "expected '$expectedMessage', got '${error.message.defaultMessage}'",
        )
    }

    private suspend fun publish(driver: InMemoryStatusListDriver, uri: String): com.sphereon.statuslist.StatusListToken {
        val token = driver.getStatusListToken(com.sphereon.statuslist.StatusListRef(statusListUri = uri)).getOrElse { fail("host token: $it") }
            ?: fail("missing hosted token")
        hosted[uri] = token.rawBytes()
        hostedContentTypes[uri] = token.contentType
        return token
    }

    private fun decodeClaims(token: com.sphereon.statuslist.StatusListToken) =
        MdocRevocationCwtClaimsCodecImpl.decode(
            CoseSign1CborCodecImpl().decode(token.rawBytes()).getOrElse { fail("decode COSE: $it") }.value.payload?.value
                ?: fail("detached payload"),
        )

    private fun decodeGenericCwt(token: com.sphereon.statuslist.StatusListToken) =
        CoseSign1CborCodecImpl().decode(token.rawBytes()).getOrElse { fail("decode generic CWT: $it") }.value

    private fun decodeGenericCwtExpiry(token: com.sphereon.statuslist.StatusListToken): Long {
        val payload = decodeGenericCwt(token).payload?.value ?: fail("generic CWT payload is detached")
        val decoded = Cbor.tryDecode(payload).getOrElse { fail("decode generic CWT payload: $it") }
        val claimsItem =
            when (decoded) {
                is CborEncodedItem<*> -> Cbor.tryDecode(decoded.value.taggedItem.value).getOrElse { fail("decode generic CWT claims: $it") }
                else -> decoded
            }
        val claims = claimsItem as? CborMap<*, *> ?: fail("generic CWT claims must be a CBOR map")
        return claims.value.entries
            .firstOrNull { (key, _) -> key is CborUInt && key.value == 4L }
            ?.value
            .let { it as? CborUInt }
            ?.value
            ?: fail("generic CWT claim 4 must be an unsigned integer")
    }

    private suspend fun resolve(uri: String, index: Int, token: com.sphereon.statuslist.StatusListToken, identifier: ByteArray? = null): Int {
        hosted[uri] = token.rawBytes()
        hostedContentTypes[uri] = token.contentType
        val result = resolveResult(uri, index, identifier = identifier).getOrElse { fail("resolve status: $it") }
        return result.value
    }

    private suspend fun resolveResult(uri: String, index: Int, identifier: ByteArray? = null) =
        resolver().resolveStatus(
            ResolveStatusArgs(
                uri = uri,
                index = index,
                expectedSpec = if (identifier == null) StatusListSpec.TOKEN_STATUS_LIST else null,
                expectedFormat = StatusProofFormat.CWT,
                identifier = identifier,
                trustedCerts = arrayOf(issuerCertificate),
                expectedCertificate = issuerCertificateDer,
            ),
        )

    private fun resolver(): StatusListResolverImpl {
        val x509 = X509VerifyServiceImpl()
        x509.setTrustedCerts(arrayOf(issuerCertificate))
        return StatusListResolverImpl(
            httpClientFactory = MockFactory(hosted, hostedContentTypes),
            jwtService = jwtService,
            coseSign1Codec = CoseSign1CborCodecImpl(),
            coseCryptoService = coseCrypto,
            x509VerifyService = x509,
            cacheService = (app as CacheModule.Graph).cacheService,
            execution = execution,
        )
    }

    private class MockFactory(
        private val hosted: MutableMap<String, ByteArray>,
        private val contentTypes: MutableMap<String, String>,
    ) : HttpClientFactory {
        override fun createClient(options: HttpClientOptions): HttpClient =
            HttpClient(MockEngine { request ->
                val uri = request.url.toString()
                val body = hosted[uri]
                if (body == null) {
                    respond("not found", io.ktor.http.HttpStatusCode.NotFound)
                } else {
                    respond(
                        body,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(contentTypes[uri] ?: "application/octet-stream"),
                            HttpHeaders.CacheControl to listOf("no-store"),
                        ),
                    )
                }
            })

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true
        override fun getEngineTypesSupported(): List<HttpClientEngineType> = emptyList()
        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private data class SignedTemporalCase(
        val name: String,
        val issuedAt: Long?,
        val ttl: Long?,
        val expiresAt: Long,
        val expectedError: String,
    )
}
