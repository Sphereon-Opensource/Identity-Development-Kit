/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.session.SessionScope


/**
 * CryptoServicesJS provides cryptographic services including X.509, COSE, and key mappings
 * with JavaScript callbacks to fit the JS ecosystem.
 *
 * This is the central entry point for external code to perform cose, X.509 actions
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, replaces = [CryptoServicesImpl::class])
class CryptoServicesJS(override val x509: X509VerifyService, override val cose: CoseCryptoService): CryptoServices {
    // The Javascript version exposes it with JS callbacks compared to the default CryptoServices
//    fun x509(platformCallback: IX509ServiceJSVerifyCallback = CallbacksImpl.x509(), trustedCerts: Set<String>? = null) = X509ServiceJS(platformCallback, trustedCerts)
//    fun cose(platformCallback: ICoseCryptoCallbackJS = CallbacksImpl.coseCrypto()) = CoseCryptoServiceJS(platformCallback)
    override val mappings = CoseJoseKeyMappingService
    // TODO: JOSE
}

