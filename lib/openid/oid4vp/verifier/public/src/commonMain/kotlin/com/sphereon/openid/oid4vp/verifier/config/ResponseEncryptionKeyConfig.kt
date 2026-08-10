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
 */

package com.sphereon.openid.oid4vp.verifier.config

/**
 * The verifier's response-encryption key, resolved server side.
 *
 * `direct_post.jwt` publishes the public half of this key in `client_metadata.jwks` at
 * authorization-request creation and performs the ECDH key agreement with the private half when the
 * wallet posts the encrypted response. Both ends ask by verifier instance only: the key is selected
 * from the deployment's own binding for the active tenant and that instance, so no alias, provider
 * id, revision, or key id reaches the selection from a caller, a configuration property, or a stored
 * session.
 *
 * The key must already exist. A verifier that finds none refuses the encrypted response mode rather
 * than minting one, and every reason a binding cannot be honoured ends at the same refusal so the
 * outcome carries no information about which verifiers hold which key material.
 */
interface ResponseEncryptionKeyConfig {
    /** Key name for [verifierInstanceId], or null to refuse the encrypted response mode. */
    suspend fun resolveEncryptionKeyName(verifierInstanceId: String): String?

    companion object {
        /**
         * Single refusal for every reason the response-encryption key cannot be used: no seam bound,
         * no binding, a detached, cross-tenant, inactive, or unmapped one, and a key that is absent
         * from the KMS. One message keeps the refusal from becoming a discovery oracle.
         */
        const val RESPONSE_ENCRYPTION_KEY_UNAVAILABLE = "The verifier response encryption key is unavailable"
    }
}
