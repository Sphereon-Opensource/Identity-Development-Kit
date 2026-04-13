/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.oauth2.client.model

import com.sphereon.oauth2.common.model.PkceMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PKCE data container (RFC 7636)
 *
 * Contains the code verifier, code challenge, and code challenge method
 * used in the OAuth 2.0 authorization code flow with PKCE.
 */
@Serializable
data class PkceData(
    /**
     * Code verifier - high-entropy cryptographic random string
     * Must be 43-128 characters long using [A-Z], [a-z], [0-9], "-", ".", "_", "~"
     */
    @SerialName("code_verifier")
    val codeVerifier: String,
    /**
     * Code challenge - derived from code verifier
     * For S256: BASE64URL(SHA256(ASCII(code_verifier)))
     * For plain: code_verifier
     */
    @SerialName("code_challenge")
    val codeChallenge: String,
    /**
     * Code challenge method - transformation applied to code verifier
     */
    @SerialName("code_challenge_method")
    val codeChallengeMethod: PkceMethod = PkceMethod.S256,
)
