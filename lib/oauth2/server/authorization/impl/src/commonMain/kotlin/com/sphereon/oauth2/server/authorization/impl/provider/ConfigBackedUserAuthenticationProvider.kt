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
        warnOnceAboutDevTestUsage()
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
        val usernamePassword = credentials as? UserCredentials.UsernamePassword ?: return Ok(null)
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
        return Ok(sub)
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

    override suspend fun isAuthenticationMethodAvailable(method: AuthenticationMethod,): IdkResult<Boolean, AuthenticationError> = Ok(method == AuthenticationMethod.PASSWORD)

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

    private fun warnOnceAboutDevTestUsage() {
        if (devTestWarningEmitted.compareAndSet(expect = false, update = true)) {
            // LogService isn't injected here to keep the lazy initializer free of suspend setup;
            // the warning needs to fire once per JVM regardless of session lifecycle.
            println(
                "WARN ConfigBackedUserAuthenticationProvider is active. IDK config-backed user storage " +
                    "is intended for development, conformance testing, and demos only, not for production. " +
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
        const val ACCOUNT_SUB_LEAF: String = "sub"
        const val ACCOUNT_EMAIL_LEAF: String = "email"
        const val ACCOUNT_EMAIL_VERIFIED_LEAF: String = "email-verified"
        const val ACCOUNT_CLAIMS_LEAF: String = "claims"

        const val DEFAULT_ITERATIONS: Int = 210_000

        private val devTestWarningEmitted = atomic(false)

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
