/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.data.store.kv.android.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStoreListing
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration

@RunWith(AndroidJUnit4::class)
class AndroidProtectedPreferencesKvStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storeId = "android-protected-device-test"
    private val alias = "com.sphereon.wallet.test.$storeId"
    private val preferencesName = "sphereon.wallet.kv.${sha256(storeId)}"
    private val config =
        AndroidProtectedPreferencesKvStoreConfig(
            id = storeId,
            scopeBinding = KvStoreScopeBinding.APP,
            keyAlias = alias,
        )
    private val namespace =
        KvNamespace(
            name = "test-namespace",
            codec = KotlinxSerializationJsonKvCodec(Json, String.serializer()),
        )

    @After
    fun cleanUp() {
        context
            .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    @Test
    fun encryptedEntriesSurviveFactoryRecreationAndRemainListable() =
        runTest {
            cleanUp()
            val first = AndroidProtectedPreferencesKvStoreFactory(context).create(config, null)
            first.put(namespace, "clear-key", "secret-value", Duration.INFINITE).getOrThrow()

            val recreated = AndroidProtectedPreferencesKvStoreFactory(context).create(config, null)
            assertEquals("secret-value", recreated.get(namespace, "clear-key").getOrThrow())
            assertEquals(listOf("clear-key"), (recreated as KvStoreListing).listKeys(namespace).getOrThrow())

            val persisted = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).all.toString()
            assertFalse(persisted.contains("clear-key"))
            assertFalse(persisted.contains("secret-value"))
            assertFalse(persisted.contains("test-namespace"))
        }

    @Test
    fun versionChainSurvivesRecreationDeletesSeparatelyAndRecreatesAfterHeadExpiry() =
        runTest {
            cleanUp()
            val first = assertIs<KvStoreVersioning>(AndroidProtectedPreferencesKvStoreFactory(context).create(config, null))
            first.put(namespace, "shared", "mutable", Duration.INFINITE).getOrThrow()
            val root =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    first.append(namespace, "shared", null, "root", Duration.INFINITE).getOrThrow(),
                ).entry
            val expired =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    first.append(namespace, "shared", root.versionId, "expired", Duration.ZERO).getOrThrow(),
                ).entry

            val recreated = assertIs<KvStoreVersioning>(AndroidProtectedPreferencesKvStoreFactory(context).create(config, null))
            assertNull(recreated.getHead(namespace, "shared").getOrThrow())
            assertNull(recreated.getVersion(namespace, "shared", root.versionId).getOrThrow())
            val fresh =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    recreated.append(namespace, "shared", null, "fresh", Duration.INFINITE).getOrThrow(),
                ).entry
            assertNull(fresh.previousVersionId)
            assertNull(recreated.getVersion(namespace, "shared", expired.versionId).getOrThrow())
            assertEquals("mutable", recreated.get(namespace, "shared").getOrThrow())
            assertEquals(true, recreated.deleteVersioned(namespace, "shared").getOrThrow())
            assertEquals("mutable", recreated.get(namespace, "shared").getOrThrow())
        }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
