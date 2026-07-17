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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.service.Amr
import com.sphereon.core.api.service.AuthAssuranceLevel
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigBackedUserAuthenticationProviderTest {
    private val deploymentSaltB64 = "SGVsbG9TYWx0Rm9yVGVzdHMxMjM0NTY3ODkw"
    private val deploymentSalt = deploymentSaltB64.decodeFromBase64()
    private val iterations = 1_000

    @Test
    fun authenticatesWithCorrectPassword() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val aliceHash = hasher.hash("alice", "alice-password")

            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.password" to aliceHash,
                        "oauth2.users.accounts.alice.sub" to "alice-sub",
                    ),
                )

            val result =
                provider.authenticateWithCredentials(
                    UserCredentials.UsernamePassword(username = "alice", password = "alice-password"),
                )
            assertTrue(result.isOk)
            assertEquals("alice-sub", result.value)
        }

    @Test
    fun fallsBackToUsernameWhenSubMissing() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val aliceHash = hasher.hash("alice", "alice-password")

            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.password" to aliceHash,
                    ),
                )

            val result =
                provider.authenticateWithCredentials(
                    UserCredentials.UsernamePassword(username = "alice", password = "alice-password"),
                )
            assertTrue(result.isOk)
            assertEquals("alice", result.value)
        }

    @Test
    fun rejectsIncorrectPassword() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val aliceHash = hasher.hash("alice", "alice-password")

            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.password" to aliceHash,
                    ),
                )

            val result =
                provider.authenticateWithCredentials(
                    UserCredentials.UsernamePassword(username = "alice", password = "wrong"),
                )
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun unknownUserReturnsNull() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                    ),
                )

            val result =
                provider.authenticateWithCredentials(
                    UserCredentials.UsernamePassword(username = "nobody", password = "anything"),
                )
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun nonUsernamePasswordCredentialsReturnNull() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                    ),
                )

            val result = provider.authenticateWithCredentials(UserCredentials.BearerToken("token"))
            assertTrue(result.isOk)
            assertNull(result.value)
        }

    @Test
    fun configBackedWebAuthnFailsClosedWithoutCryptographicVerifier() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.sub" to "alice-sub",
                        "oauth2.users.accounts.alice.${ConfigBackedUserAuthenticationProvider.ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF}" to
                            "credential-a, credential-b",
                    ) + webAuthnPolicyConfig(),
                )

            val result =
                provider.authenticateUserWithCredentials(
                    UserCredentials.WebAuthnAssertion(
                        credentialId = "credential-b",
                        challengeId = "challenge-1",
                        authenticatorData = "authenticator-data",
                        clientDataJson = "client-data-json",
                        signature = "signature",
                        userIdHint = "alice-sub",
                        origin = "https://login.example",
                        rpId = "login.example",
                        userVerified = true,
                        transport = "internal",
                        backupEligible = true,
                        backupState = true,
                        prfCapable = true,
                        assertionEvidenceRef = "passkey-evidence-1",
                    ),
                )

            assertTrue(result.isErr)
        }

    @Test
    fun configBackedWebAuthnDoesNotEnumerateConfiguredCredentials() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.sub" to "alice-sub",
                        "oauth2.users.accounts.alice.${ConfigBackedUserAuthenticationProvider.ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF}" to
                            "credential-a",
                    ) + webAuthnPolicyConfig(),
                )

            val result =
                provider.authenticateUserWithCredentials(
                    UserCredentials.WebAuthnAssertion(
                        credentialId = "credential-x",
                        challengeId = "challenge-1",
                        authenticatorData = "authenticator-data",
                        clientDataJson = "client-data-json",
                        signature = "signature",
                        origin = "https://login.example",
                        rpId = "login.example",
                        userVerified = true,
                        transport = "internal",
                        backupEligible = true,
                        backupState = true,
                        assertionEvidenceRef = "passkey-evidence-1",
                    ),
                )

            assertTrue(result.isErr)
        }

    @Test
    fun webAuthnAvailabilityIsFalseForConfigBackedProvider() =
        runTest {
            val withoutPasskey =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.sub" to "alice-sub",
                    ) + webAuthnPolicyConfig(),
                )
            assertFalse(withoutPasskey.isAuthenticationMethodAvailable(AuthenticationMethod.WEBAUTHN).value)

            val withPasskey =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.sub" to "alice-sub",
                        "oauth2.users.accounts.alice.${ConfigBackedUserAuthenticationProvider.ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF}" to
                            "credential-a",
                    ) + webAuthnPolicyConfig(),
                )
            assertFalse(withPasskey.isAuthenticationMethodAvailable(AuthenticationMethod.WEBAUTHN).value)
        }

    @Test
    fun webAuthnAuthenticationFailsClosedWhenConfigMissingOrInvalid() =
        runTest {
            val accountConfig =
                mapOf(
                    ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                    ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                    "oauth2.users.accounts.alice.sub" to "alice-sub",
                    "oauth2.users.accounts.alice.${ConfigBackedUserAuthenticationProvider.ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF}" to "credential-a",
                )
            val missing = newProvider(accountConfig)
            assertFalse(missing.isAuthenticationMethodAvailable(AuthenticationMethod.WEBAUTHN).value)
            assertTrue(missing.authenticateUserWithCredentials(validWebAuthnAssertion()).isErr)

            val invalid = newProvider(accountConfig + webAuthnPolicyConfig(rpId = "https://login.example"))
            assertFalse(invalid.isAuthenticationMethodAvailable(AuthenticationMethod.WEBAUTHN).value)
            assertTrue(invalid.authenticateUserWithCredentials(validWebAuthnAssertion()).isErr)
        }

    @Test
    fun webAuthnAuthenticationRejectsRpOriginUvTransportBackupAndPrfPolicyMismatches() =
        runTest {
            val base =
                mapOf(
                    ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                    ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                    "oauth2.users.accounts.alice.sub" to "alice-sub",
                    "oauth2.users.accounts.alice.${ConfigBackedUserAuthenticationProvider.ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF}" to "credential-a",
                )
            val provider = newProvider(base + webAuthnPolicyConfig(prfEnabled = false))

            assertTrue(provider.authenticateUserWithCredentials(validWebAuthnAssertion(rpId = "other.example")).isErr)
            assertTrue(provider.authenticateUserWithCredentials(validWebAuthnAssertion(origin = "https://evil.example")).isErr)
            assertTrue(provider.authenticateUserWithCredentials(validWebAuthnAssertion(userVerified = false)).isErr)
            assertTrue(provider.authenticateUserWithCredentials(validWebAuthnAssertion(transport = "nfc")).isErr)
            assertTrue(provider.authenticateUserWithCredentials(validWebAuthnAssertion(backupEligible = false)).isErr)
            assertTrue(provider.authenticateUserWithCredentials(validWebAuthnAssertion(prfCapable = true)).isErr)
            assertTrue(
                provider.authenticateUserWithCredentials(
                    validWebAuthnAssertion().copy(assertionEvidenceRef = null),
                ).isErr,
            )
        }

    @Test
    fun getUserInfoReturnsClaims() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.password" to "ignored-here",
                        "oauth2.users.accounts.alice.sub" to "alice-sub",
                        "oauth2.users.accounts.alice.email" to "alice@example.com",
                        "oauth2.users.accounts.alice.email-verified" to "true",
                        "oauth2.users.accounts.alice.claims.given_name" to "Alice",
                        "oauth2.users.accounts.alice.claims.family_name" to "Smith",
                    ),
                )

            val result = provider.getUserInfo("alice-sub")
            assertTrue(result.isOk)
            val info = result.value
            assertEquals("alice-sub", info.userId)
            assertEquals("alice", info.username)
            assertEquals("alice@example.com", info.email)
            assertEquals(true, info.emailVerified)
            assertEquals("Alice", info.attributes["given_name"])
            assertEquals("Smith", info.attributes["family_name"])
        }

    @Test
    fun getUserInfoResolvesByAccountKeyWhenSubAbsent() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.alice.password" to "ignored-here",
                        "oauth2.users.accounts.alice.email" to "alice@example.com",
                    ),
                )

            val result = provider.getUserInfo("alice")
            assertTrue(result.isOk)
            assertEquals("alice", result.value.userId)
            assertEquals("alice", result.value.username)
            assertEquals("alice@example.com", result.value.email)
        }

    @Test
    fun authenticatesWithEmailUsername() =
        runTest {
            val hasher = PasswordHasher(deploymentSalt, iterations)
            val email = "employee@acme.example"
            val emailHash = hasher.hash(email, "Demo1234!")

            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.[$email].password" to emailHash,
                        "oauth2.users.accounts.[$email].sub" to "employee-sub",
                    ),
                )

            val ok =
                provider.authenticateWithCredentials(
                    UserCredentials.UsernamePassword(username = email, password = "Demo1234!"),
                )
            assertTrue(ok.isOk)
            assertEquals("employee-sub", ok.value)

            val wrong =
                provider.authenticateWithCredentials(
                    UserCredentials.UsernamePassword(username = email, password = "nope"),
                )
            assertTrue(wrong.isOk)
            assertNull(wrong.value)
        }

    @Test
    fun getUserInfoReturnsClaimsForEmailUsername() =
        runTest {
            val email = "employee@acme.example"
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.[$email].password" to "ignored-here",
                        "oauth2.users.accounts.[$email].sub" to "employee-sub",
                        "oauth2.users.accounts.[$email].email" to email,
                        "oauth2.users.accounts.[$email].email-verified" to "true",
                        "oauth2.users.accounts.[$email].claims.[given_name]" to "Anneke",
                        "oauth2.users.accounts.[$email].claims.[job_title]" to "Senior Engineer",
                    ),
                )

            val result = provider.getUserInfo("employee-sub")
            assertTrue(result.isOk)
            val info = result.value
            assertEquals("employee-sub", info.userId)
            assertEquals(email, info.username)
            assertEquals(email, info.email)
            assertEquals(true, info.emailVerified)
            assertEquals("Anneke", info.attributes["given_name"])
            assertEquals("Senior Engineer", info.attributes["job_title"])
        }

    @Test
    fun getUserInfoResolvesEmailAccountByKeyWhenSubAbsent() =
        runTest {
            val email = "employee@acme.example"
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                        "oauth2.users.accounts.[$email].password" to "ignored-here",
                        "oauth2.users.accounts.[$email].email" to email,
                    ),
                )

            val result = provider.getUserInfo(email)
            assertTrue(result.isOk)
            assertEquals(email, result.value.userId)
            assertEquals(email, result.value.username)
            assertEquals(email, result.value.email)
        }

    @Test
    fun getUserInfoUnknownUserReturnsErr() =
        runTest {
            val provider =
                newProvider(
                    mapOf(
                        ConfigBackedUserAuthenticationProvider.SALT_KEY to deploymentSaltB64,
                        ConfigBackedUserAuthenticationProvider.ITERATIONS_KEY to iterations.toString(),
                    ),
                )

            val result = provider.getUserInfo("ghost")
            assertTrue(result.isErr)
        }

    private fun newProvider(properties: Map<String, String>): ConfigBackedUserAuthenticationProvider =
        ConfigBackedUserAuthenticationProvider(
            configService = FakePrincipalConfigService(properties),
        )

    private fun webAuthnPolicyConfig(
        rpId: String = "login.example",
        allowedOrigins: String = "https://login.example",
        prfEnabled: Boolean = true,
    ): Map<String, String> =
        mapOf(
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_RP_ID_KEY to rpId,
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_ALLOWED_ORIGINS_KEY to allowedOrigins,
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_ATTESTATION_POLICY_KEY to "none",
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_USER_VERIFICATION_KEY to "required",
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_ALLOWED_TRANSPORTS_KEY to "internal,usb",
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_BACKUP_STATE_POLICY_KEY to "require-backup-eligible",
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_CHALLENGE_TTL_SECONDS_KEY to "300",
            ConfigBackedUserAuthenticationProvider.WEBAUTHN_LEVEL3_PRF_ENABLED_KEY to prfEnabled.toString(),
        )

    private fun validWebAuthnAssertion(
        rpId: String = "login.example",
        origin: String = "https://login.example",
        userVerified: Boolean = true,
        transport: String = "internal",
        backupEligible: Boolean = true,
        backupState: Boolean = true,
        prfCapable: Boolean = false,
    ): UserCredentials.WebAuthnAssertion =
        UserCredentials.WebAuthnAssertion(
            credentialId = "credential-a",
            challengeId = "challenge-1",
            authenticatorData = "authenticator-data",
            clientDataJson = "client-data-json",
            signature = "signature",
            userIdHint = "alice-sub",
            origin = origin,
            rpId = rpId,
            userVerified = userVerified,
            transport = transport,
            backupEligible = backupEligible,
            backupState = backupState,
            prfCapable = prfCapable,
            assertionEvidenceRef = "passkey-evidence-1",
        )
}

