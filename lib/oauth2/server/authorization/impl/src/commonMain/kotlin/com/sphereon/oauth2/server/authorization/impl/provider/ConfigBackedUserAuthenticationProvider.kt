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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.log.Log
import com.sphereon.core.api.service.Amr
import com.sphereon.core.api.service.AuthAssuranceLevel
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationContext
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.AuthenticationMethod
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.provider.UserCredentials
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding
import kotlinx.atomicfu.atomic
import kotlin.time.Clock

/**
 * Multibinding key for [ConfigBackedUserAuthenticationProvider]. EDK's `EdkUserAuthenticationProvider`
 * already owns `"local"` for tenant-aware DB-backed auth; the IDK config-backed provider takes
 * `"local-config"` so a deployment that picks `oauth2.user-provider.mode = local-config` cannot
 * silently swap to the DB provider when EDK is also on the classpath.
 */
const val CONFIG_BACKED_USER_AUTH_PROVIDER_KEY: String = "local-config"

/**
 * IDK config-backed [UserAuthenticationProvider] for development, OIDF conformance harness runs,
 * and demos. User accounts and the deployment-wide pepper live in plain config; passwords are
 * stored as PBKDF2-HMAC-SHA256 hashes (OWASP 2024 floor: 210,000 iterations) keyed off
 * `(deploymentSalt, username)`. See [PasswordHasher] for the exact derivation.
 *
 * Map-bound under [CONFIG_BACKED_USER_AUTH_PROVIDER_KEY] (`"local-config"`).
 *
 * Config schema:
 * ```
 * oauth2.users.password.salt        = (base64 deployment-wide pepper)
 * oauth2.users.password.iterations  = 210000
 *
 * oauth2.users.accounts.alice.password         = (base64 PBKDF2 hash)
 * oauth2.users.accounts.alice.sub              = alice
 * oauth2.users.accounts.alice.webauthn.credential-ids = passkey-credential-id-1,passkey-credential-id-2
 * oauth2.users.accounts.alice.email            = alice@example.com
 * oauth2.users.accounts.alice.email-verified   = true
 * oauth2.users.accounts.alice.claims.given_name  = Alice
 * oauth2.users.accounts.alice.claims.family_name = Smith
 * ```
 *
 * Claim leaf names (`given_name`, `family_name`, ...) keep snake_case to match OIDC §5.1 wire
 * format. The IDK config keys around them are kebab-case per the IDK config naming convention.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<UserAuthenticationProvider>())
@StringKey(CONFIG_BACKED_USER_AUTH_PROVIDER_KEY)
class ConfigBackedUserAuthenticationProvider(
    private val configService: PrincipalConfigService,
) : UserAuthenticationProvider {
    private val hasher: PasswordHasher by lazy {
        warnOnceAboutNonProductionUsage()
        val saltB64 =
            configService.getPropertyAsString(SALT_KEY)
                ?: error(
                    "Missing required config: $SALT_KEY. " +
                        "Set a deployment-wide base64 pepper before enabling " +
                        "oauth2.user-provider.mode = $CONFIG_BACKED_USER_AUTH_PROVIDER_KEY.",
                )
        val iterations =
            configService
                .getPropertyAsString(ITERATIONS_KEY)
                ?.toIntOrNull()
                ?: DEFAULT_ITERATIONS
        PasswordHasher(
            deploymentSalt = saltB64.decodeFromBase64(),
            iterations = iterations,
        )
    }

    override suspend fun getAuthenticatedUser(sessionId: String,): IdkResult<AuthenticatedUser?, AuthenticationError> = Ok(null)

    override suspend fun initiateAuthentication(
        sessionId: String,
        returnUrl: String,
        hint: AuthenticationHint?,
        context: AuthenticationContext?,
    ): IdkResult<String, AuthenticationError> =
        Err(
            AuthenticationError.Generic(
                description =
                    "ConfigBackedUserAuthenticationProvider does not initiate redirect-based authentication; " +
                        "use direct credential authentication via the AS login form.",
            ),
        )

    override suspend fun authenticateWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<String?, AuthenticationError> {
        val result = authenticateUserWithCredentials(credentials, context)
        return result.fold(
            success = { authenticatedUser -> Ok(authenticatedUser?.userId) },
            failure = { Err(it) },
        )
    }

    override suspend fun authenticateUserWithCredentials(
        credentials: UserCredentials,
        context: AuthenticationContext?,
    ): IdkResult<AuthenticatedUser?, AuthenticationError> =
        when (credentials) {
            is UserCredentials.UsernamePassword -> authenticateUsernamePassword(credentials)
            is UserCredentials.WebAuthnAssertion -> authenticateWebAuthnAssertion(credentials)
            else -> Ok(null)
        }

    private suspend fun authenticateUsernamePassword(usernamePassword: UserCredentials.UsernamePassword): IdkResult<AuthenticatedUser?, AuthenticationError> {
        val username = usernamePassword.username
        val storedHash =
            configService.getPropertyAsString(accountKey(username, ACCOUNT_PASSWORD_LEAF))
                ?: return Ok(null)
        val matched =
            hasher.verify(
                username = username,
                password = usernamePassword.password,
                expectedHashB64 = storedHash,
            )
        if (!matched) return Ok(null)
        val sub = configService.getPropertyAsString(accountKey(username, ACCOUNT_SUB_LEAF)) ?: username
        return Ok(
            AuthenticatedUser(
                userId = sub,
                authenticatedAt = Clock.System.now(),
                authenticationMethod = AuthenticationMethod.PASSWORD,
                acr = AuthAssuranceLevel.AAL1.acr,
                amr = listOf(Amr.PWD),
            ),
        )
    }

    private fun authenticateWebAuthnAssertion(assertion: UserCredentials.WebAuthnAssertion): IdkResult<AuthenticatedUser?, AuthenticationError> {
        if (assertion.credentialId.isBlank()) {
            return Err(AuthenticationError.InvalidCredentials(description = "Invalid WebAuthn assertion"))
        }
        return Err(
            AuthenticationError.MethodUnavailable(
                method = AuthenticationMethod.WEBAUTHN,
                description = "Config-backed WebAuthn authentication requires the EDK cryptographic passkey verifier",
            ),
        )
    }

    override suspend fun logout(userId: String): IdkResult<Unit, AuthenticationError> = Ok(Unit)

    override suspend fun getUserInfo(userId: String): IdkResult<UserInfo, AuthenticationError> {
        val username =
            resolveUsername(userId)
                ?: return Err(AuthenticationError.UserNotFound(description = "User $userId not found"))

        // Same trailing-dot avoidance as [listConfiguredAccounts]: the resolver re-appends a
        // boundary `.` itself, so passing `oauth2.users.accounts.alice.claims` works here while
        // `oauth2.users.accounts.alice.claims.` would match nothing. The username is bracket-quoted
        // when it contains characters the property-key normalizer would otherwise mangle (e.g. the
        // dots in an email address), so an email username stays one literal config segment.
        val claimsRoot = "${ACCOUNTS_PREFIX_NO_DOT}.${encodeAccountSegment(username)}.$ACCOUNT_CLAIMS_LEAF"
        // YAML claim names use bracket-quoting (`"[given_name]": Test`) to keep underscored
        // identifiers from being mangled into dotted paths by the property-key normalizer
        // (`given_name` → `given.name`). The brackets are an in-config marker only; strip
        // them here so downstream consumers — and the OidcScopeClaimsMapper which matches
        // standard OIDC claim names exactly (`given_name`, `family_name`, …) — see the
        // wire-shape claim names.
        val claims: Map<String, Any> =
            configService
                .getSubPropertiesAsString(prefixes = setOf(claimsRoot), stripPrefix = true, redact = false)
                .mapKeys { (k, _) -> k.removeSurrounding("[", "]") }
                .mapValues { it.value }

        return Ok(
            UserInfo(
                userId = userId,
                username = username,
                email = configService.getPropertyAsString(accountKey(username, ACCOUNT_EMAIL_LEAF)),
                emailVerified =
                    configService
                        .getPropertyAsString(accountKey(username, ACCOUNT_EMAIL_VERIFIED_LEAF))
                        ?.toBooleanStrictOrNull(),
                attributes = claims,
            ),
        )
    }

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod,): IdkResult<Boolean, AuthenticationError> =
        Ok(
            when (method) {
                AuthenticationMethod.PASSWORD -> true
                AuthenticationMethod.WEBAUTHN -> false
                else -> false
            },
        )

    /**
     * Resolve a configured account name for the given userId. Accepts either the literal account
     * key (config path segment) or a `sub` value pointing at an account.
     */
    private fun resolveUsername(userId: String): String? {
        val directHit = configService.getPropertyAsString(accountKey(userId, ACCOUNT_SUB_LEAF))
        if (directHit != null) return userId

        val allAccounts = listConfiguredAccounts()
        // Implicit fallback: when no `sub` is configured for an account, the account name itself
        // is the sub. This means `userId == accountName` resolves even without an explicit sub.
        if (userId in allAccounts &&
            configService.getPropertyAsString(accountKey(userId, ACCOUNT_SUB_LEAF)) == null
        ) {
            return userId
        }
        for (account in allAccounts) {
            val sub = configService.getPropertyAsString(accountKey(account, ACCOUNT_SUB_LEAF))
            if (sub == userId) return account
        }
        return null
    }

    private fun findAccountByWebAuthnCredential(assertion: UserCredentials.WebAuthnAssertion): String? {
        val hintedAccounts =
            listOfNotNull(assertion.userIdHint, assertion.userHandle)
                .mapNotNull { resolveUsername(it) }
                .distinct()
        for (account in hintedAccounts) {
            if (assertion.credentialId in configuredWebAuthnCredentialIds(account)) return account
        }
        return listConfiguredAccounts().firstOrNull { account ->
            assertion.credentialId in configuredWebAuthnCredentialIds(account)
        }
    }

    private fun configuredWebAuthnCredentialIds(username: String): Set<String> =
        configService
            .getPropertyAsString(accountKey(username, ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF))
            ?.split(',')
            ?.mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
            ?: emptySet()

    private fun configuredWebAuthnPolicy(): ConfigBackedWebAuthnPolicy? {
        val rpId = configService.getPropertyAsString(WEBAUTHN_RP_ID_KEY)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val allowedOrigins =
            configService
                .getPropertyAsString(WEBAUTHN_ALLOWED_ORIGINS_KEY)
                ?.split(',')
                ?.mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        val userVerification =
            configService
                .getPropertyAsString(WEBAUTHN_USER_VERIFICATION_KEY, "required")
                ?.trim()
                ?.lowercase()
                ?: "required"
        if (userVerification !in setOf("required", "preferred", "discouraged")) return null
        val attestationPolicy =
            configService
                .getPropertyAsString(WEBAUTHN_ATTESTATION_POLICY_KEY, "none")
                ?.trim()
                ?.lowercase()
                ?: "none"
        if (attestationPolicy !in setOf("none", "indirect", "direct", "enterprise")) return null
        val allowedTransports =
            configService
                .getPropertyAsString(WEBAUTHN_ALLOWED_TRANSPORTS_KEY)
                ?.split(',')
                ?.mapNotNullTo(mutableSetOf()) { it.trim().takeIf(String::isNotEmpty) }
                ?: emptySet()
        val backupPolicy =
            configService
                .getPropertyAsString(WEBAUTHN_BACKUP_STATE_POLICY_KEY, "allow-any")
                ?.trim()
                ?.lowercase()
                ?: "allow-any"
        if (backupPolicy !in setOf("allow-any", "require-backup-eligible", "require-backed-up", "forbid-backed-up")) return null
        val challengeTtlSeconds =
            configService
                .getPropertyAsString(WEBAUTHN_CHALLENGE_TTL_SECONDS_KEY, "300")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: return null
        val prfEnabled =
            configService
                .getPropertyAsString(WEBAUTHN_LEVEL3_PRF_ENABLED_KEY, "false")
                ?.toBooleanStrictOrNull()
                ?: false
        if (rpId.startsWith("http://") || rpId.startsWith("https://") || rpId.contains('/')) return null
        if (allowedOrigins.any { !it.startsWith("https://") && !it.startsWith("http://localhost") && !it.startsWith("http://127.0.0.1") }) return null
        return ConfigBackedWebAuthnPolicy(
            rpId = rpId,
            allowedOrigins = allowedOrigins,
            userVerification = userVerification,
            attestationPolicy = attestationPolicy,
            allowedTransports = allowedTransports,
            backupPolicy = backupPolicy,
            challengeTtlSeconds = challengeTtlSeconds,
            level3PrfEnabled = prfEnabled,
        )
    }

    /**
     * Discover configured account names by scanning property keys under `oauth2.users.accounts`.
     * Returns the unique first segment after the prefix for every property under the account
     * subtree (`password`, `sub`, `email`, `claims.*`, ...).
     *
     * The prefix passed to [PrincipalConfigService.getSubPropertiesAsString] does NOT carry a
     * trailing dot: the resolver normalises every prefix and re-appends `.` for the boundary
     * check, so a trailing `.` here would yield `..` and match nothing.
     */
    private fun listConfiguredAccounts(): Set<String> {
        val sub =
            configService.getSubPropertiesAsString(
                prefixes = setOf(ACCOUNTS_PREFIX_NO_DOT),
                stripPrefix = true,
                redact = false,
            )
        return sub.keys.mapNotNullTo(mutableSetOf()) { key ->
            decodeAccountSegment(key).takeIf { it.isNotEmpty() }
        }
    }

    private fun warnOnceAboutNonProductionUsage() {
        if (nonProductionWarningEmitted.compareAndSet(expect = false, update = true)) {
            Log.app().withTag("ConfigBackedUserAuthenticationProvider").warn(
                "WARN ConfigBackedUserAuthenticationProvider is active. IDK config-backed user storage " +
                    "is intended for development, conformance, and demos only, not for production. " +
                    "For production deployments, contribute a database-backed UserAuthenticationProvider through EDK or VDX.",
            )
        }
    }

    companion object {
        const val SALT_KEY: String = "oauth2.users.password.salt"
        const val ITERATIONS_KEY: String = "oauth2.users.password.iterations"

        const val ACCOUNTS_PREFIX: String = "oauth2.users.accounts."

        /**
         * Identical to [ACCOUNTS_PREFIX] but without the trailing dot. Required because the
         * underlying [PrincipalConfigService] resolver re-appends `.` itself when checking
         * prefix boundaries; passing the dot-suffixed form yields `..` and matches no keys.
         */
        private const val ACCOUNTS_PREFIX_NO_DOT: String = "oauth2.users.accounts"

        const val ACCOUNT_PASSWORD_LEAF: String = "password"
        const val ACCOUNT_WEBAUTHN_CREDENTIAL_IDS_LEAF: String = "webauthn.credential-ids"
        const val ACCOUNT_SUB_LEAF: String = "sub"
        const val ACCOUNT_EMAIL_LEAF: String = "email"
        const val ACCOUNT_EMAIL_VERIFIED_LEAF: String = "email-verified"
        const val ACCOUNT_CLAIMS_LEAF: String = "claims"

        const val WEBAUTHN_RP_ID_KEY: String = "oauth2.users.webauthn.rp-id"
        const val WEBAUTHN_ALLOWED_ORIGINS_KEY: String = "oauth2.users.webauthn.allowed-origins"
        const val WEBAUTHN_ATTESTATION_POLICY_KEY: String = "oauth2.users.webauthn.attestation-policy"
        const val WEBAUTHN_USER_VERIFICATION_KEY: String = "oauth2.users.webauthn.user-verification"
        const val WEBAUTHN_ALLOWED_TRANSPORTS_KEY: String = "oauth2.users.webauthn.allowed-transports"
        const val WEBAUTHN_BACKUP_STATE_POLICY_KEY: String = "oauth2.users.webauthn.backup-state-policy"
        const val WEBAUTHN_CHALLENGE_TTL_SECONDS_KEY: String = "oauth2.users.webauthn.challenge-ttl-seconds"
        const val WEBAUTHN_LEVEL3_PRF_ENABLED_KEY: String = "oauth2.users.webauthn.level3.prf-enabled"

        const val DEFAULT_ITERATIONS: Int = 210_000

        private val nonProductionWarningEmitted = atomic(false)

        /**
         * Characters that the IDK property-key normalizer treats specially: it lowercases uppercase
         * letters and collapses spaces/underscores/hyphens/dots into the path delimiter. A username
         * containing any of these (notably the dots in an email address) must be bracket-quoted so
         * the resolver keeps it as one literal segment instead of splitting it into a sub-path.
         */
        private fun usernameNeedsBracketing(username: String): Boolean =
            username.any { ch ->
                ch == '.' || ch == '_' || ch == '-' || ch == ' ' || ch.isUpperCase()
            }

        /**
         * Encode a username into a single config-path segment. Bracket-quotes the username when it
         * would otherwise be mangled by the property-key normalizer (e.g. `employee@acme.example`
         * → `[employee@acme.example]`); leaves simple identifiers (e.g. `alice`) untouched so legacy
         * unbracketed account config keeps resolving.
         */
        fun encodeAccountSegment(username: String): String = if (usernameNeedsBracketing(username)) "[$username]" else username

        /**
         * Recover the literal account name from the first path segment returned by sub-property
         * discovery. Handles both the bracket-quoted form (`[employee@acme.example].password`,
         * where the dots inside the brackets survive normalization) and the legacy unbracketed
         * simple form (`alice.password`).
         */
        internal fun decodeAccountSegment(strippedKey: String): String =
            if (strippedKey.startsWith("[")) {
                val close = strippedKey.indexOf(']')
                if (close > 0) strippedKey.substring(1, close) else strippedKey
            } else {
                strippedKey.substringBefore('.', missingDelimiterValue = strippedKey)
            }

        private fun accountPrefix(username: String): String = "$ACCOUNTS_PREFIX${encodeAccountSegment(username)}."

        private fun accountKey(
            username: String,
            leaf: String,
        ): String = "${accountPrefix(username)}$leaf"
    }
}

