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
 *
 */
package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.interop.toPkcs8PrivateKeyInfo
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.HasKeyStoreService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.config.CaOpts
import com.sphereon.ktor.http.client.config.ClientSslConfig
import com.sphereon.ktor.http.client.config.KeystoreCertificateOpts
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.ChallengeHandler
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodClientCertificate
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionTask
import platform.Foundation.credentialForTrust
import platform.Foundation.credentialWithIdentity
import platform.Security.SecCertificateCopyPublicKey
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecIdentityRef
import platform.Security.SecIdentityRefVar
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyAttributes
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrApplicationLabel
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrLabel
import platform.Security.kSecClass
import platform.Security.kSecClassIdentity
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnRef
import platform.Security.kSecValueData
import platform.Security.kSecValueRef
import platform.darwin.OSStatus

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientFactoryIosImpl(
    private val execution: SessionExecution,
    val kms: KeyManagerService,
    private val keyStoreManager: KeyStoreManager,
) : HttpClientFactory {
    val keyStores: MutableSet<KeyStore> = mutableSetOf()
    private val log = execution.log.logManager.withTag("HttpClientFactory")

    init {
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.app))
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.tenant))
        keyStores.addAll(keyStoreManager.createFromProperties(execution.conf.principal))
        log.debug("Keystores for factory: ${keyStores.joinToString { it.id }}")
        kms.getProviderIds().forEach { providerId ->
            val keyStoreService = (kms.getProvider(providerId) as? HasKeyStoreService)?.keyStore ?: return@forEach
            (keyStoreService as? KeyStore)?.let {
                keyStores.add(it)
                log.debug("Added KeyStore $it from provider $providerId")
            }
        }
    }

    /**
     * Creates an HTTP client based on the specified options.
     *
     * @param options the [LegacyHttpClientOptions] containing engine type and SSL config
     * @return A [HttpClient]
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun createClient(options: HttpClientOptions): HttpClient {
        require(isSupportedOptions(options)) { "Provided http client options are not supported on this platform" }

        with(options) {
            val engine = Darwin

            return HttpClient(engine) {
                // install cache if requested
                if (enableHttpCache) {
                    install(HttpCache) {
                        // apply user‐supplied cache config, if any
                        httpCacheConfig?.invoke(this)
                    }
                }

                if (enableContentNegotiation) {
                    install(ContentNegotiation) {
                        contentNegotiationConfig?.invoke(this)
                    }
                }

                if (enableLogging) {
                    install(Logging) {
//                        loggingConfig?.invoke(this)
                    }
                }

                if (options.defaultRequest != null) {
                    defaultRequest(options.defaultRequest!!)
                }

                // Build client identity map if mTLS is configured
                val identityMap: Map<String, SecIdentityRef> =
                    if (options.sslConfig.client.isMtls()) {
                        runBlocking { buildIdentityMap(options.sslConfig.client) }
                    } else {
                        emptyMap()
                    }

                // Build server trust anchors if additional CAs are configured
                val serverTrustAnchors: List<SecCertificateRef>? =
                    if (options.sslConfig.server.ca.additionalCAs
                            .isNotEmpty()
                    ) {
                        runBlocking { buildServerTrustAnchors(options.sslConfig.server.ca) }
                    } else {
                        null
                    }

                // Configure challenge handler for both client certificates and server trust
                if (identityMap.isNotEmpty() || serverTrustAnchors != null) {
                    engine {
                        handleChallenge(
                            (
                                {
                                    session: NSURLSession,
                                    task: NSURLSessionTask,
                                    challenge: NSURLAuthenticationChallenge,
                                    completion: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
                                    ->
                                    when (challenge.protectionSpace.authenticationMethod) {
                                        NSURLAuthenticationMethodClientCertificate -> {
                                            // Handle client certificate authentication (mTLS)
                                            handleClientCertificateChallenge(challenge, identityMap, completion)
                                        }

                                        NSURLAuthenticationMethodServerTrust -> {
                                            // Handle server certificate validation
                                            handleServerTrustChallenge(
                                                challenge,
                                                options.sslConfig.server.ca,
                                                serverTrustAnchors,
                                                completion,
                                            )
                                        }

                                        else -> {
                                            completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                                        }
                                    }
                                } as Any
                            ) as ChallengeHandler,
                        ) // due to KTOR/Darwin interop bug KTOR-6353
                    }
                }

                additionalConfig?.invoke(this)
            }.also { client ->
                val validationPolicy = urlValidation
                if (validationPolicy != null) {
                    client.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
                        validationPolicy.validate(context.url.build())
                    }
                }
            }
        }
    }

    /**
     * Returns the list of [HttpClientEngineType] supported by this provider on the current platform.
     *
     * @return A list of [HttpClientEngineType]
     */
    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.DARWIN)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.DARWIN

    override fun isSupportedOptions(options: HttpClientOptions): Boolean {
        if (!getEngineTypesSupported().contains(options.engine ?: getEngineTypeDefault())) {
            log.error("Http client engine type ${options.engine} not supported on iOS/Darwin")
            return false
        }
        return true
    }

    /**
     * Builds a map of hostname to SecIdentityRef from the SSL configuration.
     * The empty string key ("") represents the default certificate.
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun buildIdentityMap(sslConfig: ClientSslConfig): Map<String, SecIdentityRef> {
        val identityMap = mutableMapOf<String, SecIdentityRef>()

        // Process default certificate
        sslConfig.defaultCertificate?.let { certOpts ->
            try {
                val identity = createIdentityFromCertOpts(certOpts)
                if (identity != null) {
                    identityMap[""] = identity
                }
            } catch (expected: Exception) {
                log.error("Failed to create identity for default certificate ${certOpts.certificateAlias}: ${expected.message}", expected)
                throw expected
            }
        }

        // Process per-host certificates
        sslConfig.perHostCertificate.forEach { (host, certOpts) ->
            try {
                val identity = createIdentityFromCertOpts(certOpts)
                if (identity != null) {
                    identityMap[host] = identity
                }
            } catch (expected: Exception) {
                log.error("Failed to create identity for host $host with certificate ${certOpts.certificateAlias}: ${expected.message}", expected)
                throw expected
            }
        }

        return identityMap
    }

    /**
     * Creates a SecIdentityRef from KeystoreCertificateOpts by retrieving the certificate and key
     * from the configured keystores.
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun createIdentityFromCertOpts(certOpts: KeystoreCertificateOpts): SecIdentityRef? {
        val keyStore =
            keyStores.find { it.id == certOpts.keyStoreId }
                ?: throw IllegalArgumentException("KeyStore not found: ${certOpts.keyStoreId}, available: ${keyStores.joinToString(",") { it.id }}")

        // Retrieve certificate chain
        val certChain: Array<Certificate> = keyStore.getCertificateChain(certOpts.certificateAlias)
        require(certChain.isNotEmpty()) { "Certificate chain is empty for alias: ${certOpts.certificateAlias}" }

        // Retrieve native SecKeyRef directly from keychain (private keys are not exported)
        val secKey =
            com.sphereon.crypto.kms.keystore.software
                .getNativeKeychainKey(certOpts.certificateAlias)
                ?: throw IllegalStateException("Failed to retrieve native SecKeyRef for alias: ${certOpts.certificateAlias}")

        // Convert certificates to native iOS types
        val secCerts = convertCertificatesToSecCerts(certChain)

        // Create identity using native key reference (no DER export needed)
        return createIdentityViaKeychainNative(certOpts.certificateAlias, secCerts.first(), secKey)
    }

    /**
     * Converts an array of Certificate DTOs to SecCertificateRef array.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun convertCertificatesToSecCerts(certChain: Array<Certificate>): List<SecCertificateRef> =
        certChain.map { cert ->
            val cfData: CFDataRef? =
                cert.der.usePinned { pinned ->
                    val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                    CFDataCreate(null, ptr, cert.der.size.toLong())
                }
            val dataRef = cfData ?: throw IllegalStateException("Failed to create CFDataRef for certificate")
            SecCertificateCreateWithData(null, dataRef)
                ?: throw IllegalStateException("Failed to create SecCertificateRef")
        }

    /**
     * Converts a JWK to a native SecKeyRef using awesn1 for DER encoding
     * and Apple Security framework for SecKey creation.
     * Returns both the SecKeyRef and the DER-encoded key data.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun convertJwkToSecKey(jwk: Jwk): Pair<SecKeyRef, ByteArray> =
        memScoped {
            val pkcs8 = jwk.toPkcs8PrivateKeyInfo()

            // Extract the inner key format that SecKeyCreateWithData expects
            val (innerDer, keyType) =
                when (jwk.kty) {
                    com.sphereon.crypto.core.jose.JwaKeyType.EC -> {
                        pkcs8.decodeEcPrivateKey().encodeToTlv().derEncoded to kSecAttrKeyTypeECSECPrimeRandom
                    }

                    com.sphereon.crypto.core.jose.JwaKeyType.RSA -> {
                        pkcs8.decodeRsaPrivateKey().encodeToTlv().derEncoded to kSecAttrKeyTypeRSA
                    }

                    else -> {
                        throw IllegalStateException("Unsupported key type: ${jwk.kty}")
                    }
                }

            val cfData =
                innerDer.asUByteArray().refTo(0).let { ptr ->
                    CFDataCreate(null, ptr, innerDer.size.toLong())
                } ?: throw IllegalStateException("Failed to create CFData for key")

            val attributes =
                CFDictionaryCreateMutable(
                    null,
                    3,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                )
            CFDictionarySetValue(attributes, kSecAttrKeyType, keyType)
            CFDictionarySetValue(attributes, kSecAttrKeyClass, kSecAttrKeyClassPrivate)

            val error = alloc<CFErrorRefVar>()
            val secKey =
                SecKeyCreateWithData(cfData, attributes, error.ptr)
                    ?: throw IllegalStateException(
                        "Failed to create SecKey from DER: ${
                            CFErrorCopyDescription(error.value)?.let { CFStringGetCStringPtr(it, kCFStringEncodingUTF8)?.toKString() } ?: "unknown error"
                        }",
                    )

            val fullDer = pkcs8.encodeToTlv().derEncoded
            secKey to fullDer
        }

    /**
     * Create identity using a native SecKeyRef (already stored in keychain).
     * This method doesn't require DER export of the private key.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createIdentityViaKeychainNative(
        alias: String,
        cert: SecCertificateRef,
        key: SecKeyRef,
    ): SecIdentityRef =
        memScoped {
            val label = "com.sphereon.ktor.mtls.$alias".encodeToByteArray()
            val labelData =
                label.usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret(), label.size.toLong())
                } ?: throw IllegalStateException("Failed to create CFDataRef for label")

            // Get the private key's application label FIRST (this is what iOS uses to match identities)
            val keyAttrs = SecKeyCopyAttributes(key) as? platform.CoreFoundation.CFDictionaryRef
            val keyAppLabel =
                keyAttrs?.let {
                    CFDictionaryGetValue(it, kSecAttrApplicationLabel) as? CFDataRef
                } ?: throw IllegalStateException("Private key does not have kSecAttrApplicationLabel")

            val keyAppLabelBytes = CFDataGetBytePtr(keyAppLabel)?.readBytes(CFDataGetLength(keyAppLabel).toInt())
            log.debug("Private key application label (${keyAppLabelBytes?.size} bytes): ${keyAppLabelBytes?.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

            // Get the cert's public key to verify it matches
            val certPubKey =
                SecCertificateCopyPublicKey(cert)
                    ?: throw IllegalStateException("Failed to get public key from certificate")

            val pubKeyAttrs = SecKeyCopyAttributes(certPubKey) as? platform.CoreFoundation.CFDictionaryRef
            val certAppLabel =
                pubKeyAttrs?.let {
                    CFDictionaryGetValue(it, kSecAttrApplicationLabel) as? CFDataRef
                } ?: throw IllegalStateException("Failed to get kSecAttrApplicationLabel from certificate's public key")

            val certAppLabelBytes = CFDataGetBytePtr(certAppLabel)?.readBytes(CFDataGetLength(certAppLabel).toInt())
            log.debug("Certificate public key application label (${certAppLabelBytes?.size} bytes): ${certAppLabelBytes?.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

            // Verify labels match
            if (!certAppLabelBytes.contentEquals(keyAppLabelBytes)) {
                val certAppLabelHex = certAppLabelBytes?.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
                val keyAppLabelHex = keyAppLabelBytes?.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
                throw IllegalStateException("Application label mismatch: cert=$certAppLabelHex, key=$keyAppLabelHex")
            }

            // Use the key's application label for all operations (this is the authoritative one)
            val appLabel = keyAppLabel

            // Clean up any existing certificate with the prefixed mTLS label only
            // IMPORTANT: Do NOT delete by public key hash alone, as that would delete
            // certificates stored by SoftwareKeyStoreService with plain aliases
            val deleteCertQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            deleteCertQuery?.let {
                CFDictionarySetValue(it, kSecClass, platform.Security.kSecClassCertificate)
                CFDictionarySetValue(it, kSecAttrLabel, labelData)
                SecItemDelete(it)
            }

            // Add certificate with explicit kSecAttrPublicKeyHash to ensure identity linking works
            // iOS uses kSecAttrPublicKeyHash on certs + kSecAttrApplicationLabel on keys to form identities
            val certDict =
                CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                    ?: throw IllegalStateException("Failed to create certificate dictionary")
            CFDictionarySetValue(certDict, kSecClass, platform.Security.kSecClassCertificate)
            CFDictionarySetValue(certDict, kSecValueRef, cert)
            CFDictionarySetValue(certDict, kSecAttrLabel, labelData)
            // Explicitly set kSecAttrPublicKeyHash to match the private key's kSecAttrApplicationLabel
            // This is CRITICAL for iOS to link the cert and key into an identity
            CFDictionarySetValue(certDict, platform.Security.kSecAttrPublicKeyHash, appLabel)

            val certStatus = SecItemAdd(certDict, null)
            log.debug("Certificate add status: $certStatus")
            check(certStatus == 0 || certStatus == -25299) {
                "Failed to add certificate to keychain: status=$certStatus"
            }

            // Also store certificate with plain alias label for SoftwareKeyStoreService compatibility
            // This ensures getCertificate(alias) works for other parts of the system
            val plainAliasLabel =
                alias.encodeToByteArray().usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret(), alias.length.toLong())
                }
            if (plainAliasLabel != null) {
                // Delete any existing certificate with plain alias
                val deletePlainQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                deletePlainQuery?.let {
                    CFDictionarySetValue(it, kSecClass, platform.Security.kSecClassCertificate)
                    CFDictionarySetValue(it, kSecAttrLabel, plainAliasLabel)
                    SecItemDelete(it)
                }

                // Add certificate with plain alias
                val plainCertDict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                if (plainCertDict != null) {
                    CFDictionarySetValue(plainCertDict, kSecClass, platform.Security.kSecClassCertificate)
                    CFDictionarySetValue(plainCertDict, kSecValueRef, cert)
                    CFDictionarySetValue(plainCertDict, kSecAttrLabel, plainAliasLabel)
                    val plainCertStatus = SecItemAdd(plainCertDict, null)
                    log.debug("Certificate add with plain alias status: $plainCertStatus")
                }
            }

            log.debug("Application labels match - attempting to create identity")

            // Build identity by querying for cert + private key
            // The trick is that we need to store them both so iOS can find them together

            // First, verify we can retrieve the private key separately
            val privKeyQuery =
                CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                    ?: throw IllegalStateException("Failed to create private key query dictionary")
            CFDictionarySetValue(privKeyQuery, kSecClass, platform.Security.kSecClassKey)
            CFDictionarySetValue(privKeyQuery, kSecAttrApplicationLabel, appLabel)
            CFDictionarySetValue(privKeyQuery, kSecAttrKeyClass, platform.Security.kSecAttrKeyClassPrivate)
            CFDictionarySetValue(privKeyQuery, kSecReturnRef, kCFBooleanTrue)

            val privKeyOut = alloc<CFTypeRefVar>()
            val privKeyStatus = SecItemCopyMatching(privKeyQuery, privKeyOut.ptr)
            check(privKeyStatus == 0) {
                "Failed to find private key by application label: status=$privKeyStatus"
            }

            val privateKeyForIdentity =
                privKeyOut.value as? SecKeyRef
                    ?: throw IllegalStateException("Private key found but cast to SecKeyRef failed")

            log.debug("Found private key, now querying for identity")

            // Debug: Print application label being used for identity query
            val appLabelBytes = CFDataGetBytePtr(appLabel)?.readBytes(CFDataGetLength(appLabel).toInt())
            val appLabelHex = appLabelBytes?.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            log.debug("Using application label for identity query: $appLabelHex")

            // First verify the certificate is actually in the keychain
            val certQuery =
                CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                    ?: throw IllegalStateException("Failed to create certificate query dictionary")
            CFDictionarySetValue(certQuery, kSecClass, platform.Security.kSecClassCertificate)
            CFDictionarySetValue(certQuery, kSecAttrApplicationLabel, appLabel)
            CFDictionarySetValue(certQuery, kSecReturnRef, kCFBooleanTrue)

            val certOut = alloc<CFTypeRefVar>()
            val certQueryStatus = SecItemCopyMatching(certQuery, certOut.ptr)
            log.debug("Certificate query by application label: status=$certQueryStatus")
            if (certQueryStatus != 0) {
                log.error("Certificate not found in keychain by application label! status=$certQueryStatus")
                // Try querying by label instead
                val certByLabelQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                if (certByLabelQuery != null) {
                    CFDictionarySetValue(certByLabelQuery, kSecClass, platform.Security.kSecClassCertificate)
                    CFDictionarySetValue(certByLabelQuery, kSecAttrLabel, labelData)
                    CFDictionarySetValue(certByLabelQuery, kSecReturnRef, kCFBooleanTrue)
                    val certByLabelOut = alloc<CFTypeRefVar>()
                    val certByLabelStatus = SecItemCopyMatching(certByLabelQuery, certByLabelOut.ptr)
                    log.debug("Certificate query by kSecAttrLabel: status=$certByLabelStatus")
                }
            }

            // Now query for identity using the same application label
            val idQuery =
                CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                    ?: throw IllegalStateException("Failed to create identity query dictionary")
            CFDictionarySetValue(idQuery, kSecClass, platform.Security.kSecClassIdentity)
            CFDictionarySetValue(idQuery, kSecAttrApplicationLabel, appLabel)
            CFDictionarySetValue(idQuery, kSecReturnRef, kCFBooleanTrue)

            val out = alloc<CFTypeRefVar>()
            val idStatus = SecItemCopyMatching(idQuery, out.ptr)
            if (idStatus != 0) {
                log.error("Identity query failed with status=$idStatus (errSecItemNotFound=-25300, errSecNoAccessForItem=-25243)")
                throw IllegalStateException("Failed to find identity by application label: status=$idStatus, appLabel=$appLabelHex")
            }

            log.debug("Successfully found identity for alias: $alias")
            out.value as SecIdentityRef
        }

    /**
     * Absolute simplest approach: Store cert and private key in keychain separately,
     * ensuring they are linked by iOS automatically.
     *
     * @deprecated Use createIdentityViaKeychainNative instead - this method requires DER export
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createIdentityViaKeychain(
        alias: String,
        cert: SecCertificateRef,
        key: SecKeyRef,
        keyDer: ByteArray,
    ): SecIdentityRef? =
        memScoped {
            val label = "com.sphereon.ktor.mtls.$alias".encodeToByteArray()
            val labelData =
                label.usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret(), label.size.toLong())
                } ?: return@memScoped null

            // Clean up any existing items first
            listOf(
                platform.Security.kSecClassCertificate,
                platform.Security.kSecClassKey,
                kSecClassIdentity,
            ).forEach { secClass ->
                val deleteQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                deleteQuery?.let {
                    CFDictionarySetValue(it, kSecClass, secClass)
                    CFDictionarySetValue(it, kSecAttrLabel, labelData)
                    SecItemDelete(it)
                }
            }

            // 1. Add certificate - iOS auto-generates kSecAttrApplicationLabel
            val certDict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr) ?: return@memScoped null
            CFDictionarySetValue(certDict, kSecClass, platform.Security.kSecClassCertificate)
            CFDictionarySetValue(certDict, kSecValueRef, cert)
            CFDictionarySetValue(certDict, kSecAttrLabel, labelData)

            val certStatus = SecItemAdd(certDict, null)
            if (certStatus != 0 && certStatus != -25299) {
                log.error("Failed to add certificate: status=$certStatus")
                return@memScoped null
            }

            // 2. Get the cert's public key to compute the hash for kSecAttrApplicationLabel
            val certPubKey =
                SecCertificateCopyPublicKey(cert) ?: run {
                    log.error("Failed to get public key from certificate")
                    return@memScoped null
                }

            // Use iOS's SecKeyCopyAttributes to get the application label that iOS assigned
            val pubKeyAttrs = platform.Security.SecKeyCopyAttributes(certPubKey) as? platform.CoreFoundation.CFDictionaryRef
            val appLabel =
                pubKeyAttrs?.let {
                    platform.CoreFoundation.CFDictionaryGetValue(it, kSecAttrApplicationLabel) as? CFDataRef
                } ?: run {
                    log.error("Failed to get kSecAttrApplicationLabel from certificate's public key")
                    return@memScoped null
                }

            // 3. Add private key with the SAME application label
            val keyData =
                keyDer.usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret(), keyDer.size.toLong())
                } ?: return@memScoped null

            val keyDict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr) ?: return@memScoped null
            CFDictionarySetValue(keyDict, kSecClass, platform.Security.kSecClassKey)
            CFDictionarySetValue(keyDict, platform.Security.kSecValueData, keyData)
            CFDictionarySetValue(keyDict, kSecAttrLabel, labelData)
            CFDictionarySetValue(keyDict, kSecAttrApplicationLabel, appLabel) // CRITICAL: use same appLabel as cert
            CFDictionarySetValue(keyDict, platform.Security.kSecAttrKeyClass, platform.Security.kSecAttrKeyClassPrivate)

            val keyStatus = SecItemAdd(keyDict, null)
            if (keyStatus != 0 && keyStatus != -25299) {
                log.error("Failed to add private key: status=$keyStatus")
                return@memScoped null
            }

            // 4. Now query for the identity - iOS should have linked them automatically
            val idQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr) ?: return@memScoped null
            CFDictionarySetValue(idQuery, kSecClass, kSecClassIdentity)
            CFDictionarySetValue(idQuery, kSecAttrLabel, labelData)
            CFDictionarySetValue(idQuery, kSecReturnRef, kCFBooleanTrue)

            val out = alloc<CFTypeRefVar>()
            val idStatus = SecItemCopyMatching(idQuery, out.ptr)
            if (idStatus != 0) {
                log.error("Failed to find identity after adding cert and key: status=$idStatus")
                return@memScoped null
            }

            out.value as? SecIdentityRef
        }

    /**
     * Simpler approach: Store cert and key separately, then create identity.
     * For Darwin/iOS, we can use the certificate and key directly in the credential
     * without needing a formal SecIdentityRef from the keychain.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createIdentitySimple(
        alias: String,
        cert: SecCertificateRef,
        key: SecKeyRef,
    ): SecIdentityRef? =
        memScoped {
            // Store the certificate in keychain
            val label = "com.sphereon.ktor.mtls.$alias".encodeToByteArray()
            val labelData =
                label.usePinned { pinned ->
                    val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                    CFDataCreate(null, ptr, label.size.toLong())
                } ?: return@memScoped null

            // Delete any existing items
            val deleteCertQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                )
            if (deleteCertQuery != null) {
                CFDictionarySetValue(deleteCertQuery, kSecClass, platform.Security.kSecClassCertificate)
                CFDictionarySetValue(deleteCertQuery, kSecAttrLabel, labelData)
                SecItemDelete(deleteCertQuery)
            }

            // Add certificate
            val certDict =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionarySetValue(certDict, kSecClass, platform.Security.kSecClassCertificate)
            CFDictionarySetValue(certDict, kSecValueRef, cert)
            CFDictionarySetValue(certDict, kSecAttrLabel, labelData)

            val certStatus = SecItemAdd(certDict, null)
            if (certStatus != 0 && certStatus != -25299) {
                log.error("Failed to add cert for alias $alias: status=$certStatus")
            }

            // For mTLS with ktor Darwin engine, we don't actually need a formal SecIdentityRef
            // The engine can work with cert+key directly. But let's try searching for an identity anyway.
            val searchQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionarySetValue(searchQuery, kSecClass, kSecClassIdentity)
            CFDictionarySetValue(searchQuery, kSecAttrLabel, labelData)
            CFDictionarySetValue(searchQuery, kSecReturnRef, kCFBooleanTrue)

            val out = alloc<CFTypeRefVar>()
            val searchStatus = SecItemCopyMatching(searchQuery, out.ptr)

            if (searchStatus == 0) {
                // Found an existing identity
                out.value as? SecIdentityRef
            } else {
                // No identity found - this is actually OK for ktor Darwin
                // We'll handle it differently in the challenge handler
                log.warn("No identity found in keychain for alias $alias (status=$searchStatus), will use cert+key directly")
                null
            }
        }

    /**
     * OLD APPROACH - keeping for reference but not used
     * Creates a SecIdentityRef by importing the certificate and key into the iOS keychain.
     *
     * On iOS, SecIdentityRef must be retrieved from the keychain; it cannot be created directly in memory.
     * This method temporarily imports the cert and key, retrieves the identity reference, and returns it.
     * The keychain items remain for the duration of the session.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createIdentityOld(
        alias: String,
        cert: SecCertificateRef,
        keyDer: ByteArray,
        additionalCerts: List<SecCertificateRef>,
    ): SecIdentityRef? =
        memScoped {
            val label = "com.sphereon.ktor.mtls.$alias".encodeToByteArray()
            val labelData =
                label.usePinned { pinned ->
                    val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                    CFDataCreate(null, ptr, label.size.toLong())
                } ?: return@memScoped null

            // First, clean up any existing certificate and key with this label
            // Delete certificate
            val deleteCertQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                )
            if (deleteCertQuery != null) {
                CFDictionarySetValue(deleteCertQuery, kSecClass, platform.Security.kSecClassCertificate)
                CFDictionarySetValue(deleteCertQuery, kSecAttrLabel, labelData)
                SecItemDelete(deleteCertQuery) // Ignore status - item may not exist
            }

            // Delete key
            val deleteKeyQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                )
            if (deleteKeyQuery != null) {
                CFDictionarySetValue(deleteKeyQuery, kSecClass, platform.Security.kSecClassKey)
                CFDictionarySetValue(deleteKeyQuery, kSecAttrLabel, labelData)
                SecItemDelete(deleteKeyQuery) // Ignore status - item may not exist
            }

            // Delete identity (if exists)
            val deleteIdentityQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                )
            if (deleteIdentityQuery != null) {
                CFDictionarySetValue(deleteIdentityQuery, kSecClass, kSecClassIdentity)
                CFDictionarySetValue(deleteIdentityQuery, kSecAttrLabel, labelData)
                SecItemDelete(deleteIdentityQuery) // Ignore status - item may not exist
            }

            // First, add the certificate to the keychain
            // Note: kSecAttrApplicationLabel is set automatically by iOS for certificates
            val certDict =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionarySetValue(certDict, kSecClass, platform.Security.kSecClassCertificate)
            CFDictionarySetValue(certDict, kSecValueRef, cert)
            CFDictionarySetValue(certDict, kSecAttrLabel, labelData)
            CFDictionarySetValue(certDict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)

            val certAddStatus: OSStatus = SecItemAdd(certDict, null)
            if (certAddStatus != 0 && certAddStatus != -25299) { // -25299 = errSecDuplicateItem
                log.error("Failed to add certificate to keychain for alias $alias: status=$certAddStatus")
                return@memScoped null
            }

            // Now retrieve the certificate to get its auto-generated kSecAttrApplicationLabel
            val certSearchQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionarySetValue(certSearchQuery, kSecClass, platform.Security.kSecClassCertificate)
            CFDictionarySetValue(certSearchQuery, kSecAttrLabel, labelData)
            CFDictionarySetValue(certSearchQuery, platform.Security.kSecReturnAttributes, kCFBooleanTrue)

            val certOut = alloc<CFTypeRefVar>()
            val certSearchStatus = SecItemCopyMatching(certSearchQuery, certOut.ptr)
            if (certSearchStatus != 0) {
                log.error("Failed to retrieve certificate attributes for alias $alias: status=$certSearchStatus")
                return@memScoped null
            }

            // Extract kSecAttrApplicationLabel from the certificate attributes
            val certAttrs = certOut.value as? platform.CoreFoundation.CFDictionaryRef
            if (certAttrs == null) {
                log.error("Failed to get certificate attributes dictionary for alias $alias")
                return@memScoped null
            }

            val appLabelData = platform.CoreFoundation.CFDictionaryGetValue(certAttrs, kSecAttrApplicationLabel) as? CFDataRef
            if (appLabelData == null) {
                log.error("Failed to get kSecAttrApplicationLabel from certificate for alias $alias")
                return@memScoped null
            }

            // Then, add the private key to the keychain using DER data
            val keyData =
                keyDer.usePinned { pinned ->
                    val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                    CFDataCreate(null, ptr, keyDer.size.toLong())
                } ?: return@memScoped null

            val keyDict =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionarySetValue(keyDict, kSecClass, platform.Security.kSecClassKey)
            CFDictionarySetValue(keyDict, platform.Security.kSecValueData, keyData)
            CFDictionarySetValue(keyDict, kSecAttrLabel, labelData)
            CFDictionarySetValue(keyDict, kSecAttrApplicationLabel, appLabelData)
            CFDictionarySetValue(keyDict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            CFDictionarySetValue(keyDict, platform.Security.kSecAttrKeyClass, platform.Security.kSecAttrKeyClassPrivate)

            // Add the key to the keychain
            val addStatus: OSStatus = SecItemAdd(keyDict, null)
            if (addStatus != 0 && addStatus != -25299) { // -25299 = errSecDuplicateItem
                log.error("Failed to add key to keychain for alias $alias: status=$addStatus")
                return@memScoped null
            }

            // Now retrieve the identity
            val searchQuery =
                CFDictionaryCreateMutable(
                    kCFAllocatorDefault,
                    0,
                    kCFTypeDictionaryKeyCallBacks.ptr,
                    kCFTypeDictionaryValueCallBacks.ptr,
                ) ?: return@memScoped null

            CFDictionarySetValue(searchQuery, kSecClass, kSecClassIdentity)
            CFDictionarySetValue(searchQuery, kSecAttrLabel, labelData)
            CFDictionarySetValue(searchQuery, kSecReturnRef, kCFBooleanTrue)

            val out = alloc<CFTypeRefVar>()
            val searchStatus: OSStatus = SecItemCopyMatching(searchQuery, out.ptr)
            if (searchStatus != 0) {
                log.error("Failed to retrieve identity from keychain for alias $alias: status=$searchStatus")
                return@memScoped null
            }

            out.value as? SecIdentityRef
        }

    /**
     * Builds a list of SecCertificateRef to use as trust anchors from the CaOpts configuration.
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun buildServerTrustAnchors(caOpts: CaOpts): List<SecCertificateRef> {
        val anchors = mutableListOf<SecCertificateRef>()

        // Add additional CAs from keystores
        caOpts.additionalCAs.forEach { ca ->
            val keyStore = keyStores.find { it.id == ca.keyStoreId }
            if (keyStore == null) {
                log.warn("KeyStore not found for server CA: ${ca.keyStoreId}. Available: ${keyStores.joinToString(",") { it.id }}")
                return@forEach
            }

            try {
                val certChain = keyStore.getCertificateChain(ca.certificateAlias)
                if (certChain.isEmpty()) {
                    log.warn("Certificate chain is empty for CA alias: ${ca.certificateAlias}")
                    return@forEach
                }

                // Convert the first certificate (the CA cert) to SecCertificateRef
                val caCert = certChain.first()
                val cfData: CFDataRef? =
                    caCert.der.usePinned { pinned ->
                        val ptr: CPointer<UByteVar> = pinned.addressOf(0).reinterpret()
                        CFDataCreate(null, ptr, caCert.der.size.toLong())
                    }
                if (cfData == null) {
                    return@forEach
                }
                val secCert = SecCertificateCreateWithData(null, cfData)
                if (secCert == null) {
                    return@forEach
                }

                anchors.add(secCert)
            } catch (expected: Exception) {
                log.warn("Failed to load CA certificate ${ca.certificateAlias}: ${expected.message}")
            }
        }

        return anchors
    }

    /**
     * Handles client certificate authentication challenge (mTLS).
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun handleClientCertificateChallenge(
        challenge: NSURLAuthenticationChallenge,
        identityMap: Map<String, SecIdentityRef>,
        completion: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        // Select identity based on host
        val host = challenge.protectionSpace.host
        val identity = identityMap[host] ?: identityMap[""] // Try host-specific, fall back to default

        identity?.let { id ->
            completion(
                NSURLSessionAuthChallengeUseCredential,
                NSURLCredential.credentialWithIdentity(
                    id,
                    null,
                    NSURLCredentialPersistence.NSURLCredentialPersistenceForSession,
                ),
            )
        } ?: completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
    }

    /**
     * Handles server trust validation challenge.
     * Validates server certificate against platform trust store and/or additional CAs.
     *
     * Note: Custom server CA validation on iOS requires access to serverTrust property which
     * is not directly exposed in Kotlin/Native interop. For now, we log a warning and use
     * default handling. Full implementation would require additional platform-specific bindings.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun handleServerTrustChallenge(
        challenge: NSURLAuthenticationChallenge,
        caOpts: CaOpts,
        serverTrustAnchors: List<SecCertificateRef>?,
        completion: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        // If we have additional CAs configured, log a warning as custom validation is not fully supported
        if (!serverTrustAnchors.isNullOrEmpty()) {
            log.warn(
                "Custom server CA validation requested but not fully supported on iOS platform - using default handling. " +
                    "Additional CAs configured: ${caOpts.additionalCAs.size}",
            )
        }

        // Use platform default handling
        // This will validate against the iOS system trust store
        completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
    }
}
