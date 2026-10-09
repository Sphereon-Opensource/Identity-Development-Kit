/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.jsonSerializer
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.DefaultOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildSignedAuthorizationServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildSignedAuthorizationServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.ObserveHostedServerMetadataArgs
import com.sphereon.oauth2.server.authorization.impl.config.ActiveSigningKeySnapshotCache
import com.sphereon.oauth2.server.authorization.impl.config.DefaultAsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.StubBuildSignedAuthorizationServerMetadataCommand
import com.sphereon.oauth2.server.authorization.impl.testutil.TenantOverrideSessionExecution
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.AsSigningRequirement
import com.sphereon.oauth2.server.authorization.signing.AsSigningSelection
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class HostedServerMetadataObservationTest {
    private val ctx = OAuth2ServerTestContext("hosted-metadata-observation", this)
    private val tenant = "observation-tenant"
    private val execution = TenantOverrideSessionExecution(ctx.execution, tenant)
    private val ambientA = OAuth2ServerInstanceConfig(
        issuer = "https://ambient-a.example/oidc",
        tokenFormat = TokenFormat.JWT,
        oidc = FeaturePolicy.SUPPORTED,
        idTokenSigningAlgValuesSupported = setOf("ES256"),
    )
    private val selectedB = OAuth2ServerInstanceConfig(
        issuer = "https://selected-b.example/oauth",
        tokenFormat = TokenFormat.OPAQUE,
        oidc = FeaturePolicy.DISABLED,
        signedMetadata = FeaturePolicy.REQUIRED,
        dpop = FeaturePolicy.DISABLED,
        jar = FeaturePolicy.DISABLED,
        grantTypesEnabled = setOf("client_credentials"),
        responseTypesSupported = setOf("code"),
    )

    private class CountingProvider(val root: OAuth2ServersConfig) : OAuth2ServersConfigProvider by TestOAuth2ServersConfigProvider(root) {
        var reads = 0
        override fun getConfig(): OAuth2ServersConfig {
            reads++
            return root
        }
    }

    private class CountingResolver(
        private val delegate: AsServerSigningIdentifierResolver,
        private val current: DefaultOAuth2ServerInstanceIdProvider,
        private val failure: Throwable? = null,
        private val onSelect: suspend () -> Unit = {},
    ) : AsServerSigningIdentifierResolver {
        var calls = 0
        var observedCurrent: String? = null
        override suspend fun selectSigning(
            captured: CapturedAsServerConfig,
            requirement: AsSigningRequirement,
            requestedAlgorithm: String?,
        ): AsSigningSelection {
            calls++
            observedCurrent = current.currentAsInstanceId()
            onSelect()
            failure?.let { throw it }
            return delegate.selectSigning(captured, requirement, requestedAlgorithm)
        }
    }

    private class CountingSigner(
        private val delegate: BuildSignedAuthorizationServerMetadataCommand,
    ) : BuildSignedAuthorizationServerMetadataCommand by delegate {
        var calls = 0
        override suspend fun execute(args: BuildSignedAuthorizationServerMetadataArgs): IdkResult<JwtCompactResult, IdkError> {
            calls++
            return delegate.execute(args)
        }
    }

    private class Fixture(
        val command: ObserveHostedServerMetadataCommandImpl,
        val builder: BuildServerMetadataCommandImpl,
        val provider: CountingProvider,
        val current: DefaultOAuth2ServerInstanceIdProvider,
        val resolver: CountingResolver,
        val signer: CountingSigner,
        val store: InMemorySigningKeyStore,
        val kms: CountingKmsRegistry,
    )

    private class CountingKmsRegistry(
        private val delegate: KmsProviderRegistry,
        private val capabilityFailure: Throwable? = null,
        private val enumerationFailure: Throwable? = null,
        private val lookupFailure: Throwable? = null,
    ) : KmsProviderRegistry by delegate {
        var enumerations = 0
        val defaultProviderId: String get() = delegate.defaultProviderId()
        val lookups = mutableMapOf<String, Int>()
        val capabilityReads = mutableMapOf<String, Int>()
        override fun getProviderIds(): Array<String> {
            enumerations++
            enumerationFailure?.let { throw it }
            return if (capabilityFailure == null) delegate.getProviderIds() else arrayOf(delegate.defaultProviderId(), "unavailable-provider")
        }
        override suspend fun getProviderById(id: String): KmsProvider {
            lookups[id] = (lookups[id] ?: 0) + 1
            if (id == "unavailable-provider") lookupFailure?.let { throw it }
            val actual = delegate.getProviderById(if (id == "unavailable-provider") delegate.defaultProviderId() else id)
            return object : KmsProvider by actual {
                override fun getCapabilities(): KmsProviderCapabilities {
                    capabilityReads[id] = (capabilityReads[id] ?: 0) + 1
                    if (id == "unavailable-provider") throw requireNotNull(capabilityFailure)
                    return actual.getCapabilities()
                }
            }
        }
    }

    private fun fixture(
        selected: OAuth2ServerInstanceConfig = selectedB,
        previous: String? = "A",
        explicitlyConfigured: Boolean = true,
        failure: Throwable? = null,
        kmsFailure: Throwable? = null,
        kmsEnumerationFailure: Throwable? = null,
        kmsLookupFailure: Throwable? = null,
        kmsDelegate: KmsProviderRegistry = ctx.kmsProviderRegistry,
        executionOverride: SessionExecution = execution,
        onSelect: suspend () -> Unit = {},
    ): Fixture {
        val root = OAuth2ServersConfig(
            defaultServer = "A",
            servers = mapOf("A" to ambientA, "B" to selected),
            explicitlyConfigured = explicitlyConfigured,
        )
        val provider = CountingProvider(root)
        val current = DefaultOAuth2ServerInstanceIdProvider()
        previous?.let(current::setCurrentAsInstanceId)
        val store = InMemorySigningKeyStore()
        val realResolver = DefaultAsServerSigningIdentifierResolver(executionOverride, store, ActiveSigningKeySnapshotCache())
        val resolver = CountingResolver(realResolver, current, failure, onSelect)
        val signer = CountingSigner(StubBuildSignedAuthorizationServerMetadataCommand(executionOverride))
        val kms = CountingKmsRegistry(kmsDelegate, kmsFailure, kmsEnumerationFailure, kmsLookupFailure)
        val builder = BuildServerMetadataCommandImpl(
            execution = executionOverride,
            configProvider = provider,
            asInstanceIdProvider = current,
            signingIdentifierResolver = resolver,
            grantHandlers = emptyMap(),
            kmsProviderRegistry = kms,
            buildSignedMetadata = signer,
        )
        val command = ObserveHostedServerMetadataCommandImpl(executionOverride, provider, current, builder)
        return Fixture(command, builder, provider, current, resolver, signer, store, kms)
    }

    private val requestedB = ObserveHostedServerMetadataArgs("B", "https://selected-b.example/oauth")

    @Test
    fun selectedBMatchesExistingUnsignedAssemblyWithoutAmbientAOrEmbeddedSigner() = runTest {
        val f = fixture()
        val expected = f.builder.execute(BuildServerMetadataArgs(serverId = "B", includeSignedMetadata = false))
        assertTrue(expected.isOk, "real existing builder must establish the unsigned metadata control")
        f.provider.reads = 0
        f.resolver.calls = 0
        val observed = f.command.execute(requestedB)
        assertTrue(observed.isOk)
        assertEquals(1, f.provider.reads)
        assertEquals(1, f.resolver.calls)
        assertEquals("B", f.resolver.observedCurrent)
        assertEquals("A", f.current.currentAsInstanceId())
        assertEquals("B", observed.value.serverKey)
        assertEquals("https://selected-b.example/oauth", observed.value.effectiveIssuer)
        assertEquals(TokenFormat.OPAQUE, observed.value.tokenFormat)
        assertFalse(observed.value.oidcEnabled)
        assertEquals(expected.value, observed.value.metadata)
        assertNull(observed.value.metadata.signedMetadata)
        assertNull(observed.value.signingDescriptor)
        assertNull(observed.value.signingDescriptorFingerprint)
        assertEquals(0, f.signer.calls)
        assertEquals(64, observed.value.metadataFingerprint.length)
        assertEquals(0, f.kms.enumerations, "no derived capability field needs a KMS read")
        assertEquals(64, observed.value.contextFingerprint.length)
        val publicContext = JsonObject(mapOf(
            "serverKey" to JsonPrimitive("B"),
            "effectiveIssuer" to JsonPrimitive("https://selected-b.example/oauth"),
            "tokenFormat" to JsonPrimitive("OPAQUE"),
            "oidcEnabled" to JsonPrimitive(false),
        ))
        assertEquals(hash(Jcs.canonicalize(publicContext), DigestAlg.SHA256).encodeToHex(), observed.value.contextFingerprint)
        assertEquals(
            hash(Jcs.canonicalize(jsonSerializer.encodeToJsonElement(observed.value.metadata)), DigestAlg.SHA256).encodeToHex(),
            observed.value.metadataFingerprint,
        )
        val wire = Json.encodeToString(observed.value)
        assertFalse(wire.contains("signed_metadata"))
        assertFalse(wire.contains("ManagedOpts"))
    }

    @Test
    fun configuredNullIssuerUsesExactTrustedIssuerAndRestoresPriorNull() = runTest {
        val f = fixture(selected = selectedB.copy(issuer = null), previous = null)
        val exact = "https://Selected-B.example:8443/oauth/"
        val observed = f.command.execute(ObserveHostedServerMetadataArgs("B", exact))
        assertTrue(observed.isOk)
        assertEquals(exact, observed.value.effectiveIssuer)
        assertEquals(exact, observed.value.metadata.issuer)
        assertNull(f.current.currentAsInstanceId())
        assertEquals(1, f.provider.reads)
        assertEquals(0, f.signer.calls)
        val changed = f.command.execute(ObserveHostedServerMetadataArgs("B", "https://Selected-B.example:8443/other"))
        assertTrue(changed.isOk)
        assertNotEquals(observed.value.contextFingerprint, changed.value.contextFingerprint)
    }

    @Test
    fun missingExternalAndMismatchedIssuerRejectBeforeSigningOrFallback() = runTest {
        val cases = listOf(
            ObserveHostedServerMetadataArgs("missing", requestedB.effectiveIssuer),
            ObserveHostedServerMetadataArgs("B", "https://other.example/oauth"),
            ObserveHostedServerMetadataArgs("B", ""),
            ObserveHostedServerMetadataArgs("", requestedB.effectiveIssuer),
        )
        for (input in cases) {
            val f = fixture()
            val result = f.command.execute(input)
            assertTrue(result.isErr, "invalid exact selection must fail: ${input.serverKey}")
            assertEquals("invalid_request", result.error.code)
            assertEquals(0, f.resolver.calls)
            assertEquals(0, f.signer.calls)
            assertEquals("A", f.current.currentAsInstanceId())
        }
        val external = fixture(selected = selectedB.copy(mode = AuthorizationServerMode.EXTERNAL))
        val result = external.command.execute(requestedB)
        assertTrue(result.isErr)
        assertEquals("invalid_request", result.error.code)
        assertEquals(0, external.resolver.calls)
        assertEquals("A", external.current.currentAsInstanceId())
    }

    @Test
    fun synthesizedUnconfiguredRootCannotSupplyHostedObservation() = runTest {
        val f = fixture(explicitlyConfigured = false)
        val result = f.command.execute(requestedB)
        assertTrue(result.isErr)
        assertEquals("invalid_request", result.error.code)
        assertEquals(0, f.resolver.calls)
        assertEquals("A", f.current.currentAsInstanceId())
    }

    @Test
    fun missingTrustedIssuerIsNotFilledFromNullConfiguredIssuer() = runTest {
        for (prior in listOf<String?>("A", null)) {
            val f = fixture(selected = selectedB.copy(issuer = null), previous = prior)
            val result = f.command.execute(ObserveHostedServerMetadataArgs("B", ""))
            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
            assertEquals(0, f.resolver.calls)
            assertEquals(prior, f.current.currentAsInstanceId())
        }
    }

    @Test
    fun requiredIndependentJwtSignerCannotBeSuppressedByUnsignedObservation() = runTest {
        val f = fixture(selected = selectedB.copy(tokenFormat = TokenFormat.JWT))
        val result = f.command.execute(requestedB)
        assertTrue(result.isErr)
        assertEquals(1, f.resolver.calls)
        assertEquals(0, f.signer.calls)
        assertEquals("A", f.current.currentAsInstanceId())
    }

    @Test
    fun ordinarySelectionFailureRestoresPreviousInstanceAndReturnsControlledError() = runTest {
        for (prior in listOf<String?>("A", null)) {
            val f = fixture(selected = selectedB.copy(tokenFormat = TokenFormat.JWT), previous = prior, failure = IllegalStateException("selection sentinel"))
            val result = f.command.execute(requestedB)
            assertTrue(result.isErr)
            assertEquals(1, f.resolver.calls)
            assertEquals("B", f.resolver.observedCurrent)
            assertEquals(prior, f.current.currentAsInstanceId())
        }
    }

    @Test
    fun cancellationEscapesAndRestoresPriorNull() = runTest {
        for (prior in listOf<String?>(null, "A")) {
            val f = fixture(selected = selectedB.copy(tokenFormat = TokenFormat.JWT), previous = prior, failure = CancellationException("selection cancelled"))
            val thrown = assertFailsWith<CancellationException> { f.command.execute(requestedB) }
            assertTrue(thrown.message?.contains("selection cancelled") == true)
            assertEquals(1, f.resolver.calls)
            assertEquals(prior, f.current.currentAsInstanceId())
        }
    }

    @Test
    fun selectedPublicDescriptorIsObservedWithoutKeyMaterialOrSigning() = runTest {
        val f = fixture(selected = selectedB.copy(tokenFormat = TokenFormat.JWT))
        val now = Clock.System.now()
        val registered = f.store.register(OAuth2SigningKey(
            tenantId = tenant,
            keyInfo = KeyInfo<KeyType>(alias = "selected-key", kid = "selected-kid", signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256),
            state = OAuth2SigningKeyState.ACTIVE,
            priority = 1,
            createdAt = now,
            notBefore = now,
        ))
        assertTrue(registered.isOk)
        val observed = f.command.execute(requestedB)
        assertTrue(observed.isOk)
        assertEquals("selected-kid", observed.value.signingDescriptor?.kid)
        assertEquals("ES256", observed.value.signingDescriptor?.algorithm)
        assertEquals(64, observed.value.signingDescriptorFingerprint?.length)
        assertEquals(
            hash(Jcs.canonicalize(jsonSerializer.encodeToJsonElement(requireNotNull(observed.value.signingDescriptor))), DigestAlg.SHA256).encodeToHex(),
            observed.value.signingDescriptorFingerprint,
        )
        assertEquals(0, f.signer.calls)
        val wire = Json.encodeToString(observed.value)
        assertFalse(wire.contains("selected-key"))
        assertFalse(wire.contains("keyInfo"))
    }

    @Test
    fun multipleDerivedCapabilityFieldsReuseOneCompleteProviderCapture() = runTest {
        val f = fixture(selected = selectedB.copy(dpop = FeaturePolicy.SUPPORTED, jar = FeaturePolicy.SUPPORTED))
        val result = f.command.execute(requestedB)
        assertTrue(result.isOk)
        assertEquals(1, f.kms.enumerations)
        assertTrue(f.kms.lookups.isNotEmpty())
        assertEquals(f.kms.lookups.keys, f.kms.capabilityReads.keys)
        assertTrue(f.kms.capabilityReads.isNotEmpty())
        assertTrue(f.kms.lookups.values.all { it == 1 }, "each KMS provider must be inspected once")
        assertTrue(f.kms.capabilityReads.values.all { it == 1 }, "each provider capability must be captured once")
        assertEquals(result.value.metadata.dpopSigningAlgValuesSupported, result.value.metadata.requestObjectSigningAlgValuesSupported)
        assertTrue(result.value.metadata.dpopSigningAlgValuesSupported.orEmpty().isNotEmpty())
        assertTrue(result.value.metadata.requestObjectSigningAlgValuesSupported.orEmpty().isNotEmpty())
        assertEquals(0, f.signer.calls)
    }

    @Test
    fun failedSecondCapabilityProviderCannotProducePartialObservation() = runTest {
        val f = fixture(selected = selectedB.copy(dpop = FeaturePolicy.SUPPORTED), kmsFailure = IllegalStateException("provider failed"))
        val result = f.command.execute(requestedB)
        assertTrue(result.isErr)
        assertEquals(1, f.kms.enumerations)
        assertEquals(1, f.kms.lookups["unavailable-provider"])
        assertEquals(1, f.kms.capabilityReads["unavailable-provider"])
        assertEquals(1, f.kms.capabilityReads[f.kms.defaultProviderId])
        assertEquals("A", f.current.currentAsInstanceId())
    }

    @Test
    fun capabilityCancellationEscapesAndRestoresPreviousInstance() = runTest {
        val f = fixture(selected = selectedB.copy(dpop = FeaturePolicy.SUPPORTED), kmsFailure = CancellationException("capability cancelled"))
        val thrown = assertFailsWith<CancellationException> { f.command.execute(requestedB) }
        assertTrue(thrown.message?.contains("capability cancelled") == true)
        assertEquals(1, f.kms.lookups["unavailable-provider"])
        assertEquals(1, f.kms.capabilityReads["unavailable-provider"])
        assertEquals("A", f.current.currentAsInstanceId())
    }

    @Test
    fun providerEnumerationFailureIsControlledBeforeAnyCapabilityRead() = runTest {
        val f = fixture(selected = selectedB.copy(dpop = FeaturePolicy.SUPPORTED), kmsEnumerationFailure = IllegalStateException("enumeration failed"))
        val result = f.command.execute(requestedB)
        assertTrue(result.isErr)
        assertEquals(1, f.kms.enumerations)
        assertTrue(f.kms.lookups.isEmpty())
        assertEquals("A", f.current.currentAsInstanceId())
    }

    @Test
    fun providerEnumerationCancellationEscapesAndRestoresPriorNull() = runTest {
        val f = fixture(selected = selectedB.copy(dpop = FeaturePolicy.SUPPORTED), previous = null, kmsEnumerationFailure = CancellationException("enumeration cancelled"))
        val thrown = assertFailsWith<CancellationException> { f.command.execute(requestedB) }
        assertTrue(thrown.message?.contains("enumeration cancelled") == true)
        assertEquals(1, f.kms.enumerations)
        assertTrue(f.kms.lookups.isEmpty())
        assertNull(f.current.currentAsInstanceId())
    }

    @Test
    fun secondProviderLookupFailureCannotProducePartialObservation() = runTest {
        val f = fixture(
            selected = selectedB.copy(dpop = FeaturePolicy.SUPPORTED),
            kmsFailure = IllegalStateException("capability sentinel unused"),
            kmsLookupFailure = IllegalStateException("lookup failed"),
        )
        val result = f.command.execute(requestedB)
        assertTrue(result.isErr)
        assertEquals(1, f.kms.lookups["unavailable-provider"])
        assertNull(f.kms.capabilityReads["unavailable-provider"])
        assertEquals(1, f.kms.capabilityReads[f.kms.defaultProviderId])
        assertEquals("A", f.current.currentAsInstanceId())
    }

    @Test
    fun overlappingObservationsInDistinctSessionsRestoreTheirOwnSelections() = runTest {
        val otherContext = OAuth2ServerTestContext("hosted-metadata-observation-other", this@HostedServerMetadataObservationTest)
        val otherExecution = TenantOverrideSessionExecution(otherContext.execution, tenant)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = fixture(previous = "A", onSelect = { entered.complete(Unit); release.await() })
        val second = fixture(previous = null, executionOverride = otherExecution, kmsDelegate = otherContext.kmsProviderRegistry)
        val pendingFirst = async { first.command.execute(requestedB) }
        var pendingSecond: Deferred<*>? = null
        var primaryFailure: Throwable? = null
        try {
            val enteredBeforeCompletion = withTimeout(5_000) {
                select<Boolean> {
                    entered.onAwait { true }
                    pendingFirst.onAwait { false }
                }
            }
            assertTrue(enteredBeforeCompletion, "first observation returned before entering the selection callback")
            assertTrue(pendingFirst.isActive, "first observation must remain suspended at the selected-B callback")
            val secondTask = async { second.command.execute(requestedB) }
            pendingSecond = secondTask
            assertTrue(withTimeout(5_000) { secondTask.await() }.isOk)
            assertNull(second.current.currentAsInstanceId())
            release.complete(Unit)
            assertTrue(withTimeout(5_000) { pendingFirst.await() }.isOk)
            assertEquals("A", first.current.currentAsInstanceId())
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            release.complete(Unit)
            try {
                withContext(NonCancellable) {
                    withTimeout(5_000) {
                        pendingSecond?.cancel()
                        pendingFirst.cancel()
                        pendingSecond?.join()
                        pendingFirst.join()
                    }
                }
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure == null) throw cleanupFailure
            }
        }
    }
}
