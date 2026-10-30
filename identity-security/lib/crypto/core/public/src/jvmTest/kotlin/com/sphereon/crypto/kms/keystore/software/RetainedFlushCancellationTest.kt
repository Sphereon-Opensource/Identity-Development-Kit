package com.sphereon.crypto.kms.keystore.software

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test-only provider delegates real PKCS12 operations and pauses the first armed file flush. */
class FlushBarrierKeyStoreSpi : KeyStoreSpi() {
    private val delegate = KeyStore.getInstance("PKCS12", requireNotNull(Security.getProvider(delegateProvider)))
    override fun engineLoad(stream: InputStream?, password: CharArray?) = delegate.load(stream, password)
    override fun engineStore(stream: OutputStream, password: CharArray?) {
        if (armed.compareAndSet(true, false)) {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "flush barrier timed out" }
        }
        delegate.store(stream, password)
    }
    override fun engineGetKey(alias: String, password: CharArray?): Key? = delegate.getKey(alias, password)
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = delegate.getCertificateChain(alias)
    override fun engineGetCertificate(alias: String): Certificate? = delegate.getCertificate(alias)
    override fun engineGetCreationDate(alias: String): Date? = delegate.getCreationDate(alias)
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) = delegate.setKeyEntry(alias, key, password, chain)
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = delegate.setKeyEntry(alias, key, chain)
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) = delegate.setCertificateEntry(alias, cert)
    override fun engineDeleteEntry(alias: String) = delegate.deleteEntry(alias)
    override fun engineAliases(): Enumeration<String> = delegate.aliases()
    override fun engineContainsAlias(alias: String) = delegate.containsAlias(alias)
    override fun engineSize() = delegate.size()
    override fun engineIsKeyEntry(alias: String) = delegate.isKeyEntry(alias)
    override fun engineIsCertificateEntry(alias: String) = delegate.isCertificateEntry(alias)
    override fun engineGetCertificateAlias(cert: Certificate): String? = delegate.getCertificateAlias(cert)
    override fun engineGetEntry(alias: String, protection: KeyStore.ProtectionParameter?): KeyStore.Entry? = delegate.getEntry(alias, protection)
    override fun engineSetEntry(alias: String, entry: KeyStore.Entry, protection: KeyStore.ProtectionParameter?) = delegate.setEntry(alias, entry, protection)
    override fun engineEntryInstanceOf(alias: String, entryClass: Class<out KeyStore.Entry>) = delegate.entryInstanceOf(alias, entryClass)
    companion object {
        lateinit var delegateProvider: String
        var entered = CountDownLatch(1)
        var release = CountDownLatch(1)
        val armed = AtomicBoolean(false)
    }
}

class RetainedFlushCancellationTest {
    private fun key(alias: String): ResolvedKeyInfo<Jwk> = ResolvedKeyInfo(
        key = Jwk(kty = JwaKeyType.oct, k = ByteArray(32).also { SecureRandom().nextBytes(it) }.encodeToBase64Url(),
            alg = JwaAlgorithm.HS256, kid = alias),
        keyVisibility = KeyVisibility.PRIVATE, keyType = KeyTypeMapping.Symmetric, alias = alias,
        providerId = "software", kid = alias, signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
    )

    @Test fun cancellationDuringFlushKeepsMutationLockUntilDurableWriterFinishes() = runTest {
        val prior = Security.getProviders("KeyStore.PKCS12").first().name
        FlushBarrierKeyStoreSpi.delegateProvider = prior
        FlushBarrierKeyStoreSpi.entered = CountDownLatch(1)
        FlushBarrierKeyStoreSpi.release = CountDownLatch(1)
        val provider = object : Provider("RetainedFlushBarrier", "1.0", "test PKCS12 flush barrier") {
            init { put("KeyStore.PKCS12", FlushBarrierKeyStoreSpi::class.java.name) }
        }
        Security.insertProviderAt(provider, 1)
        try {
            val dir = Files.createTempDirectory("retained-flush-cancellation").toFile().also { it.deleteOnExit() }
            val config = Pkcs12KeyStoreConfig(id = "software", password = "test-password", path = dir.resolve("keys.p12").absolutePath,
                persist = true, overwriteAlias = false, retainAliases = true, keyVisibility = KeyVisibility.PRIVATE.keyVisibility)
            val first = SoftwareKeyStoreService(config)
            val second = SoftwareKeyStoreService(config)
            first.listKeys(); second.listKeys()
            val firstKey = key("first")
            val secondKey = key("second")
            FlushBarrierKeyStoreSpi.armed.set(true)
            coroutineScope {
                val writer = async(Dispatchers.IO) { first.storeKey(firstKey, "software", "first") }
                assertTrue(FlushBarrierKeyStoreSpi.entered.await(5, TimeUnit.SECONDS), "real PKCS12 writer did not reach its flush")
                writer.cancel()
                val competitorStarted = CountDownLatch(1)
                val competitor = async(Dispatchers.IO) {
                    competitorStarted.countDown()
                    second.storeKey(secondKey, "software", "second")
                }
                try {
                    assertTrue(competitorStarted.await(5, TimeUnit.SECONDS))
                    Thread.sleep(500)
                    assertFalse(writer.isCompleted, "cancellation released the lock while the durable writer remained in flight")
                    assertFalse(competitor.isCompleted, "another instance crossed the active writer's lock")
                } finally {
                    FlushBarrierKeyStoreSpi.release.countDown()
                }
                writer.join()
                competitor.await()
            }
            val reopened = SoftwareKeyStoreService(config)
            assertEquals(firstKey.key.k, (reopened.getKey(KeyInfo<Jwk>(alias = "first", keyVisibility = KeyVisibility.PRIVATE)).key as Jwk).k)
            assertEquals(secondKey.key.k, (reopened.getKey(KeyInfo<Jwk>(alias = "second", keyVisibility = KeyVisibility.PRIVATE)).key as Jwk).k)
        } finally {
            FlushBarrierKeyStoreSpi.release.countDown()
            FlushBarrierKeyStoreSpi.armed.set(false)
            Security.removeProvider(provider.name)
        }
    }
}
