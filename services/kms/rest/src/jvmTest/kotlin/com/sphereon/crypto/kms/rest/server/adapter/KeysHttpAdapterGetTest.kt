/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.dispatch.HttpAdapterRouteMatch
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.server.service.KmsRestService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeysHttpAdapterGetTest {
    @Test
    fun liveGetRouteReturnsPersistedLifecycleAndSanitizedProviderMetadata() =
        runTest {
            val adapter = KeysHttpAdapter(keyService(), unusedRegisterCommand())

            val response = adapter.handleResolvedRequest(request(), route())

            assertEquals(200, response.statusCode)
            val body = response.body ?: error("GET key response body is missing")
            val decoded = Json.decodeFromString<GetKeyResponse>(body).keyInfo
            assertEquals("external", decoded.origin?.value)
            assertEquals("externally_managed", decoded.controlMode?.value)
            assertEquals(listOf("leaf", "issuer"), decoded.key.x5c?.toList())
            assertEquals("sha1-thumbprint", decoded.key.x5t)
            assertEquals("sha256-thumbprint", decoded.key.x5tHashS256)
            assertNull(decoded.key.d)
            assertNull(decoded.key.k)
            assertNull(decoded.key.x5u)
            assertFalse(body.contains("private-d"))
            assertFalse(body.contains("provider.invalid"))
        }

    @Test
    fun liveGetRouteFailsWhenPersistedLifecycleLookupFails() =
        runTest {
            val service = keyService(metadataFailure = IllegalStateException("reference database unavailable"))
            val adapter = KeysHttpAdapter(service, unusedRegisterCommand())

            val response = adapter.handleResolvedRequest(request(), route())

            assertEquals(500, response.statusCode)
            val body = response.body ?: error("GET key failure response body is missing")
            assertTrue(body.contains("Registered key reference lookup failed"))
            assertFalse(body.contains("reference database unavailable"))
        }

    private fun request() = GenericHttpRequest(method = "GET", path = "/keys/provider-alias")

    private fun route() =
        HttpAdapterRouteMatch(
            adapterId = KeysHttpAdapter.ID,
            method = "GET",
            originalPath = "/keys/provider-alias",
            normalizedPath = "/keys/provider-alias",
            matchedPathPattern = "/keys/{aliasOrKid}",
            handlerCommandId = "kms.keys.get",
            tenantIdFromPath = null,
        )

    private fun keyService(metadataFailure: Exception? = null): KmsRestService =
        object : KmsRestService {
            override suspend fun getKey(aliasOrKid: String, providerId: String?): ManagedKeyInfoType<*> =
                ManagedKeyInfo.fromKeyInfo(
                    KeyInfo(
                        key =
                            Jwk(
                                kty = JwaKeyType.EC,
                                kid = "provider-kid",
                                crv = JwaCurve.P_256,
                                x = "public-x",
                                y = "public-y",
                                d = "private-d",
                                x5c = arrayOf("leaf", "issuer"),
                                x5t = "sha1-thumbprint",
                                x5u = "https://provider.invalid/certificate",
                                x5t_S256 = "sha256-thumbprint",
                            ),
                        alias = "provider-alias",
                        providerId = "provider-1",
                        x5c = arrayOf("leaf", "issuer"),
                    ),
                )

            override suspend fun getKeyReference(aliasOrKid: String, providerId: String?): ManagedKeyReference? {
                metadataFailure?.let { throw it }
                return ManagedKeyReference(
                    alias = "provider-alias",
                    kid = "provider-kid",
                    providerId = "provider-1",
                    origin = Origin.EXTERNAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                )
            }

            override suspend fun listKeys(providerId: String?): Array<ManagedKeyReference> = unexpected("listKeys")

            override suspend fun storeKey(
                keyInfo: ResolvedKeyInfoType<*>,
                certChain: Array<String>?,
            ): ManagedKeyInfoType<*> = unexpected("storeKey")

            override suspend fun generateKey(
                alias: String?,
                use: JwkUse?,
                keyOperations: Array<KeyOperations>?,
                alg: SignatureAlgorithm?,
                providerId: String?,
            ): ManagedKeyPair = unexpected("generateKey")

            override suspend fun deleteKey(aliasOrKid: String, providerId: String?): Boolean = unexpected("deleteKey")
        }

    private fun unusedRegisterCommand(): RegisterKeyReferenceServiceCommand =
        Proxy.newProxyInstance(
            RegisterKeyReferenceServiceCommand::class.java.classLoader,
            arrayOf(RegisterKeyReferenceServiceCommand::class.java),
        ) { _, method, _ -> error("RegisterKeyReferenceServiceCommand.${method.name} must not be called") }
            as RegisterKeyReferenceServiceCommand

    private fun unexpected(method: String): Nothing = error("KmsRestService.$method must not be called")
}
