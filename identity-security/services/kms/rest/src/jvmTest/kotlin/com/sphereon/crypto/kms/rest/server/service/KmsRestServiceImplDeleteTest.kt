/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.ManagedKeyStoreService
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.key.persistence.KeyReferenceResolutionException
import com.sphereon.crypto.key.persistence.KeyReferenceStoreErrorCodes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KmsRestServiceImplDeleteTest {
    @Test
    fun getKeyReferenceReturnsPersistedLifecycleMetadataByKid() =
        runTest {
            val reference =
                ManagedKeyReference(
                    alias = "provider-alias",
                    kid = "provider-kid",
                    providerId = "provider-1",
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            val service = KmsRestServiceImpl(unusedKms(), registeredReferenceStore(reference))

            val result = service.getKeyReference("provider-kid", "provider-1")

            assertEquals(reference, result)
        }

    @Test
    fun getKeyReferenceDoesNotFabricateLifecycleMetadataForProviderInventory() =
        runTest {
            val service = KmsRestServiceImpl(unusedKms(), registeredReferenceStore(null))

            assertNull(service.getKeyReference("provider-alias", "provider-1"))
        }

    @Test
    fun deletePreservesCodedKeyReferenceResolutionFailureForHttpMapping() =
        runTest {
            assertDeletePreservesCodedFailure(
                code = KeyReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE,
                aliasOrKid = "shared-alias",
                providerId = "provider-1",
            )
        }

    @Test
    fun providerScopedDeletePreservesUnavailableHistoryFailureWithoutProviderAccess() =
        runTest {
            assertDeletePreservesCodedFailure(
                code = KeyReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
                aliasOrKid = "possibly-indexed",
                providerId = "provider-1",
            )
        }

    private suspend fun assertDeletePreservesCodedFailure(
        code: String,
        aliasOrKid: String,
        providerId: String?,
    ) {
        var keyManagerDeleteCalls = 0
        val expected =
            KeyReferenceResolutionException(
                code = code,
                message = "coded key reference failure",
            )
        val kms =
            Proxy.newProxyInstance(
                KeyManagerService::class.java.classLoader,
                arrayOf(KeyManagerService::class.java),
            ) { _, method, _ ->
                if (method.name == "deleteKey") {
                    keyManagerDeleteCalls++
                }
                error("KeyManagerService.${method.name} must not be called")
            } as KeyManagerService
        val managedKeyStore =
            object : ManagedKeyStoreService {
                override val settings: KeyProviderSettings? = null

                override suspend fun listKeys(): Array<ManagedKeyReference> = unexpectedCall("listKeys")

                override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> =
                    unexpectedCall("getKey")

                override suspend fun storeKey(
                    keyInfo: ResolvedKeyInfoType<*>,
                    providerId: String,
                    alias: String,
                    certChain: Array<Certificate>?,
                ): ManagedKeyInfoType<*> = unexpectedCall("storeKey")

                override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
                    check(keyInfo.alias == aliasOrKid)
                    check(keyInfo.providerId == providerId)
                    throw expected
                }

                override fun keyVisibility(): KeyVisibility = unexpectedCall("keyVisibility")
            }
        val service = KmsRestServiceImpl(kms, managedKeyStore)

        val actual =
            assertFailsWith<KeyReferenceResolutionException> {
                service.deleteKey(aliasOrKid, providerId)
            }

        assertEquals(code, actual.code)
        assertEquals(0, keyManagerDeleteCalls)
    }

    private fun unexpectedCall(method: String): Nothing = error("ManagedKeyStoreService.$method must not be called")

    private fun unusedKms(): KeyManagerService =
        Proxy.newProxyInstance(
            KeyManagerService::class.java.classLoader,
            arrayOf(KeyManagerService::class.java),
        ) { _, method, _ -> error("KeyManagerService.${method.name} must not be called") } as KeyManagerService

    private fun registeredReferenceStore(reference: ManagedKeyReference?): ManagedKeyStoreService =
        object : ManagedKeyStoreService {
            override val settings: KeyProviderSettings? = null

            override suspend fun findRegisteredKeyReference(
                aliasOrKid: String,
                providerId: String?,
            ): ManagedKeyReference? = reference

            override suspend fun listKeys(): Array<ManagedKeyReference> = unexpectedCall("listKeys")

            override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> = unexpectedCall("getKey")

            override suspend fun storeKey(
                keyInfo: ResolvedKeyInfoType<*>,
                providerId: String,
                alias: String,
                certChain: Array<Certificate>?,
            ): ManagedKeyInfoType<*> = unexpectedCall("storeKey")

            override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean = unexpectedCall("deleteKey")

            override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC
        }
}
