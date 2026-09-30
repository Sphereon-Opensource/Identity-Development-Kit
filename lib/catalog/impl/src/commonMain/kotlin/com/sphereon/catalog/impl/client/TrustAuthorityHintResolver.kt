/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.model.CatalogImportDiagnostic
import com.sphereon.catalog.model.TrustAuthority
import com.sphereon.catalog.model.TrustFrameworkType
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOIDFEntityIdOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.etsi.resolution.ExternalIdentifierETSITslOpts
import com.sphereon.trust.etsi.resolution.ExternalIdentifierETSITslResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn

/**
 * Decides whether a hint URL taken from a catalogue listing may be fetched. Hints come from listings that are not
 * signed, so a hint URL is attacker-controlled input. No policy is bound by default and network resolution is then
 * skipped; a binding must validate the resolved address of the host (https only, no loopback, private or link-local
 * addresses) and the fetch must not follow redirects.
 */
fun interface TrustAuthorityHintEgress {
    suspend fun permits(url: String): Boolean
}

/**
 * Resolves TS 11 `trustedAuthorities` hints through the existing external identifier services and
 * reports what happened as import diagnostics. The outcome never changes which issuers are
 * trusted; that stays with trust domains, their anchors and attachments.
 *
 * Hint mapping: `etsi_tl` and `isLOTE` go through the ETSI trust list identifier method (which
 * parses both TS 119 612 lists and TS 119 602 LoTE), `aki` goes through the X5C method when the
 * hint carries a certificate, and `openid_federation` goes through the entity id method.
 */
@Inject
@SingleIn(SessionScope::class)
class TrustAuthorityHintResolver(
    private val identifierServices: Set<ExternalIdentifierService>,
    private val egressProvider: Provider<TrustAuthorityHintEgress>? = null,
) {
    suspend fun resolve(hints: Collection<TrustAuthority>): List<CatalogImportDiagnostic> =
        hints.distinct().map { resolveOne(it) }

    private suspend fun resolveOne(hint: TrustAuthority): CatalogImportDiagnostic {
        val subject = "${hint.frameworkType.name}:${hint.value}"
        val lote = if (hint.isLOTE == true) " (LoTE)" else ""
        val opts: ExternalIdentifierOpts =
            when (hint.frameworkType) {
                TrustFrameworkType.etsi_tl -> ExternalIdentifierETSITslOpts(identifier = hint.value)
                TrustFrameworkType.openid_federation -> ExternalIdentifierOIDFEntityIdOpts(identifier = hint.value)
                TrustFrameworkType.aki ->
                    if (looksLikeCertificate(hint.value)) {
                        ExternalIdentifierX5cOpts(identifier = listOf(hint.value), verify = false)
                    } else {
                        return CatalogImportDiagnostic(
                            code = UNRESOLVED,
                            subject = subject,
                            message = "aki hint identifies a key and needs an x5c chain from a presented credential to resolve",
                        )
                    }
            }
        if (fetchesUrl(hint) && egressProvider?.invoke()?.permits(hint.value) != true) {
            return CatalogImportDiagnostic(
                code = SKIPPED,
                subject = subject,
                message = "Hint$lote was not fetched: the URL comes from an unsigned listing and no egress policy permits it",
            )
        }
        val service =
            identifierServices.sortedBy { it.order }.firstOrNull { runCatching { it.isSupportedOpts(opts) }.getOrDefault(false) }
                ?: return CatalogImportDiagnostic(
                    code = UNSUPPORTED,
                    subject = subject,
                    message = "No identifier service supports ${hint.frameworkType.name}$lote hints",
                )
        val outcome = runCatching { service.resolve(opts) }
        val result = outcome.getOrNull()
        if (result == null || result.isErr) {
            val reason = result?.takeIf { it.isErr }?.error?.message?.defaultMessage ?: outcome.exceptionOrNull()?.message ?: "resolution failed"
            return CatalogImportDiagnostic(code = UNRESOLVED, subject = subject, message = "Hint$lote did not resolve: $reason")
        }
        return CatalogImportDiagnostic(code = RESOLVED, subject = subject, message = describe(result.value, lote))
    }

    private fun describe(
        result: ExternalIdentifierResult,
        lote: String,
    ): String =
        when (result) {
            is ExternalIdentifierETSITslResult ->
                "Resolved trust list$lote with ${result.trustAnchors.size} anchors, signatureVerified=${result.signatureVerified}"
            is ExternalIdentifierResult.OIDFEntityId ->
                "Resolved entity, trustEstablished=${result.trustEstablished}, ${result.jwks.size} keys"
            else -> "Resolved ${result.method.methodName}, ${result.jwks.size} keys"
        }

    private fun fetchesUrl(hint: TrustAuthority): Boolean = hint.frameworkType != TrustFrameworkType.aki

    private fun looksLikeCertificate(value: String): Boolean =
        value.length > 200 && value.startsWith("MI") && value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }

    companion object {
        const val RESOLVED = "catalog.trust-authority.resolved"
        const val UNRESOLVED = "catalog.trust-authority.unresolved"
        const val UNSUPPORTED = "catalog.trust-authority.unsupported"
        const val SKIPPED = "catalog.trust-authority.skipped"
    }
}
