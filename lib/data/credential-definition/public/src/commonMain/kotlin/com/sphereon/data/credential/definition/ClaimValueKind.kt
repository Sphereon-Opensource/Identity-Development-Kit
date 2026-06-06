package com.sphereon.data.credential.definition

import kotlinx.serialization.Serializable

/**
 * The fundamental data type of a [CredentialClaim]'s value. A lean, role-neutral enum for the
 * free-form credential-definition tier, kept self-contained in the IDK layer so the definition does
 * not couple to the heavier credential-design or semantic-catalog models. The EDK profile-bound
 * resolver maps the catalog/profile value kind onto this enum when producing effective free-form
 * claims.
 */
@Serializable
enum class ClaimValueKind {
    STRING,
    NUMBER,
    BOOLEAN,

    /** Binary/blob data such as images or documents. */
    BINARY,
    DATE_TIME,
    DATE,
    TIME,

    /** A reference to another claim or external entity. */
    REFERENCE,

    /** An ordered collection of values. */
    ARRAY,

    /** A compound/object node addressed structurally via its nested [ClaimPath]. */
    OBJECT,

    /** Type could not be determined or has not been set. */
    UNKNOWN,
}
