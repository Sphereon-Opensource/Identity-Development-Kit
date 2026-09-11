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

package com.sphereon.statuslist.impl.resolve

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.CborUInt
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.cache.HttpCacheExpiryPolicy
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseSign1CborCodec
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.core.x509.X509VerificationProfile
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.MdocStatusListPayload
import com.sphereon.statuslist.impl.codec.MdocRevocationCwtClaimsCodecImpl
import com.sphereon.statuslist.impl.codec.StatusListCodec
import com.sphereon.statuslist.impl.envelope.BitstringStatusListEnvelope
import com.sphereon.statuslist.impl.envelope.TokenStatusListEnvelope
import com.sphereon.statuslist.spi.StatusListResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant as KotlinInstant

private typealias ResolveResult = IdkResult<ResolvedStatus, IdkError>

private const val MDOC_STATUS_LIST_CWT_SHORT_TYP = "statuslist+cwt"
private const val MDOC_IDENTIFIER_LIST_CWT_SHORT_TYP = "identifierlist+cwt"

private fun isMdocStatusListType(type: String?): Boolean =
    type == StatusListContentTypes.STATUSLIST_CWT || type == MDOC_STATUS_LIST_CWT_SHORT_TYP

private fun isMdocIdentifierListType(type: String?): Boolean =
    type == StatusListContentTypes.IDENTIFIERLIST_CWT || type == MDOC_IDENTIFIER_LIST_CWT_SHORT_TYP

/**
 * Validates the optional temporal claims of a JWT status-list envelope without relying on resolver
 * state or a wall clock. The caller supplies the current epoch second so this logic is directly
 * testable in common code.
 */
