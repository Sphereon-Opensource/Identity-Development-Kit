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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.statuslist.StatusListDefinitionsProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Singular, single-issuer [Oid4vciIssuerConfigProvider] backed by IDK's ConfigService.
 *
 * Pins the config namespace to the fixed [NAMESPACE] (`oid4vci.issuer`), preserving the pure-IDK
 * config-only single-issuer deploy. All read logic lives in [AbstractConfigOid4vciIssuerConfigProvider];
 * this subclass only fixes the namespace.
 *
 * Per-instance multi-issuer routing (`oid4vci.issuers.<id>.*` selected at request time) is provided
 * by [RegistryBackedOid4vciIssuerConfigProvider], which replaces this binding when on the classpath.
 *
 * Reads issuer metadata and credential configurations from principal config, allowing
 * tenant/app overrides. Credential configuration IDs are declared explicitly via a
 * comma-separated property rather than discovered by prefix scan.
 *
 * Example configuration (application.yml):
 * ```yaml
 * sphereon:
 *   oid4vci:
 *     issuer:
 *       identifier: https://issuer.example.com
 *       baseUrl: https://issuer.example.com
 *       authorizationServers: https://as.example.com
 *       credentialConfigurationIds: UniversityDegree,MembershipCard
 *       display:
 *         name: Example University
 *         locale: en-US
 *       batchSize: 5
 *       credentials:
 *         UniversityDegree:
 *           format: jwt_vc_json
 *           scope: degree
 *           signingAlgorithms: ES256,ES384
 *           bindingMethods: jwk,did:key
 *           proofTypes: jwt,cwt   # optional explicit list; otherwise discovered from sub-keys
 *             jwt:
 *               signingAlgorithms: ES256
 *             cwt:
 *               signingAlgorithms: ES256
 *           credentialDefinition:
 *             types: VerifiableCredential,UniversityDegreeCredential
 *           display:
 *             name: University Degree
 *             locale: en-US
 * ```
 *
 * Or via environment variables:
 * ```
 * SPHEREON_OID4VCI_ISSUER_IDENTIFIER=https://issuer.example.com
 * SPHEREON_OID4VCI_ISSUER_AUTHORIZATIONSERVERS=https://as.example.com
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALCONFIGURATIONIDS=UniversityDegree,MembershipCard
 * SPHEREON_OID4VCI_ISSUER_DISPLAY_NAME=Example University
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_FORMAT=jwt_vc_json
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SCOPE=degree
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SIGNINGALGORITHMS=ES256,ES384
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_BINDINGMETHODS=jwk,did:key
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_PROOFTYPES_JWT_SIGNINGALGORITHMS=ES256
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_CREDENTIALDEFINITION_TYPES=VerifiableCredential,UniversityDegreeCredential
 * SPHEREON_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_DISPLAY_NAME=University Degree
 * ```
 *
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuerConfigProvider>())
@ContributesBinding(SessionScope::class, binding = binding<VctTypeMetadataProvider>())
class ConfigDrivenOid4vciIssuerConfigProvider(
    execution: SessionExecution,
    statusListDefinitionsProvider: Provider<StatusListDefinitionsProvider>? = null,
) : AbstractConfigOid4vciIssuerConfigProvider(
        execution = execution,
        statusListDefinitionsProvider = statusListDefinitionsProvider,
        namespaceProvider = { NAMESPACE },
    ) {
    companion object {
        /** Root config namespace for all singular-issuer metadata properties. */
        const val NAMESPACE = "oid4vci.issuer"
    }
}
