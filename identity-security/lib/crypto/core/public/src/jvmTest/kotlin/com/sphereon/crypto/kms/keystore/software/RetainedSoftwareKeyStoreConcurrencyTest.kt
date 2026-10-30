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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises two real file-backed keystore instances, private import and reopen. */
class RetainedSoftwareKeyStoreConcurrencyTest {
    @Test fun retainedProfileRejectsMutableOrNonPersistentConfiguration() {
        val path = Files.createTempDirectory("retained-profile-config").resolve("keys.p12").toString()
        val valid = Pkcs12KeyStoreConfig(id = "software", password = "test-password", path = path,
            persist = true, overwriteAlias = false, retainAliases = true)
        assertFailsWith<IllegalArgumentException> { SoftwareKeyStoreService(valid.copy(overwriteAlias = true)) }
        assertFailsWith<IllegalArgumentException> { SoftwareKeyStoreService(valid.copy(persist = false)) }
    }

    private fun key(alias: String): ResolvedKeyInfo<Jwk> = ResolvedKeyInfo(
        key = Jwk(kty = JwaKeyType.oct, k = ByteArray(32).also { SecureRandom().nextBytes(it) }.encodeToBase64Url(),
            alg = JwaAlgorithm.HS256, kid = alias),
        keyVisibility = KeyVisibility.PRIVATE, keyType = KeyTypeMapping.Symmetric, alias = alias,
        providerId = "software", kid = alias, signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
    )

    @Test fun concurrentImportsCannotReplaceRetainedAliasAndReopenKeepsWinner() = runTest {
        val root = Files.createTempDirectory("retained-keystore-race").toFile().also { it.deleteOnExit() }
        repeat(1) { iteration ->
            val config = Pkcs12KeyStoreConfig(id = "software", password = "test-password",
                path = root.resolve("$iteration.p12").absolutePath, persist = true, overwriteAlias = false,
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility, retainAliases = true)
            val services = listOf(SoftwareKeyStoreService(config), SoftwareKeyStoreService(config))
            services.forEach { it.listKeys() }
            val keys = listOf(key("retained"), key("retained"))
            assertNotEquals(keys[0].key.k, keys[1].key.k)
            val results = coroutineScope {
                val lockFile = root.resolve("$iteration.p12.lock").toPath()
                FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                    val lock = channel.lock()
                    val started = CountDownLatch(2)
                    try {
                        val jobs = services.indices.map { index -> async(Dispatchers.IO) {
                            started.countDown()
                            runCatching { services[index].storeKey(keys[index], "software", "retained") }
                        } }
                        assertTrue(started.await(5, TimeUnit.SECONDS), "both imports must reach the storage boundary")
                        Thread.sleep(200)
                        jobs.forEach { assertFalse(it.isCompleted, "import must wait for the file mutation lock") }
                        lock.release()
                        jobs.awaitAll()
                    } finally {
                        if (lock.isValid) lock.release()
                    }
                }
            }
            assertEquals(1, results.count { it.isSuccess }, "exactly one private import must own the alias")
            val winner = keys[results.indexOfFirst { it.isSuccess }]
            val reopened = SoftwareKeyStoreService(config).getKey(KeyInfo<Jwk>(alias = "retained", keyVisibility = KeyVisibility.PRIVATE))
            assertEquals(winner.key.k, (reopened.key as Jwk).k)
            assertTrue(SoftwareKeyStoreService(config).isRetainedKeyAlias("retained"))
            assertFalse(SoftwareKeyStoreService(config).isRetainedKeyAlias("missing"))
            assertTrue(runCatching { SoftwareKeyStoreService(config).storeKey(key("retained"), "software", "retained") }.isFailure)
            val mutable = SoftwareKeyStoreService(config.copy(overwriteAlias = true, retainAliases = false))
            assertFalse(mutable.isRetainedKeyAlias("retained"))
            assertFailsWith<IllegalStateException> { mutable.storeKey(key("retained"), "software", "retained") }
            assertFailsWith<IllegalStateException> { mutable.deleteKey(KeyInfo<Jwk>(alias = "retained")) }
            val afterDeniedMutations = SoftwareKeyStoreService(config).getKey(KeyInfo<Jwk>(alias = "retained", keyVisibility = KeyVisibility.PRIVATE))
            assertEquals(winner.key.k, (afterDeniedMutations.key as Jwk).k)
        }
    }

    @Test fun independentProcessLockFencesRealKeystoreImport() = runTest {
        val root = Files.createTempDirectory("retained-keystore-process").toFile().also { it.deleteOnExit() }
        val keystore = root.resolve("keys.p12")
        val config = Pkcs12KeyStoreConfig(id = "software", password = "test-password", path = keystore.absolutePath,
            persist = true, overwriteAlias = false, retainAliases = true, keyVisibility = KeyVisibility.PRIVATE.keyVisibility)
        val service = SoftwareKeyStoreService(config)
        service.listKeys()
        val source = root.resolve("LockOwner.java")
        source.writeText("""
            import java.nio.channels.*;
            import java.nio.file.*;
            public class LockOwner {
              public static void main(String[] args) throws Exception {
                try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                  Files.writeString(Path.of(args[1]), "ready");
                  long deadline = System.nanoTime() + 20_000_000_000L;
                  while (!Files.exists(Path.of(args[2])) && System.nanoTime() < deadline) Thread.sleep(20);
                  if (!Files.exists(Path.of(args[2]))) System.exit(2);
                }
              }
            }
        """.trimIndent())
        val lockPath = Path.of(keystore.absolutePath + ".lock")
        val ready = root.resolve("ready").toPath()
        val release = root.resolve("release").toPath()
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val executable = Path.of(System.getProperty("java.home"), "bin", if (windows) "javaw.exe" else "java")
        val output = root.resolve("child.out")
        val errors = root.resolve("child.err")
        val child = ProcessBuilder(executable.toString(), source.absolutePath, lockPath.toString(), ready.toString(), release.toString())
            .redirectOutput(output).redirectError(errors).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!Files.exists(ready) && child.isAlive && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(Files.exists(ready), "lock holder did not start: ${errors.readText()}")
            coroutineScope {
                val started = CountDownLatch(1)
                val material = key("cross-process")
                val write = async(Dispatchers.IO) {
                    started.countDown()
                    service.storeKey(material, "software", "cross-process")
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                Thread.sleep(200)
                assertFalse(write.isCompleted, "another process owns the file mutation lock")
                Files.writeString(release, "release")
                assertEquals("cross-process", write.await().alias)
                assertTrue(child.waitFor(5, TimeUnit.SECONDS), "lock holder did not exit")
                assertEquals(0, child.exitValue(), errors.readText())
                val reopened = SoftwareKeyStoreService(config).getKey(KeyInfo<Jwk>(alias = "cross-process", keyVisibility = KeyVisibility.PRIVATE))
                assertEquals(material.key.k, (reopened.key as Jwk).k)
            }
        } finally {
            if (child.isAlive) {
                child.destroy()
                if (!child.waitFor(5, TimeUnit.SECONDS)) {
                    child.destroyForcibly()
                    child.waitFor(5, TimeUnit.SECONDS)
                }
            }
        }
    }
}