internal fun validateJwtStatusListTemporalClaims(
    payload: JsonObject,
    spec: StatusListSpec,
    nowEpochSeconds: Long,
): IdkResult<Unit, String> {
    when (spec) {
        StatusListSpec.TOKEN_STATUS_LIST -> {
            val expiryClaim = payload["exp"] ?: return Ok(Unit)
            val expiry =
                (expiryClaim as? JsonPrimitive)
                    ?.takeUnless { it.isString }
                    ?.doubleOrNull
                    ?.takeIf { it.isFinite() }
                    ?: return Err("Token Status List JWT exp must be a finite number")
            if (expiry <= nowEpochSeconds) {
                return Err("Token Status List JWT exp must be greater than the current time")
            }
        }

        StatusListSpec.BITSTRING_STATUS_LIST -> {
            val validUntilClaim = payload["validUntil"] ?: return Ok(Unit)
            val validUntil =
                (validUntilClaim as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.let { value ->
                        try {
                            Instant.parse(value)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                    ?: return Err("Bitstring Status List VC-JWT validUntil must be an ISO Instant")
            if (validUntil.epochSeconds <= nowEpochSeconds) {
                return Err("Bitstring Status List VC-JWT validUntil must be greater than the current time")
            }
        }
    }
    return Ok(Unit)
}

/**
 * Validates the required expiration claim of an ISO mdoc status-list CWT. The current epoch
 * second is supplied by the caller so the strict boundary is deterministic in common tests.
 */
internal fun validateMdocStatusListTemporalClaims(
    expiresAtEpochSeconds: Long,
    issuedAtEpochSeconds: Long?,
    ttlSeconds: Long?,
    nowEpochSeconds: Long,
): IdkResult<Unit, String> {
    if (expiresAtEpochSeconds <= nowEpochSeconds) {
        return Err("mdoc revocation CWT is expired")
    }
    if (ttlSeconds == null) {
        return Ok(Unit)
    }
    val issuedAt = issuedAtEpochSeconds ?: return Err("mdoc revocation CWT ttl requires iat")
    if (ttlSeconds <= 0) {
        return Err("mdoc revocation CWT ttl must be greater than zero")
    }
    if (issuedAt > Long.MAX_VALUE - ttlSeconds) {
        return Err("mdoc revocation CWT iat plus ttl overflows")
    }
    if (issuedAt + ttlSeconds <= nowEpochSeconds) {
        return Err("mdoc revocation CWT publication freshness is expired")
    }
    return Ok(Unit)
}

/** Requires an explicit protected signature algorithm before mdoc status COSE verification. */
internal fun validateMdocStatusListProtectedAlgorithm(algorithm: CoseAlgorithm?): IdkResult<Unit, String> =
    when (algorithm) {
        null -> Err("mdoc revocation CWT requires protected alg")
        CoseAlgorithm.ES256,
        CoseAlgorithm.ES384,
        CoseAlgorithm.ES512,
        CoseAlgorithm.EdDSA,
        -> Ok(Unit)
        else -> Err("mdoc revocation CWT protected alg is unsupported")
    }

/** Validates the optional integer expiration claim of a generic status-list CWT. */
internal fun validateGenericCwtTemporalClaims(
    expiresAtEpochSeconds: Long?,
    nowEpochSeconds: Long,
): IdkResult<Unit, String> =
    if (expiresAtEpochSeconds != null && expiresAtEpochSeconds <= nowEpochSeconds) {
        Err("status-list CWT is expired")
    } else {
        Ok(Unit)
    }

/**
 * Default [StatusListResolver]: fetches the hosted token, verifies its JWS or COSE_Sign1 signature
 * via the shared crypto services, decodes the bit at the requested index, and reports the status.
 * mdoc CWTs are handled as binary responses and are required to carry a protected x5chain and an
 * `exp` claim; generic JWT behavior remains unchanged.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListResolver>())
class StatusListResolverImpl(
    private val httpClientFactory: HttpClientFactory,
    private val jwtService: JwtService,
    private val coseSign1Codec: CoseSign1CborCodec,
    private val coseCryptoService: CoseCryptoService,
    private val x509VerifyService: X509VerifyService,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
) : StatusListResolver {
    private val cache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = CACHE_NAMESPACE,
                ttlConfig = CacheTtlConfig(tenant = CACHE_LOCAL_TTL),
            ),
        )
    }
    private val cacheJson = Json { encodeDefaults = true }
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<FlightKey, CompletableDeferred<FetchOutcome>>()

    // Create the HTTP client once per resolver instance and reuse its connection pool across calls
    // rather than allocating a new one per resolveStatus.
    private val client by lazy {
        httpClientFactory.createClient(
            HttpClientOptions.createDefault().copy(
                followRedirects = false,
                urlValidation = UrlValidationPolicy.BLOCK_PRIVATE,
            ),
        )
    }

    override suspend fun resolveStatus(args: ResolveStatusArgs): IdkResult<ResolvedStatus, IdkError> {
        if (args.index < 0) {
            return Err(StatusListErrors.resolutionFailed(args.uri, "status index ${args.index} is negative"))
        }
        val canonicalUri =
            try {
                validateUrl(args.uri)
            } catch (e: Exception) {
                return Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "invalid status-list URI", e))
            }
        val key =
            try {
                cacheKey(canonicalUri, args)
            } catch (e: Exception) {
                return Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "status-list cache key is too large", e))
            }
        readCached(canonicalUri, key, args)?.let { cached ->
            val checked = resolveFetchedSafely(args, cached.body, cached.contentType)
            if (checked.result.isOk) return checked.result
            removeCached(key)
        }
        return resolveAfterCacheMiss(canonicalUri, key, args)
    }

    private suspend fun resolveAfterCacheMiss(
        canonicalUri: String,
        key: String,
        args: ResolveStatusArgs,
    ): ResolveResult {
        val flightKey = FlightKey(execution.tenantId, key)
        while (true) {
            val created = CompletableDeferred<FetchOutcome>()
            val claim =
                inFlightMutex.withLock {
                    inFlight[flightKey]?.let { return@withLock FlightClaim(it, owner = false) }
                    if (inFlight.size >= MAX_IN_FLIGHT_ENTRIES) {
                        return@withLock null
                    }
                    inFlight[flightKey] = created
                    FlightClaim(created, owner = true)
                }
            if (claim == null) {
                return Err(
                    StatusListErrors.resolutionFailed(
                        args.uri,
                        "active status-list fetch limit exceeded",
                    ),
                )
            }

            val deferred = claim.deferred
            if (!claim.owner) {
                when (val outcome = deferred.await()) {
                    FetchOutcome.Retry -> continue
                    else -> return resolveOutcome(args, outcome)
                }
            }

            try {
                // A caller that lost the cache race must use the same validated path as a cache hit.
                val cached = readCached(canonicalUri, key, args)
                if (cached != null) {
                    val checked = resolveFetchedSafely(args, cached.body, cached.contentType)
                    if (checked.result.isOk) {
                        // Publish only after the owner has completed all validation work.
                        created.complete(FetchOutcome.Artifact(cached.body, cached.contentType))
                        return checked.result
                    }
                    removeCached(key)
                }
                when (val outcome =
                    try {
                        val response = withTimeout(HTTP_FETCH_TIMEOUT_MS) { fetchStatusList(canonicalUri) }
                        FetchOutcome.Fetched(response)
                    } catch (timeout: TimeoutCancellationException) {
                        FetchOutcome.Failure(
                            Err(
                                StatusListErrors.resolutionFailed(
                                    args.uri,
                                    "status-list HTTP fetch timed out",
                                    timeout,
                                ),
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        FetchOutcome.Failure(Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "fetch failed", e)))
                    }
                ) {
                    is FetchOutcome.Fetched -> {
                        val checked = resolveFetchedSafely(args, outcome.response.body, outcome.response.contentType)
                        if (checked.result.isOk) {
                            // Validation and cache publication are owner work and remain cancellable.
                            putCached(canonicalUri, key, args, outcome.response)
                            created.complete(FetchOutcome.Artifact(outcome.response.body, outcome.response.contentType))
                        } else {
                            created.complete(FetchOutcome.Failure(checked.result))
                        }
                        return checked.result
                    }
                    is FetchOutcome.Failure -> {
                        created.complete(outcome)
                        return outcome.result
                    }
                    is FetchOutcome.Artifact -> error("owner cannot consume an already-published artifact")
                    FetchOutcome.Retry -> error("owner cannot produce Retry")
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    inFlightMutex.withLock {
                        if (inFlight[flightKey] === created) {
                            inFlight.remove(flightKey)
                            created.complete(FetchOutcome.Retry)
                        }
                    }
                }
                throw cancelled
            } catch (e: Exception) {
                val failure = FetchOutcome.Failure(Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "status-list resolution failed", e)))
                created.complete(failure)
                return failure.result
            } finally {
                withContext(NonCancellable) {
                    inFlightMutex.withLock {
                        if (inFlight[flightKey] === created) inFlight.remove(flightKey)
                    }
                }
            }
        }
    }

    private suspend fun resolveOutcome(
        args: ResolveStatusArgs,
        outcome: FetchOutcome,
    ): ResolveResult =
        when (outcome) {
            FetchOutcome.Retry -> Err(StatusListErrors.resolutionFailed(args.uri, "status-list fetch was cancelled; retry required"))
            is FetchOutcome.Failure -> outcome.result
            is FetchOutcome.Artifact -> {
                resolveFetchedSafely(args, outcome.body, outcome.contentType).result
            }
            is FetchOutcome.Fetched -> error("waiter cannot consume an unvalidated status-list response")
        }

    private suspend fun resolveFetchedSafely(
        args: ResolveStatusArgs,
        body: ByteArray,
        contentType: String,
    ): ValidatedFetch =
        try {
            resolveFetched(args, body, contentType)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ValidatedFetch(Err(StatusListErrors.verificationFailed(args.uri, "status-list validation failed: ${e.message}", e)))
        }

    private suspend fun resolveFetched(
        args: ResolveStatusArgs,
        body: ByteArray,
        contentType: String,
    ): ValidatedFetch {
        val isCwt = contentType.contains("cwt", ignoreCase = true)
        args.expectedFormat?.let { expected ->
            val expectedCwt = expected == StatusProofFormat.CWT
            if (expectedCwt != isCwt) {
                return ValidatedFetch(
                    Err(
                        StatusListErrors.verificationFailed(
                            args.uri,
                            "status-list content type does not match expected format ${expected.value}",
                        ),
                    ),
                )
            }
        }
        if (isCwt) return ValidatedFetch(resolveCwt(args, body))

        val token = body.decodeToString().trim()
        val spec = args.expectedSpec ?: inferSpec(contentType)
        val valid =
            (try {
                jwtService
                    .verifyJws(VerifyJwsArgs(jws = JwsCompact(token)))
                    .getOrElse { return ValidatedFetch(Err(it)) }
            } catch (e: Exception) {
                return ValidatedFetch(Err(StatusListErrors.verificationFailed(args.uri, "malformed status-list JWT: ${e.message}", e)))
            }).isValid
        if (!valid) return ValidatedFetch(Err(StatusListErrors.verificationFailed(args.uri, "signature invalid")))

        return try {
            val payload =
                decodeJwtPayload(token)
                    ?: return ValidatedFetch(Err(StatusListErrors.verificationFailed(args.uri, "malformed JWT payload")))
            validateJwtStatusListTemporalClaims(payload, spec, kotlinx.datetime.Clock.System.now().epochSeconds)
                .getOrElse { return ValidatedFetch(Err(StatusListErrors.verificationFailed(args.uri, it))) }
            val (encodedList, bits, purpose) =
                when (spec) {
                    StatusListSpec.TOKEN_STATUS_LIST -> {
                        val content = TokenStatusListEnvelope.parse(payload)
                        Triple(content.encodedList, content.bitsPerStatus, null)
                    }

                    StatusListSpec.BITSTRING_STATUS_LIST -> {
                        val content = BitstringStatusListEnvelope.parse(payload)
                        Triple(content.encodedList, content.statusSize, StatusPurpose.fromValue(content.statusPurpose))
                    }
                }
            val bitset = StatusListCodec.decode(encodedList, bits, spec)
            val value = bitset.get(args.index)
            ValidatedFetch(
                Ok(
                    ResolvedStatus(
                        value = value,
                        purpose = purpose,
                        valid = value == StatusValues.VALID,
                        statusListUri = args.uri,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ValidatedFetch(Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "decode failed", e)))
        }
    }

    private fun inferSpec(contentType: String): StatusListSpec =
        when {
            contentType.contains(StatusListContentTypes.VC_JWT, ignoreCase = true) ||
                contentType.contains("vc+jwt", ignoreCase = true) -> StatusListSpec.BITSTRING_STATUS_LIST

            else -> StatusListSpec.TOKEN_STATUS_LIST
        }

    private suspend fun fetchStatusList(uri: String): HttpFetchResult {
        var currentUri = validateUrl(uri)
        var redirects = 0
        while (true) {
            val response = client.get(currentUri)
            if (response.status.value in 300..399) {
                if (redirects >= MAX_REDIRECTS) {
                    discard(response)
                    error("status-list redirect limit exceeded")
                }
                val nextUri = resolveRedirect(currentUri, response.headers[HttpHeaders.Location])
                discard(response)
                currentUri = nextUri
                redirects++
                continue
            }
            if (!response.status.isSuccess()) {
                discard(response)
                error("status-list HTTP response was rejected: ${response.status.value}")
            }
            val body = readBounded(response)
            return HttpFetchResult(
                body = body,
                contentType = response.headers[HttpHeaders.ContentType] ?: "",
                cacheControl = response.headers[HttpHeaders.CacheControl],
                sourceUri = currentUri,
            )
        }
    }

    private suspend fun readCached(
        canonicalUri: String,
        key: String,
        args: ResolveStatusArgs,
    ): CachedStatusList? {
        val encoded =
            try {
                cache.getTenant(execution.tenantId, key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return null
        if (encoded.length > MAX_ENVELOPE_CHARS) {
            removeCached(key)
            return null
        }
        val envelope =
            runCatching { cacheJson.decodeFromString(CachedStatusListEnvelope.serializer(), encoded) }.getOrNull()
                ?: run {
                    removeCached(key)
                    return null
                }
        val now = KotlinInstant.fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
        val retrievedAt = envelope.retrievedAtEpochMillis
        val body =
            runCatching { envelope.bodyBase64.decodeFromBase64() }.getOrNull()
                ?: run {
                    removeCached(key)
                    return null
                }
        val retrievedAtIsSane = retrievedAt >= 0L && retrievedAt <= now.toEpochMilliseconds()
        if (!retrievedAtIsSane) {
            removeCached(key)
            return null
        }
        val expectedTtl =
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = envelope.cacheControl,
                localTtlMillis = CACHE_LOCAL_TTL_MS,
                expiresAt = null, // KMP Ktor has no portable HTTP Expires parser; signed claims revalidate on every read.
                signedNextUpdate = null,
                now = KotlinInstant.fromEpochMilliseconds(retrievedAt),
            )
        val validMetadata =
            envelope.schemaVersion == CACHE_SCHEMA_VERSION &&
                runCatching { validateUrl(envelope.canonicalSourceUri) }.getOrNull() == envelope.canonicalSourceUri &&
                envelope.requestedUri == canonicalUri &&
                envelope.expectedSpec == args.expectedSpec?.value &&
                envelope.expectedFormat == args.expectedFormat?.value &&
                envelope.contentType.length <= MAX_CONTENT_TYPE_CHARS &&
                envelope.cacheControl.orEmpty().length <= MAX_CACHE_CONTROL_CHARS &&
                envelope.effectiveTtlMillis == expectedTtl &&
                envelope.noStore == HttpCacheExpiryPolicy.isNoStore(envelope.cacheControl) &&
                envelope.revalidationRequired == HttpCacheExpiryPolicy.requiresRevalidation(envelope.cacheControl) &&
                envelope.effectiveTtlMillis > 0L &&
                body.size.toLong() <= MAX_BODY_BYTES
        if (!validMetadata) {
            removeCached(key)
            return null
        }
        val age = now.toEpochMilliseconds() - retrievedAt
        if (age >= envelope.effectiveTtlMillis || envelope.noStore || envelope.revalidationRequired) {
            removeCached(key)
            return null
        }
        return CachedStatusList(body, envelope.contentType)
    }

    private suspend fun putCached(
        canonicalUri: String,
        key: String,
        args: ResolveStatusArgs,
        response: HttpFetchResult,
    ) {
        val retrievedAt = kotlin.time.Clock.System.now()
        val effectiveTtl =
            HttpCacheExpiryPolicy.ttlMillis(
                cacheControl = response.cacheControl,
                localTtlMillis = CACHE_LOCAL_TTL_MS,
                expiresAt = null, // Expires parsing is intentionally unavailable in common Ktor.
                signedNextUpdate = null,
                now = retrievedAt,
            )
        val noStore = HttpCacheExpiryPolicy.isNoStore(response.cacheControl)
        val revalidationRequired = HttpCacheExpiryPolicy.requiresRevalidation(response.cacheControl)
        if (effectiveTtl <= 0L || noStore || revalidationRequired) return
        val envelope =
            CachedStatusListEnvelope(
                schemaVersion = CACHE_SCHEMA_VERSION,
                canonicalSourceUri = response.sourceUri,
                requestedUri = canonicalUri,
                bodyBase64 = response.body.encodeToBase64(),
                contentType = response.contentType,
                retrievedAtEpochMillis = retrievedAt.toEpochMilliseconds(),
                effectiveTtlMillis = effectiveTtl,
                cacheControl = response.cacheControl,
                noStore = noStore,
                revalidationRequired = revalidationRequired,
                expectedSpec = args.expectedSpec?.value,
                expectedFormat = args.expectedFormat?.value,
            )
        val serialized = cacheJson.encodeToString(CachedStatusListEnvelope.serializer(), envelope)
        if (serialized.length > MAX_ENVELOPE_CHARS) return
        try {
            cache.putTenant(execution.tenantId, key, serialized, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A cache backend failure must not turn a freshly validated artifact into a failure.
        }
    }

    private suspend fun removeCached(key: String) {
        try {
            cache.removeTenant(execution.tenantId, key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Best-effort eviction; the network result remains authoritative.
        }
    }

    private fun cacheKey(
        canonicalUri: String,
        args: ResolveStatusArgs,
    ): String {
        val key = "$canonicalUri|expected-spec=${args.expectedSpec?.value ?: UNSPECIFIED_MARKER}|expected-format=${args.expectedFormat?.value ?: UNSPECIFIED_MARKER}"
        require(key.length <= MAX_CACHE_KEY_CHARS) { "status-list cache key exceeds the configured maximum" }
        return key
    }

    private fun validateUrl(value: String): String {
        require(value.isNotBlank()) { "status-list URI must not be blank" }
        require(value.length <= MAX_URI_CHARS) { "status-list URI exceeds the configured maximum" }
        require('#' !in value) { "status-list URI must not contain a fragment" }
        val parsed = Url(value)
        UrlValidationPolicy.BLOCK_PRIVATE.validate(parsed)
        return canonicalUrl(parsed)
    }

    private fun resolveRedirect(currentValue: String, location: String?): String {
        require(!location.isNullOrBlank()) { "status-list redirect did not provide a Location header" }
        val current = Url(currentValue)
        val targetValue = location.trim().substringBefore('#')
        require(targetValue.isNotEmpty()) { "status-list redirect location was empty" }
        val target =
            when {
                targetValue.startsWith("//") -> "${current.protocol.name}:$targetValue"
                Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(targetValue) -> targetValue
                targetValue.startsWith("?") -> "${origin(current)}${current.encodedPath.ifEmpty { "/" }}$targetValue"
                else -> {
                    val queryStart = targetValue.indexOf('?')
                    val pathPart = if (queryStart < 0) targetValue else targetValue.substring(0, queryStart)
                    val queryPart = if (queryStart < 0) "" else targetValue.substring(queryStart)
                    val baseDirectory = current.encodedPath.ifEmpty { "/" }.substringBeforeLast('/', missingDelimiterValue = "") + "/"
                    "${origin(current)}${normalizePath(if (pathPart.startsWith('/')) pathPart else baseDirectory + pathPart)}$queryPart"
                }
            }
        if (current.protocol.name.equals("https", ignoreCase = true) && target.startsWith("http:", ignoreCase = true)) {
            error("status-list redirect would downgrade HTTPS")
        }
        return validateUrl(target)
    }

    private fun origin(url: Url): String {
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        val defaultPort = if (url.protocol.name.equals("https", ignoreCase = true)) 443 else 80
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        return "${url.protocol.name}://$host$port"
    }

    private fun canonicalUrl(url: Url): String {
        val scheme = url.protocol.name.lowercase()
        val host = if (url.host.contains(':')) "[${url.host.lowercase()}]" else url.host.lowercase()
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        val path = url.encodedPath.ifEmpty { "/" }
        val query = url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        return "$scheme://$host$port$path$query"
    }

    private fun normalizePath(path: String): String {
        val output = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeLast()
                else -> output.addLast(segment)
            }
        }
        val normalized = "/" + output.joinToString("/")
        return if (path.endsWith('/') && normalized != "/") "$normalized/" else normalized
    }

    private suspend fun readBounded(response: HttpResponse): ByteArray {
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        require(declaredLength == null || declaredLength in 0..MAX_BODY_BYTES) {
            "status-list response body exceeds the configured maximum"
        }
        val channel = response.bodyAsChannel()
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        try {
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                require(total <= MAX_BODY_BYTES - count.toLong()) {
                    "status-list response body exceeds the configured maximum"
                }
                total += count
                chunks += buffer.copyOf(count)
            }
        } catch (expected: Exception) {
            try {
                channel.cancel(expected)
            } catch (_: Exception) {
                // Preserve the size/read failure.
            }
            throw expected
        }
        return ByteArray(total.toInt()).also { result ->
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(result, destinationOffset = offset)
                offset += chunk.size
            }
        }
    }

    private suspend fun discard(response: HttpResponse) {
        try {
            response.bodyAsChannel().cancel(null)
        } catch (_: Exception) {
            // The shared client remains reusable for the next request.
        }
    }

    private suspend fun resolveCwt(
        args: ResolveStatusArgs,
        encoded: ByteArray,
    ): IdkResult<ResolvedStatus, IdkError> {
        val cose =
            coseSign1Codec
                .decode(encoded)
                .getOrElse { return Err(StatusListErrors.verificationFailed(args.uri, "malformed status-list CWT: $it")) }
                .value
        val payload = cose.payload?.value
            ?: return Err(StatusListErrors.verificationFailed(args.uri, "status-list CWT payload is detached"))
        val claimsPayload =
            try {
                unwrapCborDataItem(payload)
            } catch (e: Exception) {
                return Err(StatusListErrors.verificationFailed(args.uri, "malformed status-list CWT payload: ${e.message}"))
            }
        val type = cose.protectedHeader.typ?.value
        val hasMdocClaims = looksLikeMdocClaims(claimsPayload)
        return when {
            isMdocIdentifierListType(type) -> resolveMdocCwt(args, cose, claimsPayload)
            isMdocStatusListType(type) && hasMdocClaims -> resolveMdocCwt(args, cose, claimsPayload)
            type == StatusListContentTypes.STATUSLIST_CWT -> resolveGenericCwt(args, cose, claimsPayload)
            else -> Err(StatusListErrors.verificationFailed(args.uri, "status-list CWT has an unsupported protected typ"))
        }
    }

    private fun unwrapCborDataItem(encoded: ByteArray): ByteArray {
        val decoded = Cbor.tryDecode(encoded).getOrThrow()
        return when (decoded) {
            is CborEncodedItem<*> -> decoded.value.taggedItem.value.copyOf()
            is CborTagged<*> -> if (decoded.tagNumber == 24) {
                (decoded.taggedItem as? CborByteString)?.value?.copyOf()
                    ?: error("CBOR data-item tag 24 must contain a byte string")
            } else encoded
            else -> encoded
        }
    }

    private fun looksLikeMdocClaims(encoded: ByteArray): Boolean =
        try {
            val map = Cbor.tryDecode(encoded).getOrThrow() as? CborMap<*, *> ?: return false
            fun item(label: Long): CborItem<*>? =
                map.value.entries.firstOrNull { (key, _) -> key is CborUInt && key.value == label }?.value
            if (item(65530L) != null) return true
            val status = item(65533L) as? CborMap<*, *> ?: return false
            status.value.keys.any { it is CborString }
        } catch (_: Exception) {
            false
        }

    private suspend fun resolveGenericCwt(
        args: ResolveStatusArgs,
        cose: com.sphereon.crypto.core.cose.CoseSign1<*>,
        payload: ByteArray,
    ): IdkResult<ResolvedStatus, IdkError> {
        val signature =
            try {
                coseCryptoService.verify1(cose, keyInfo = null, requireX5Chain = false)
            } catch (e: Exception) {
                return Err(StatusListErrors.verificationFailed(args.uri, "status-list CWT verification failed: ${e.message}"))
            }
        if (signature.error) {
            return Err(StatusListErrors.verificationFailed(args.uri, signature.message ?: "status-list CWT signature invalid"))
        }
        return try {
            val map = Cbor.tryDecode(payload).getOrThrow() as? CborMap<*, *>
                ?: error("status-list CWT claims must be a CBOR map")
            fun item(label: Long): CborItem<*>? =
                map.value.entries.firstOrNull { (key, _) -> key is CborUInt && key.value == label }?.value
            val subject = (item(2L) as? CborString)?.value
                ?: if (item(2L) == null) null else error("status-list CWT sub must be text")
            if (subject != null && subject != args.uri) {
                return Err(StatusListErrors.verificationFailed(args.uri, "status-list CWT subject does not match requested URI"))
            }
            val exp = (item(4L) as? CborUInt)?.value
                ?: if (item(4L) == null) null else error("status-list CWT exp must be unsigned")
            val now = kotlinx.datetime.Clock.System.now().epochSeconds
            validateGenericCwtTemporalClaims(exp, now)
                .getOrElse { return Err(StatusListErrors.verificationFailed(args.uri, it)) }
            val statusList = item(65533L) as? CborMap<*, *>
                ?: error("status-list CWT status_list claim is missing")
            val bits = (statusList.value.entries.firstOrNull { (key, _) -> key is CborUInt && key.value == 0L }?.value as? CborUInt)?.value
                ?: error("status-list CWT bits claim is missing or invalid")
            require(bits in setOf(1L, 2L, 4L, 8L)) { "status-list CWT bits must be one of 1, 2, 4, or 8" }
            val list = statusList.value.entries.firstOrNull { (key, _) -> key is CborUInt && key.value == 1L }?.value as? CborByteString
                ?: error("status-list CWT lst claim is missing or invalid")
            val bitset = StatusListCodec.decode(list.value.encodeToBase64Url(), bits.toInt(), StatusListSpec.TOKEN_STATUS_LIST)
            val value = bitset.get(args.index)
            Ok(
                ResolvedStatus(
                    value = value,
                    purpose = null,
                    valid = value == StatusValues.VALID,
                    statusListUri = args.uri,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Err(StatusListErrors.resolutionFailed(args.uri, e.message ?: "status-list CWT decode failed", e))
        }
    }

    private suspend fun resolveMdocCwt(
        args: ResolveStatusArgs,
        cose: com.sphereon.crypto.core.cose.CoseSign1<*>,
        payload: ByteArray,
    ): IdkResult<ResolvedStatus, IdkError> {
        val type = cose.protectedHeader.typ?.value
        if (!isMdocStatusListType(type) && !isMdocIdentifierListType(type)) {
            return Err(StatusListErrors.verificationFailed(args.uri, "mdoc revocation CWT has no protected status-list typ"))
        }
        validateMdocStatusListProtectedAlgorithm(cose.protectedHeader.alg)
            .getOrElse { return Err(StatusListErrors.verificationFailed(args.uri, it)) }
        if (isMdocIdentifierListType(type) && args.expectedSpec != null) {
            return Err(
                StatusListErrors.verificationFailed(
                    args.uri,
                    "mdoc Identifier List resolution must not declare a generic status-list specification",
                ),
            )
        }
        if (isMdocStatusListType(type) && args.expectedSpec == StatusListSpec.BITSTRING_STATUS_LIST) {
            return Err(
                StatusListErrors.verificationFailed(
                    args.uri,
                    "mdoc Token Status List resolution cannot use the bitstring status-list specification",
                ),
            )
        }
        if (cose.protectedHeader.x5chain == null || cose.protectedHeader.x5chain?.value?.isEmpty() == true) {
            return Err(StatusListErrors.verificationFailed(args.uri, "mdoc revocation CWT requires protected x5chain"))
        }
        val trustedCerts = args.trustedCerts
        if (trustedCerts.isNullOrEmpty()) {
            return Err(
                StatusListErrors.verificationFailed(
                    args.uri,
                    "mdoc revocation CWT verification requires explicit trusted certificates",
                ),
            )
        }
        val chain = cose.protectedHeader.x5chain!!.value.map { it.value }.toTypedArray()
        args.expectedCertificate?.let { expectedCertificate ->
            if (expectedCertificate.isEmpty() || chain.none { it.contentEquals(expectedCertificate) }) {
                return Err(
                    StatusListErrors.verificationFailed(
                        args.uri,
                        "mdoc revocation CWT certificate chain does not contain the MSO certificate pin",
                    ),
                )
            }
        }
        val chainValidation =
            try {
                x509VerifyService.verifyCertificateChain(
                    X509VerificationRequest(
                        chainDER = chain,
                        trustedCerts = trustedCerts,
                        verificationProfile = X509VerificationProfile.ISO_18013_5,
                    ),
                )
            } catch (e: Exception) {
                return Err(StatusListErrors.verificationFailed(args.uri, "mdoc revocation CWT certificate validation failed: ${e.message}"))
            }
        if (chainValidation.error) {
            return Err(
                StatusListErrors.verificationFailed(
                    args.uri,
                    "mdoc revocation CWT certificate chain is not trusted: ${chainValidation.message}",
                ),
            )
        }
        val signature =
            try {
                coseCryptoService.verify1(cose, keyInfo = null, requireX5Chain = true)
            } catch (e: Exception) {
                return Err(StatusListErrors.verificationFailed(args.uri, "mdoc revocation CWT verification failed: ${e.message}"))
            }
        if (signature.error) {
            return Err(StatusListErrors.verificationFailed(args.uri, signature.message ?: "mdoc revocation CWT signature invalid"))
        }
        val claims =
            try {
                MdocRevocationCwtClaimsCodecImpl.decode(payload)
            } catch (e: IllegalArgumentException) {
                return Err(StatusListErrors.verificationFailed(args.uri, "invalid mdoc revocation CWT claims: ${e.message}"))
            }
        if (claims.subject != null && claims.subject != args.uri) {
            return Err(StatusListErrors.verificationFailed(args.uri, "mdoc revocation CWT subject does not match requested URI"))
        }
        val now = kotlinx.datetime.Clock.System.now().epochSeconds
        // RFC 7519-style exp semantics require now to be strictly before exp.
        validateMdocStatusListTemporalClaims(
            expiresAtEpochSeconds = claims.expiresAtEpochSeconds,
            issuedAtEpochSeconds = claims.issuedAtEpochSeconds,
            ttlSeconds = claims.ttlSeconds,
            nowEpochSeconds = now,
        )
            .getOrElse { return Err(StatusListErrors.verificationFailed(args.uri, it)) }
        val issuedAt = claims.issuedAtEpochSeconds
        if (issuedAt != null && issuedAt > now) {
            return Err(StatusListErrors.verificationFailed(args.uri, "mdoc revocation CWT iat is in the future"))
        }
        val value =
            when (val payload = claims.payload) {
                is MdocStatusListPayload.Token -> {
                    if (!isMdocStatusListType(type)) {
                        return Err(
                            StatusListErrors.verificationFailed(
                                args.uri,
                                "Identifier List CWT typ does not match its Token Status List payload",
                            ),
                        )
                    }
                    if (args.expectedSpec != null && args.expectedSpec != StatusListSpec.TOKEN_STATUS_LIST) {
                        return Err(StatusListErrors.verificationFailed(args.uri, "mdoc Token Status List has an incompatible expected specification"))
                    }
                    if (args.identifier != null) {
                        return Err(StatusListErrors.verificationFailed(args.uri, "identifier was supplied for an mdoc Token Status List"))
                    }
                    if (args.index < 0 || args.index / 8 >= payload.list.size) {
                        return Err(StatusListErrors.resolutionFailed(args.uri, "status index ${args.index} is outside the mdoc status list"))
                    }
                    (payload.list[args.index / 8].toInt() ushr (args.index % 8)) and 1
                }

                is MdocStatusListPayload.IdentifierList -> {
                    if (!isMdocIdentifierListType(type)) {
                        return Err(
                            StatusListErrors.verificationFailed(
                                args.uri,
                                "Token Status List CWT typ does not match its Identifier List payload",
                            ),
                        )
                    }
                    val identifier = args.identifier
                        ?: return Err(StatusListErrors.verificationFailed(args.uri, "mdoc Identifier List resolution requires an identifier"))
                    if (args.index != 0) {
                        return Err(StatusListErrors.verificationFailed(args.uri, "mdoc Identifier List resolution does not use an index"))
                    }
                    if (payload.identifiers.any { it.contentEquals(identifier) }) StatusValues.INVALID else StatusValues.VALID
                }
            }
        return Ok(
            ResolvedStatus(
                value = value,
                purpose = null,
                valid = value == StatusValues.VALID,
                statusListUri = args.uri,
            ),
        )
    }

    private fun decodeJwtPayload(jwt: String): JsonObject? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            JwsUtils.decodeBase64UrlToJson(parts[1])
        } catch (_: Exception) {
            null
        }
    }

    private data class HttpFetchResult(
        val body: ByteArray,
        val contentType: String,
        val cacheControl: String?,
        val sourceUri: String,
    )

    private sealed interface FetchOutcome {
        data object Retry : FetchOutcome

        data class Fetched(val response: HttpFetchResult) : FetchOutcome

        data class Artifact(
            val body: ByteArray,
            val contentType: String,
        ) : FetchOutcome

        data class Failure(val result: ResolveResult) : FetchOutcome
    }

    private data class CachedStatusList(
        val body: ByteArray,
        val contentType: String,
    )

    private data class ValidatedFetch(
        val result: ResolveResult,
    )

    private data class FlightKey(
        val tenantId: String,
        val cacheKey: String,
    )

    private data class FlightClaim(
        val deferred: CompletableDeferred<FetchOutcome>,
        val owner: Boolean,
    )

    @Serializable
    private data class CachedStatusListEnvelope(
        val schemaVersion: Int,
        val canonicalSourceUri: String,
        val requestedUri: String,
        val bodyBase64: String,
        val contentType: String,
        val retrievedAtEpochMillis: Long,
        val effectiveTtlMillis: Long,
        val cacheControl: String?,
        val noStore: Boolean,
        val revalidationRequired: Boolean,
        val expectedSpec: String?,
        val expectedFormat: String?,
    )

    private companion object {
        private const val CACHE_NAMESPACE = "statuslist.resolver.http"
        private val CACHE_LOCAL_TTL = 5.minutes
        private const val CACHE_LOCAL_TTL_MS = 5L * 60L * 1000L
        private const val CACHE_SCHEMA_VERSION = 1
        private const val UNSPECIFIED_MARKER = "<unspecified>"
        private const val MAX_ENVELOPE_CHARS = 15 * 1024 * 1024
        private const val MAX_CONTENT_TYPE_CHARS = 512
        private const val MAX_CACHE_CONTROL_CHARS = 4096
        private const val MAX_URI_CHARS = 4096
        private const val MAX_CACHE_KEY_CHARS = 8192
        private const val MAX_IN_FLIGHT_ENTRIES = 64
        private const val HTTP_FETCH_TIMEOUT_MS = 30_000L
        private const val MAX_REDIRECTS = 3
        private const val MAX_BODY_BYTES = 10L * 1024L * 1024L
    }
}