private data class ConfigBackedWebAuthnPolicy(
    val rpId: String,
    val allowedOrigins: Set<String>,
    val userVerification: String,
    val attestationPolicy: String,
    val allowedTransports: Set<String>,
    val backupPolicy: String,
    val challengeTtlSeconds: Long,
    val level3PrfEnabled: Boolean,
) {
    fun validate(assertion: UserCredentials.WebAuthnAssertion): Result<Unit> {
        if (assertion.assertionEvidenceRef.isNullOrBlank()) return Result.failure(IllegalArgumentException("Missing verified WebAuthn assertion evidence"))
        if (assertion.rpId != rpId) return Result.failure(IllegalArgumentException("WebAuthn RP ID mismatch"))
        val origin = assertion.origin ?: return Result.failure(IllegalArgumentException("Missing WebAuthn origin"))
        if (origin !in allowedOrigins) return Result.failure(IllegalArgumentException("WebAuthn origin mismatch"))
        if (userVerification == "required" && assertion.userVerified != true) {
            return Result.failure(IllegalArgumentException("WebAuthn user verification is required"))
        }
        val transport = assertion.transport
        if (allowedTransports.isNotEmpty() && transport != null && transport !in allowedTransports) {
            return Result.failure(IllegalArgumentException("WebAuthn transport is not allowed"))
        }
        when (backupPolicy) {
            "require-backup-eligible" ->
                if (assertion.backupEligible != true) return Result.failure(IllegalArgumentException("WebAuthn backup eligibility is required"))
            "require-backed-up" ->
                if (assertion.backupState != true) return Result.failure(IllegalArgumentException("WebAuthn backed-up state is required"))
            "forbid-backed-up" ->
                if (assertion.backupState == true) return Result.failure(IllegalArgumentException("WebAuthn backed-up state is not allowed"))
        }
        if (assertion.prfCapable && !level3PrfEnabled) {
            return Result.failure(IllegalArgumentException("WebAuthn PRF capability is not enabled"))
        }
        return Result.success(Unit)
    }
}
