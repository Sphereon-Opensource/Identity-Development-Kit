/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DefaultProviderKeyReferenceInspectorTest {
    @Test
    fun aliasOnlyLookupReturnsProviderCanonicalKidAndPublicProjection() =
        runTest {
            val lookups = mutableListOf<KeyInfoType<*>>()
            val key = managedKey(alias = "alias", kid = "provider-canonical", x = "x", includePrivate = true)
            val inspector = inspector(mapOf("alias" to key), lookups)

            val result = inspector.inspect("provider-1", "alias")

            assertTrue(result.isOk)
            assertEquals("provider-canonical", result.value.kid)
            assertEquals(1, lookups.size)
            assertEquals("alias", lookups.single().alias)
            assertNull(lookups.single().kid)
            assertNull((result.value.key as Jwk).d)
        }

    @Test
    fun aliasAndKidAreResolvedWithSeparateSingleIdentifierLookups() =
        runTest {
            val lookups = mutableListOf<KeyInfoType<*>>()
            val key = managedKey(alias = "alias", kid = "provider-canonical", x = "x", includePrivate = false)
            val inspector = inspector(mapOf("alias" to key, "provider-canonical" to key), lookups)

            val result = inspector.inspect("provider-1", "alias", "provider-canonical")

            assertTrue(result.isOk)
            assertEquals(2, lookups.size)
            assertEquals("alias", lookups[0].alias)
            assertNull(lookups[0].kid)
            assertNull(lookups[1].alias)
            assertEquals("provider-canonical", lookups[1].kid)
        }

    @Test
    fun aliasAndKidWithDifferentPublicMaterialAreRejected() =
        runTest {
            val lookups = mutableListOf<KeyInfoType<*>>()
            val aliasKey = managedKey(alias = "alias", kid = "provider-canonical", x = "x", includePrivate = false)
            val kidKey = managedKey(alias = "alias", kid = "provider-canonical", x = "different", includePrivate = false)
            val inspector = inspector(mapOf("alias" to aliasKey, "provider-canonical" to kidKey), lookups)

            val result = inspector.inspect("provider-1", "alias", "provider-canonical")

            assertTrue(result.isErr)
            assertEquals("KMS_EXTERNAL_KEY_IDENTITY_MISMATCH", result.error.code)
        }

    @Test
    fun aliasAndKidWithDifferentCanonicalKidsAreRejected() =
        runTest {
            val lookups = mutableListOf<KeyInfoType<*>>()
            val aliasKey = managedKey(alias = "alias", kid = "canonical-alias", x = "x", includePrivate = false)
            val kidKey = managedKey(alias = "alias", kid = "canonical-kid", x = "x", includePrivate = false)
            val inspector = inspector(mapOf("alias" to aliasKey, "requested-kid" to kidKey), lookups)

            val result = inspector.inspect("provider-1", "alias", "requested-kid")

            assertTrue(result.isErr)
            assertEquals("KMS_EXTERNAL_KEY_IDENTITY_MISMATCH", result.error.code)
        }

    @Test
    fun missingComparablePublicMaterialFailsClosed() =
        runTest {
            val lookups = mutableListOf<KeyInfoType<*>>()
            val aliasKey = managedKeyWithJwk(
                alias = "alias",
                kid = "canonical-kid",
                jwk = Jwk(kty = JwaKeyType.oct, k = "symmetric-material"),
            )
            val kidKey = managedKey(alias = "alias", kid = "canonical-kid", x = "x", includePrivate = false)
            val inspector = inspector(mapOf("alias" to aliasKey, "canonical-kid" to kidKey), lookups)

            val result = inspector.inspect("provider-1", "alias", "canonical-kid")

            assertTrue(result.isErr)
            assertEquals("KMS_EXTERNAL_KEY_IDENTITY_MISMATCH", result.error.code)
        }

    @Test
    fun unknownProviderAndKeyReturnNotFoundErrors() =
        runTest {
            val provider = providerProxy(emptyMap(), mutableListOf())
            val missingProviderInspector = DefaultProviderKeyReferenceInspector(registry(mapOf("provider-1" to provider)))
            val missingProvider = missingProviderInspector.inspect("missing", "alias")
            assertTrue(missingProvider.isErr)
            assertEquals("KMS_PROVIDER_NOT_FOUND", missingProvider.error.code)

            val missingKeyInspector = DefaultProviderKeyReferenceInspector(registry(mapOf("provider-1" to provider)))
            val missingKey = missingKeyInspector.inspect("provider-1", "missing")
            assertTrue(missingKey.isErr)
            assertEquals("KMS_EXTERNAL_KEY_NOT_FOUND", missingKey.error.code)
        }

    @Test
    fun kidLookupUnsupportedByProviderAcceptsAliasKeyWithMatchingCanonicalKid() =
        runTest {
            val lookups = mutableListOf<KeyInfoType<*>>()
            val key = managedKey(alias = "alias", kid = "provider-canonical", x = "x", includePrivate = false)
            val inspector = inspector(mapOf("alias" to key), lookups)

            val result = inspector.inspect("provider-1", "alias", "provider-canonical")

            assertTrue(result.isOk)
            assertEquals("provider-canonical", result.value.kid)
            assertEquals(2, lookups.size)
            assertEquals("provider-canonical", lookups[1].kid)
        }

    @Test
    fun kidLookupUnsupportedByProviderRejectsAKidTheAliasKeyDoesNotCarry() =
        runTest {
            val key = managedKey(alias = "alias", kid = "provider-canonical", x = "x", includePrivate = false)
            val inspector = inspector(mapOf("alias" to key), mutableListOf())

            val result = inspector.inspect("provider-1", "alias", "other-kid")

            assertTrue(result.isErr)
            assertEquals("KMS_EXTERNAL_KEY_IDENTITY_MISMATCH", result.error.code)
        }

    private fun inspector(
        keys: Map<String, ManagedKeyInfoType<*>>,
        lookups: MutableList<KeyInfoType<*>>,
    ): DefaultProviderKeyReferenceInspector =
        DefaultProviderKeyReferenceInspector(
            registry(
                mapOf(
                    "provider-1" to providerProxy(keys, lookups),
                ),
            ),
        )

    private fun registry(providers: Map<String, KmsProvider>): KmsProviderRegistry =
        Proxy.newProxyInstance(
            KmsProviderRegistry::class.java.classLoader,
            arrayOf(KmsProviderRegistry::class.java),
        ) { _, method, args ->
            when (method.name) {
                "defaultProviderId" -> providers.keys.first()
                "getProviderIds" -> providers.keys.toTypedArray()
                "getProviderById" -> providers[args?.firstOrNull() as? String] ?: throw IllegalArgumentException("provider not found")
                else -> null
            }
        } as KmsProviderRegistry

    private fun providerProxy(
        keys: Map<String, ManagedKeyInfoType<*>>,
        lookups: MutableList<KeyInfoType<*>>,
    ): KmsProvider =
        Proxy.newProxyInstance(
            KmsProvider::class.java.classLoader,
            arrayOf(KmsProvider::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getId" -> "provider-1"
                "getKey" -> {
                    val keyInfo = args?.firstOrNull() as KeyInfoType<*>
                    lookups += keyInfo
                    keys[keyInfo.alias ?: keyInfo.kid] ?: throw IllegalArgumentException("key not found")
                }
                else -> null
            }
        } as KmsProvider

    private fun managedKey(
        alias: String,
        kid: String,
        x: String,
        includePrivate: Boolean,
    ): ManagedKeyInfo<Jwk> {
        val jwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = x,
                y = "y",
                d = if (includePrivate) "private" else null,
            )
        return ManagedKeyInfo.fromKeyInfo(
            KeyInfo(
                kid = kid,
                key = jwk,
                alias = alias,
                providerId = "provider-1",
            ),
        )
    }

    private fun managedKeyWithJwk(alias: String, kid: String, jwk: Jwk): ManagedKeyInfo<Jwk> =
        ManagedKeyInfo.fromKeyInfo(
            KeyInfo(
                kid = kid,
                key = jwk,
                alias = alias,
                providerId = "provider-1",
            ),
        )
}
