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

package com.sphereon.crypto.core.sign

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.sign.model.CompleteSignatureRequest
import com.sphereon.crypto.core.sign.model.DigestRequest
import com.sphereon.crypto.core.sign.model.DigestResponse
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.SignatureForm
import com.sphereon.crypto.core.sign.model.SignatureLevel
import com.sphereon.crypto.core.sign.model.SignatureParameters
import com.sphereon.core.compat.JsExportCompat

/**
 * Unified signature service interface.
 *
 * IDK supports RAW/JWS/COSE signing via DefaultSignatureService.
 * EDK extends this with eIDAS forms (CAdES/JAdES/PAdES/XAdES) via SignatureProvider.
 *
 * Developers inject this interface and call the same methods regardless
 * of the underlying signing mechanism. The classpath determines what forms are available.
 */
interface SignatureService : SimpleSignatureService {

    /**
     * Create a digital signature.
     *
     * For RAW: signs the input bytes directly using the key.
     * For eIDAS (EDK): creates a CAdES/PAdES/JAdES/XAdES signature based on [parameters].
     *
     * @param signInput The input data and metadata required for creating the signature.
     * @param keyInfo Key information for the signing operation.
     * @param parameters Optional signature parameters controlling the signing behavior.
     * @return The generated signature output, or an error.
     */
    suspend fun sign(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>,
        parameters: SignatureParameters? = null,
    ): IdkResult<SignOutput, IdkError>

    /**
     * Two-step signing step 1: create a digest for external/HSM signing.
     *
     * Use this when the private key is not directly accessible (e.g. HSM, remote signing service).
     * The returned [DigestResponse] contains the digest to be signed externally, plus state
     * needed to complete the signature via [completeSignature].
     *
     * @param request The digest creation request
     * @return The digest and associated data needed to complete the signature
     */
    suspend fun createDigest(request: DigestRequest): IdkResult<DigestResponse, IdkError>

    /**
     * Two-step signing step 2: complete signature with externally-computed value.
     *
     * @param request The completion request with the externally-signed value
     * @return The completed signed output
     */
    suspend fun completeSignature(request: CompleteSignatureRequest): IdkResult<SignOutput, IdkError>

    /**
     * Which signature forms this service supports.
     * IDK: {RAW, JWS, COSE}.
     */
    fun supportedForms(): Set<SignatureForm>

    /**
     * Validate a signature against the original input.
     *
     * Verifies that [signature] is a valid signature of [signInput].input using [keyInfo].
     *
     * @param signInput The original data that was supposedly signed
     * @param signature The signature bytes to verify
     * @param keyInfo Key information for verification
     * @return true if the signature is valid, false otherwise
     */
    suspend fun validate(
        signInput: SignInput,
        signature: ByteArray,
        keyInfo: KeyInfoType<*>,
    ): IdkResult<Boolean, IdkError>

    /**
     * Whether a specific signature level is supported.
     * Default implementation checks if the level's form is in [supportedForms].
     */
    fun supportsLevel(level: SignatureLevel): Boolean = level.form in supportedForms()
}