/**
 * Minimal in-test fake that satisfies [PrincipalConfigService] for the property reads
 * [ConfigBackedUserAuthenticationProvider] performs (`getPropertyAsString`, `getSubPropertiesAsString`).
 * Other methods throw to guard against unintended scope creep in the provider under test.
 */
private class FakePrincipalConfigService(
    private val properties: Map<String, String>,
) : PrincipalConfigService {
    override val parent: TenantConfigService
        get() = error("parent not used in this test")

    override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

    override fun addPropertySource(source: PropertySource<*>): Nothing = error("not used")

    override fun removePropertySource(source: PropertySource<*>): Nothing = error("not used")

    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "test-app"

    override fun getConfigLocation(): Path = error("not used")

    override fun getPropertySources(includeParents: Boolean): PropertySources = error("not used")

    @Suppress("DEPRECATION")
    override fun getNamespace(): String = ""

    override fun containsProperty(key: String): Boolean = properties.containsKey(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        val value = properties[key] ?: return defaultValue
        return targetType.cast(value)
    }

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = properties[key] ?: defaultValue

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T =
        getProperty(key, targetType, defaultValue)
            ?: error("Missing required property $key")

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String =
        getPropertyAsString(key, defaultValue)
            ?: error("Missing required property $key")

    override fun getAllProperties(): Map<String, Any> = properties.toMap()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = computeSubProperties(prefixes, stripPrefix)

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = computeSubProperties(prefixes, stripPrefix)

    private fun computeSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, String> {
        val matched = mutableMapOf<String, String>()
        for (prefix in prefixes) {
            for ((key, value) in properties) {
                val matches = key.startsWith("$prefix.") || key == prefix
                if (!matches) continue
                val outKey = if (stripPrefix) key.removePrefix("$prefix.") else key
                matched[outKey] = value
            }
        }
        return matched
    }
}
