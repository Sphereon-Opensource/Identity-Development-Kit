package com.sphereon.trust.oidfed

import com.sphereon.crypto.resolution.extern.ExternalIdentifierOIDFEntityIdOpts
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure chain-policy tests; cryptographic acceptance/tamper proof belongs to the JOSE integration suite. */
class OidfedChainValidatorTest {
    private val rootKeys = jwks("root-k1")
    private val leafKeys = jwks("leaf-k1")
    private val root = "https://root.example"
    private val leaf = "https://leaf.example"
    private val rootConfigurationJws = "eyJhbGciOiJFZERTQSIsImtpZCI6InJvb3QtaTEifQ.e30.AA"
    private val leafConfigurationJws = "eyJhbGciOiJFZERTQSIsImtpZCI6ImxlYWYtaTEifQ.e30.AQ"
    private val subordinateJws = "eyJhbGciOiJFZERTQSIsImtpZCI6InJvb3QtaTEifQ.e30.Ag"
    private val middleConfigurationJws = "eyJhbGciOiJFZERTQSIsImtpZCI6InJvb3QtaTEifQ.e30.Aw"
    private val reverseSubordinateJws = "eyJhbGciOiJFZERTQSIsImtpZCI6InJvb3QtaTEifQ.e30.BA"

    private fun jwks(kid: String) = buildJsonObject {
        put("keys", buildJsonArray {
            add(buildJsonObject {
                put("kid", kid); put("kty", "OKP"); put("crv", "Ed25519"); put("x", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"); put("alg", "EdDSA"); put("use", "sig")
            })
        })
    }

    private fun claims(issuer: String, subject: String, keys: JsonObject, hints: List<String> = emptyList(), marks: Set<String> = setOf("mark-1"), iat: Long = 100, exp: Long = 200, audience: Set<String> = emptySet()) =
        OidfedAuthenticatedClaims(issuer, subject, iat, exp, keys, hints, audience, marks)

    private class Source(
        private val configurations: Map<String, OidfedEntityConfiguration>,
        private val subordinate: Map<Pair<String, String>, OidfedSubordinateStatement>,
        private val authenticated: Map<String, OidfedAuthenticatedStatement>,
        private val throwOnRead: Boolean = false,
    ) : OidfedEntityStatementSource {
        override suspend fun entityConfiguration(entityIdentifier: String): OidfedEntityConfiguration? { if (throwOnRead) error("source failure"); return configurations[entityIdentifier] }
        override suspend fun subordinateStatement(superior: String, subject: String): OidfedSubordinateStatement? { if (throwOnRead) error("source failure"); return subordinate[superior to subject] }
        fun verifier() = OidfedJwsVerifier verifier@{ compact, trustedJwks ->
            val statement = authenticated[compact] ?: return@verifier null
            val claims = statement.claims
            if (trustedJwks == null && claims.issuer != claims.subject) return@verifier null
            if (trustedJwks != null && trustedJwks != claims.jwks && claims.issuer == claims.subject) return@verifier null
            val keySet = trustedJwks ?: claims.jwks
            val keyIds = (keySet["keys"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("kid")?.toString()?.trim('"') }
            if (statement.header.kid !in keyIds) return@verifier null
            statement
        }
    }

    private fun validator(
        source: Source,
        anchorKeys: JsonObject = rootKeys,
        anchorFrom: Long? = null,
        anchorUntil: Long? = null,
        anchorStatus: OidfedTrustAnchorStatus = OidfedTrustAnchorStatus.ACTIVE,
        policyDepth: Int = 4,
        clock: Long = 150,
    ) = OidfedTrustValidationService(
        source = source,
        anchors = OidfedTrustAnchorSource { listOf(OidfedTrustAnchor(root, anchorKeys, anchorFrom, anchorUntil, anchorStatus)) },
        verifier = source.verifier(),
        clock = OidfedValidationClock { clock },
        policy = OidfedValidationPolicy(maxChainDepth = policyDepth, maxAgeSeconds = 100),
    )

    private fun source(
        leafClaims: OidfedAuthenticatedClaims = claims(leaf, leaf, leafKeys, listOf(root)),
        rootClaims: OidfedAuthenticatedClaims = claims(root, root, rootKeys),
        subordinateClaims: OidfedAuthenticatedClaims = claims(root, leaf, leafKeys),
        leafHeader: OidfedJoseHeader = OidfedJoseHeader("entity-statement+jwt", "EdDSA", "leaf-k1"),
        throwOnRead: Boolean = false,
    ): Source {
        val authenticated = mapOf(
            leafConfigurationJws to OidfedAuthenticatedStatement(leafHeader, leafClaims),
            rootConfigurationJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), rootClaims),
            subordinateJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), subordinateClaims),
        )
        return Source(
            configurations = mapOf(leaf to OidfedEntityConfiguration(leafConfigurationJws), root to OidfedEntityConfiguration(rootConfigurationJws)),
            subordinate = mapOf((root to leaf) to OidfedSubordinateStatement(subordinateJws)),
            authenticated = authenticated,
            throwOnRead = throwOnRead,
        )
    }

    private suspend fun validate(service: OidfedTrustValidationService, depth: Int = 4, marks: String = "") = service.validate(
        TrustValidationRequest(
            identifier = ExternalIdentifierOIDFEntityIdOpts(identifier = leaf),
            context = TrustContext(TrustContext.TYPE_OPENID_FEDERATION, parameters = mapOf("entityIdentifier" to leaf, "maxChainDepth" to depth.toString(), "requiredTrustMarks" to marks)),
        ),
    )

    @Test fun validVerifierAuthenticatedConfigurationAndSubordinateStatementProduceChain() = runTest {
        val result = validate(validator(source()))
        assertTrue(result.trusted)
        assertEquals(TrustStatus.TRUSTED, result.status)
        assertEquals(listOf(leaf, root), result.trustChain?.hops?.map { it.identifier })
    }

    @Test fun persistedRootEntityConfigurationIsAValidZeroHopChain() = runTest {
        val service = validator(source())
        val result = service.validate(TrustValidationRequest(ExternalIdentifierOIDFEntityIdOpts(identifier = root), TrustContext(TrustContext.TYPE_OPENID_FEDERATION, parameters = mapOf("entityIdentifier" to root))))
        assertTrue(result.trusted)
        assertEquals(listOf(root), result.trustChain?.hops?.map { it.identifier })
    }

    @Test fun requestIdentifierMustMatchTheOidfContextEntity() = runTest {
        val service = validator(source())
        val result = service.validate(TrustValidationRequest(ExternalIdentifierOIDFEntityIdOpts(identifier = "https://other.example"), TrustContext(TrustContext.TYPE_OPENID_FEDERATION, parameters = mapOf("entityIdentifier" to leaf))))
        assertFalse(result.trusted)
        assertEquals("oidfed_identifier_context_mismatch", result.details)
    }

    @Test fun futureAndExpiredPersistedAnchorsKeepTypedStatus() = runTest {
        val future = validate(validator(source(), anchorFrom = 151), marks = "")
        assertFalse(future.trusted)
        assertEquals(TrustStatus.NOT_YET_VALID, future.status)
        val expired = validate(validator(source(), anchorUntil = 150), marks = "")
        assertFalse(expired.trusted)
        assertEquals(TrustStatus.EXPIRED, expired.status)
        val invalid = validate(validator(source(), anchorKeys = buildJsonObject { put("bad", true) }), marks = "")
        assertFalse(invalid.trusted)
        assertEquals(TrustStatus.VALIDATION_ERROR, invalid.status)
        val disabled = validate(validator(source(), anchorStatus = OidfedTrustAnchorStatus.DISABLED), marks = "")
        assertFalse(disabled.trusted)
        assertEquals(TrustStatus.VALIDATION_ERROR, disabled.status)
        val revoked = validate(validator(source(), anchorStatus = OidfedTrustAnchorStatus.REVOKED), marks = "")
        assertFalse(revoked.trusted)
        assertEquals(TrustStatus.VALIDATION_ERROR, revoked.status)
    }

    @Test fun payloadAndHeaderClaimsMustBeAuthenticatedAndTyped() = runTest {
        val result = validate(validator(Source(
            mapOf(leaf to OidfedEntityConfiguration(leafConfigurationJws), root to OidfedEntityConfiguration(rootConfigurationJws)),
            mapOf((root to leaf) to OidfedSubordinateStatement(subordinateJws)),
            mapOf(leafConfigurationJws to OidfedAuthenticatedStatement(OidfedJoseHeader("wrong", "EdDSA", "leaf-k1"), claims(leaf, leaf, leafKeys, listOf(root))), rootConfigurationJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), claims(root, root, rootKeys)), subordinateJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), claims(root, leaf, leafKeys))),
        )), marks = "")
        assertFalse(result.trusted)
        assertFalse(validate(validator(source(leafClaims = claims(leaf, "wrong", leafKeys, listOf(root)))), marks = "").trusted)
        assertFalse(validate(validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf(root), audience = setOf("unexpected")))), marks = "").trusted)
    }

    @Test fun noneUnknownAlgorithmAndUnknownKidAreRejected() = runTest {
        assertFalse(validate(validator(source(leafHeader = OidfedJoseHeader("entity-statement+jwt", "none", "leaf-k1"))), marks = "").trusted)
        assertFalse(validate(validator(source(leafHeader = OidfedJoseHeader("entity-statement+jwt", "RS256", "leaf-k1"))), marks = "").trusted)
        assertFalse(validate(validator(source(leafHeader = OidfedJoseHeader("entity-statement+jwt", "EdDSA", "unknown"))), marks = "").trusted)
    }

    @Test fun staleExpiredFutureAndMissingTrustMarkAreRejected() = runTest {
        assertFalse(validate(validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf(root), iat = 0))), marks = "").trusted)
        val expired = validate(validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf(root), exp = 150))), marks = "")
        assertFalse(expired.trusted)
        assertEquals(TrustStatus.EXPIRED, expired.status)
        val future = validate(validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf(root), iat = 151))), marks = "")
        assertFalse(future.trusted)
        assertEquals(TrustStatus.NOT_YET_VALID, future.status)
        val missingTrustMark = validate(
            validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf(root), marks = emptySet()))),
            marks = "required",
        )
        assertFalse(missingTrustMark.trusted)
        val extremeTimes = validate(
            validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf(root), exp = Long.MIN_VALUE, iat = Long.MIN_VALUE))),
        )
        assertFalse(extremeTimes.trusted)
    }

    @Test fun forgedHopWrongAnchorAndRealMultiHopLoopAreRejected() = runTest {
        assertFalse(validate(validator(source(leafClaims = claims(leaf, leaf, leafKeys, listOf("https://attacker.example")))), marks = "").trusted)
        assertFalse(validate(validator(source(), anchorKeys = jwks("wrong-root")), marks = "").trusted)
        val middle = "https://middle.example"
        val loopSource = Source(
            configurations = mapOf(leaf to OidfedEntityConfiguration(leafConfigurationJws), middle to OidfedEntityConfiguration(middleConfigurationJws), root to OidfedEntityConfiguration(rootConfigurationJws)),
            subordinate = mapOf((middle to leaf) to OidfedSubordinateStatement(subordinateJws), (leaf to middle) to OidfedSubordinateStatement(reverseSubordinateJws)),
            authenticated = mapOf(
                leafConfigurationJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "leaf-k1"), claims(leaf, leaf, leafKeys, listOf(middle))),
                middleConfigurationJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), claims(middle, middle, rootKeys, listOf(leaf))),
                rootConfigurationJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), claims(root, root, rootKeys)),
                subordinateJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "root-k1"), claims(middle, leaf, leafKeys)),
                reverseSubordinateJws to OidfedAuthenticatedStatement(OidfedJoseHeader("entity-statement+jwt", "EdDSA", "leaf-k1"), claims(leaf, middle, rootKeys)),
            ),
        )
        assertFalse(validate(validator(loopSource), marks = "").trusted)
    }

    @Test fun requestedDepthCannotBypassPolicyAndExceptionsFailClosed() = runTest {
        assertFalse(validate(validator(source(), policyDepth = 1), depth = 4, marks = "").trusted)
        assertFalse(validate(validator(source(throwOnRead = true)), marks = "").trusted)
    }
}
