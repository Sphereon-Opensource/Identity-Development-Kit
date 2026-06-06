package com.sphereon.data.credential.definition

import kotlinx.serialization.Serializable

/**
 * A role-neutral claim within a [CredentialDefinition]: the same shape both an issuer and a verifier
 * consume downstream. A claim is structure-aware via its [path]: nested claims (e.g.
 * `address.postal_code`) are encoded by the dotted path, mirroring how DCQL and SD-JWT address
 * nested claims, so the claim itself carries no child tree.
 *
 * Because a free-form claim has no profile to inherit display text from, [labels] is REQUIRED and
 * must be non-empty (enforced in [init]).
 *
 * @property path the fully-qualified claim path; may be a single segment (`given_name`) or nested
 *   (`address.postal_code`).
 * @property valueKind the claim's data type.
 * @property labels a non-empty locale -> label map (e.g. `{"en": "Postal code"}`). Required because
 *   a free-form claim has no profile to inherit display text from.
 */
@Serializable
data class CredentialClaim(
    val path: ClaimPath,
    val valueKind: ClaimValueKind,
    val labels: Map<String, String>,
) {
    init {
        require(path.isWellFormed) {
            "A CredentialClaim.path must be a non-blank, well-formed single-or-nested path; got: " +
                "'${path.value}'"
        }
        require(labels.isNotEmpty()) {
            "A CredentialClaim.labels map must be non-empty for claim '${path.value}'; a free-form " +
                "claim has no profile to inherit display text from"
        }
        require(labels.keys.all { it.isNotBlank() } && labels.values.all { it.isNotBlank() }) {
            "A CredentialClaim.labels map must not contain blank locales or blank labels for claim " +
                "'${path.value}'"
        }
    }
}
