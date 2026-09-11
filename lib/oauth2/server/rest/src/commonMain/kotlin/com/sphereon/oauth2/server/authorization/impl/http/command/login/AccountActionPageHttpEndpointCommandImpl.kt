/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.conf.theme.core.model.AssetElementValue
import com.sphereon.conf.theme.core.model.ProductType
import com.sphereon.conf.theme.core.model.ResolvedFeature
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.model.ThemeVariant
import com.sphereon.conf.theme.core.resolve.FeatureResolver
import com.sphereon.conf.theme.core.resolve.ThemeResolver
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.login.AccountActionPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.newCspNonce
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.software.registry.SoftwareInstanceRegistry
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException

/**
 * Tenant-AS landing for invitation onboarding. Token is read from the fragment so it
 * never hits access logs. Password completion is required; passkey enrollment on this
 * same origin is the operator's one identity-scoped credential.
 *
 * Branding follows the hosted login page: ThemeResolver + FeatureResolver are optional
 * and any failure falls back to the neutral page.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(AccountActionPageHttpEndpointCommand.COMMAND_ID)
class AccountActionPageHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider? = null,
    private val baseUrlResolver: OAuth2ServerBaseUrlResolver? = null,
    private val themeResolver: Provider<ThemeResolver>? = null,
    private val featureResolver: Provider<FeatureResolver>? = null,
    private val softwareInstanceRegistry: Provider<SoftwareInstanceRegistry>? = null,
) : HttpEndpointCommandAdapter(
        id = AccountActionPageHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AccountActionPageHttpEndpointCommand.ENDPOINT,
    ),
    AccountActionPageHttpEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val showWebAuthn = configProvider.serverConfig.webAuthn.enabled
        val nonce = newCspNonce()
        val tenantId =
            runCatching { execution.tenantId }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != IdentityConstants.ANONYMOUS_TENANT_ID }
        val asInstanceId = asInstanceIdProvider?.currentAsInstanceId() ?: DEFAULT_AS_INSTANCE_ID
        val theming =
            if (configProvider.serverConfig.login.themeResolutionEnabled) {
                resolveThemingOrNeutral(tenantId, asInstanceId)
            } else {
                AccountActionTheming()
            }
        val trustedBase = baseUrlResolver?.resolveBaseUrl(request, configProvider)?.trimEnd('/').orEmpty()
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = mapOf(
                    "Content-Type" to "text/html; charset=utf-8",
                    "Cache-Control" to "no-store",
                ),
                body = html(showWebAuthn, nonce, theming),
            ).withSecurityHeaders(
                ResponseCategory.HTML,
                nonce,
                imageOrigins = crossOriginImageOrigins(theming, trustedBase),
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveThemingOrNeutral(
        tenantId: String?,
        asInstanceId: String,
    ): AccountActionTheming {
        val resolverProvider = themeResolver ?: return AccountActionTheming()
        if (tenantId == null) return AccountActionTheming()
        return try {
            val applicationId = softwareInstanceRegistry?.invoke()?.get(tenantId, asInstanceId)?.partyId
            val resolver = resolverProvider.invoke()
            val light = resolver.resolve(tenant = tenantId, variant = ThemeVariant.LIGHT, applicationId = applicationId)
            val dark = resolver.resolve(tenant = tenantId, variant = ThemeVariant.DARK, applicationId = applicationId)
            val loginFeature =
                featureResolver?.invoke()?.resolve(
                    tenant = tenantId,
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = LOGIN_FEATURE_ID,
                    applicationId = applicationId,
                    variant = null,
                )
            val loginFeatureDark =
                featureResolver?.invoke()?.resolve(
                    tenant = tenantId,
                    productType = ProductType.AUTHORIZATION_SERVER,
                    featureId = LOGIN_FEATURE_ID,
                    applicationId = applicationId,
                    variant = ThemeVariant.DARK,
                )
            AccountActionTheming(light = light, dark = dark, loginFeature = loginFeature, loginFeatureDark = loginFeatureDark)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            execution.log.logManager
                .withTag("AccountActionPageHttpEndpointCommand")
                .warn("Theme resolution failed for tenant '$tenantId'; rendering the neutral account-action page: ${e.message}")
            AccountActionTheming()
        }
    }

    private fun html(
        showWebAuthn: Boolean,
        nonce: String,
        theming: AccountActionTheming,
    ): String {
        val light = branding(theming.light, theming.loginFeature, dark = false)
        val dark = branding(theming.dark, theming.loginFeatureDark ?: theming.loginFeature, dark = true)
        val title = light.appName?.takeIf { it.isNotBlank() }?.let { "Activate your $it account" } ?: "Activate your account"
        val themeStyle = themeStyleBlock(nonce, light, dark)
        val logoBlock = logoBlock(light, dark)
        return """
        <!DOCTYPE html>
        <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${escapeHtml(title)}</title>
        $themeStyle
        <style nonce="${escapeHtml(nonce)}">
          :root{--color-primary:#111;--color-surface:#fff;--color-on-surface:#111}
          body{font-family:system-ui,sans-serif;max-width:28rem;margin:4rem auto;padding:0 1.25rem;color:var(--color-on-surface);background:var(--color-surface)}
          label,button{display:block;width:100%;margin:0.75rem 0}
          input{width:100%;padding:0.5rem;box-sizing:border-box}
          button{padding:0.65rem 1rem;cursor:pointer;background:var(--color-primary);color:#fff;border:0;border-radius:0.35rem}
          [role=status]{min-height:1.25rem}
          .brand-logo img{max-height:2.5rem;width:auto}
        </style></head><body>
        $logoBlock
        <h1>${escapeHtml(title)}</h1>
        <p>This page is on your organization's sign-in host. The password and optional passkey you set here are the ones you use to sign in and to confirm wallet operations.</p>
        <form id="activation" method="post">
          <label>New password <input type="password" name="password" autocomplete="new-password" required minlength="8"></label>
          <button id="submit-activation" type="submit" disabled>${if (showWebAuthn) "Save password and enroll passkey" else "Save password and continue"}</button>
          ${if (showWebAuthn) """<p>This device will create one passkey for this identity. You use that same passkey to sign in and to confirm wallet operations.</p>""" else ""}
          <p role="status" id="status"></p>
          <button id="retry-activation" type="button" hidden>Retry activation check</button>
        </form>
        <noscript><p>JavaScript is required to verify this single-use activation link. Enable JavaScript and reload this page.</p></noscript>
        <script nonce="${escapeHtml(nonce)}">
          (function () {
            var token = (location.hash || "").replace(/^#/, "");
            var status = document.getElementById("status");
            var form = document.getElementById("activation");
            var submit = document.getElementById("submit-activation");
            var retry = document.getElementById("retry-activation");
            var webauthn = ${if (showWebAuthn) "true" else "false"};
            var activationReady;
            function resolveActivation() {
              if (!token) {
                status.textContent = "This activation link is missing its token. Open the complete link from the registration response or email.";
                retry.hidden = true;
                submit.disabled = true;
                return Promise.reject(new Error("missing-token"));
              }
              status.textContent = "Checking activation link…";
              retry.hidden = true;
              submit.disabled = true;
              activationReady = fetch("/api/account-actions/v1/resolve", {
                method: "POST",
                headers: { "content-type": "application/json" },
                body: JSON.stringify({ token: token })
              }).then(function (res) { return res.json().then(function (body) { return { ok: res.ok, body: body }; }); })
                .then(function (result) {
                  if (!result.ok) throw new Error("invalid-action");
                  status.textContent = "Activation link verified. Set the password to continue.";
                  submit.disabled = false;
                  return result;
                })
                .catch(function (error) {
                  submit.disabled = true;
                  retry.hidden = false;
                  status.textContent = "The activation service could not verify this link. Check that the link is complete and try again.";
                  throw error;
                });
              return activationReady;
            }
            retry.addEventListener("click", function () { resolveActivation().catch(function () {}); });
            resolveActivation().catch(function () {});
            function b64urlToBytes(value) {
              var pad = value.length % 4 === 0 ? "" : "=".repeat(4 - (value.length % 4));
              var bin = atob(value.replace(/-/g, "+").replace(/_/g, "/") + pad);
              var out = new Uint8Array(bin.length);
              for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
              return out;
            }
            function bytesToB64url(buffer) {
              var view = new Uint8Array(buffer);
              var bin = "";
              for (var i = 0; i < view.length; i++) bin += String.fromCharCode(view[i]);
              return btoa(bin).replace(/\\+/g, "-").replace(/\\//g, "_").replace(/=+$/g, "");
            }
            function enrollPasskey() {
              if (!webauthn || !window.PublicKeyCredential) return Promise.resolve(null);
              return fetch("/api/account-actions/v1/passkey/begin", {
                method: "POST",
                headers: { "content-type": "application/json" },
                body: JSON.stringify({ token: token, origin: location.origin, rpId: location.hostname })
              }).then(function (res) { return res.json().then(function (body) { return { ok: res.ok, body: body }; }); })
                .then(function (result) {
                  var options = result.ok && result.body && result.body.options;
                  if (!options || !options.challenge || !options.rpId || !options.challengeId || !options.userHandle) {
                    throw new Error("passkey-unavailable");
                  }
                  return navigator.credentials.create({
                    publicKey: {
                      challenge: b64urlToBytes(options.challenge),
                      rp: { id: options.rpId, name: options.rpName || options.rpId },
                      user: { id: new TextEncoder().encode(options.userHandle), name: "operator", displayName: "Operator" },
                      pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -8 }, { type: "public-key", alg: -257 }],
                      timeout: 60000,
                      attestation: "none",
                      authenticatorSelection: { authenticatorAttachment: "platform", residentKey: "preferred", userVerification: "required" }
                    }
                  }).then(function (credential) {
                    if (!credential || !credential.rawId) throw new Error("passkey-missing");
                    var response = credential.response;
                    return {
                      passkeyChallengeId: options.challengeId,
                      passkeyCredentialId: bytesToB64url(credential.rawId),
                      passkeyAttestationObject: bytesToB64url(response.attestationObject),
                      passkeyClientDataJson: bytesToB64url(response.clientDataJSON),
                      passkeyOrigin: location.origin,
                      passkeyRpId: options.rpId
                    };
                  });
                });
            }
            form.addEventListener("submit", function (event) {
              event.preventDefault();
              if (submit.disabled) return;
              status.textContent = webauthn ? "Enrolling passkey…" : "Saving…";
              activationReady.then(function () { return enrollPasskey().catch(function () { return null; }); }).then(function (passkey) {
                var body = { token: token, password: form.password.value };
                if (passkey) {
                  body.passkeyChallengeId = passkey.passkeyChallengeId;
                  body.passkeyCredentialId = passkey.passkeyCredentialId;
                  body.passkeyAttestationObject = passkey.passkeyAttestationObject;
                  body.passkeyClientDataJson = passkey.passkeyClientDataJson;
                  body.passkeyOrigin = passkey.passkeyOrigin;
                  body.passkeyRpId = passkey.passkeyRpId;
                }
                status.textContent = "Saving…";
                return fetch("/api/account-actions/v1/complete", {
                  method: "POST",
                  headers: { "content-type": "application/json" },
                  body: JSON.stringify(body)
                });
              }).then(function (res) { return res.json().then(function (body) { return { ok: res.ok, body: body }; }); })
                .then(function (result) {
                  if (!result.ok) throw new Error("refused");
                  status.textContent = webauthn
                    ? "Account activated. Sign in with this password or the passkey created on this device."
                    : "Account activated. You can now sign in with this password.";
                  form.hidden = true;
                }).catch(function () { status.textContent = "Activation did not complete. Check the password and try again."; });
            });
          })();
        </script>
        </body></html>
        """.trimIndent()
    }

    private data class AccountActionTheming(
        val light: ResolvedTheme? = null,
        val dark: ResolvedTheme? = null,
        val loginFeature: ResolvedFeature? = null,
        val loginFeatureDark: ResolvedFeature? = null,
    )

    private data class PageBranding(
        val primary: String?,
        val surface: String?,
        val onSurface: String?,
        val logoUrl: String?,
        val logoDarkUrl: String?,
        val appName: String?,
    )

    private fun branding(
        theme: ResolvedTheme?,
        feature: ResolvedFeature?,
        dark: Boolean,
    ): PageBranding {
        val logoElement = if (dark) "logoDark" else "logo"
        return PageBranding(
            primary = safeCssColor(theme?.tokens?.get("color.primary") ?: theme?.branding?.primaryColor),
            surface = safeCssColor(theme?.tokens?.get("color.surface")),
            onSurface = safeCssColor(theme?.tokens?.get("color.onSurface")),
            logoUrl = assetUri(feature, logoElement) ?: if (dark) theme?.branding?.logoDarkUrl else theme?.branding?.logoUrl,
            logoDarkUrl = assetUri(feature, "logoDark") ?: theme?.branding?.logoDarkUrl,
            appName = theme?.branding?.appName,
        )
    }

    private fun themeStyleBlock(
        nonce: String,
        light: PageBranding,
        dark: PageBranding,
    ): String {
        val lightVars = cssVariables(light)
        val darkVars = cssVariables(dark)
        if (lightVars.isEmpty() && darkVars.isEmpty()) return ""
        return buildString {
            append("<style nonce=\"").append(escapeHtml(nonce)).append("\">")
            if (lightVars.isNotEmpty()) append("body{").append(lightVars).append('}')
            if (darkVars.isNotEmpty()) {
                append("@media (prefers-color-scheme: dark){body{").append(darkVars).append("}}")
            }
            append("</style>")
        }
    }

    private fun logoBlock(light: PageBranding, dark: PageBranding): String {
        val logo = light.logoUrl ?: return ""
        val darkLogo = dark.logoUrl ?: light.logoDarkUrl ?: logo
        val alt = light.appName ?: "Organization"
        return """
            <picture class="brand-logo">
              <source media="(prefers-color-scheme: dark)" srcset="${escapeHtml(darkLogo)}">
              <img src="${escapeHtml(logo)}" alt="${escapeHtml(alt)}">
            </picture>
        """.trimIndent()
    }

    private fun cssVariables(branding: PageBranding): String =
        buildString {
            branding.primary?.let { append("--color-primary:").append(it).append(';') }
            branding.surface?.let { append("--color-surface:").append(it).append(';') }
            branding.onSurface?.let { append("--color-on-surface:").append(it).append(';') }
        }

    private fun assetUri(feature: ResolvedFeature?, elementId: String): String? =
        (feature?.elements?.get(elementId)?.value as? AssetElementValue)?.asset?.uri

    private fun safeCssColor(value: String?): String? =
        value?.trim()?.takeIf { CSS_COLOR.matches(it) }

    private fun crossOriginImageOrigins(
        theming: AccountActionTheming,
        trustedBase: String,
    ): Set<String> {
        val candidates =
            buildList {
                for (feature in listOfNotNull(theming.loginFeature, theming.loginFeatureDark)) {
                    for (elementId in THEMED_IMAGE_ELEMENT_IDS) {
                        (feature.elements[elementId]?.value as? AssetElementValue)?.asset?.uri?.let(::add)
                    }
                }
                for (theme in listOfNotNull(theming.light, theming.dark)) {
                    theme.branding?.let { branding ->
                        branding.logoUrl?.let(::add)
                        branding.logoDarkUrl?.let(::add)
                        branding.faviconUrl?.let(::add)
                    }
                }
            }
        if (candidates.isEmpty() || trustedBase.isBlank()) return emptySet()
        val baseOrigin = httpOriginOrNull(trustedBase)
        return candidates
            .mapNotNull(::httpOriginOrNull)
            .filterTo(mutableSetOf()) { it != baseOrigin }
    }

    private fun httpOriginOrNull(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val hostPort =
            url
                .substring(schemeEnd + 3)
                .takeWhile { it != '/' && it != '?' && it != '#' }
        if (hostPort.isEmpty() || !HOST_PORT_PATTERN.matches(hostPort)) return null
        return "$scheme://${hostPort.lowercase()}"
    }

    private fun escapeHtml(value: String): String =
        buildString(value.length) {
            for (char in value) {
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }

    companion object {
        private const val DEFAULT_AS_INSTANCE_ID: String = "default"
        private const val LOGIN_FEATURE_ID: String = "login"
        private val THEMED_IMAGE_ELEMENT_IDS: List<String> = listOf("logo", "logoDark", "background", "favicon")
        private val CSS_COLOR = Regex("""#[0-9A-Fa-f]{3,8}""")
        private val HOST_PORT_PATTERN = Regex("""(?:[A-Za-z0-9.-]+|\[[0-9A-Fa-f:]+])(?::\d{1,5})?""")
    }
}
