package com.sphereon.data.store.blob

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * HTTP method for temporary URL access.
 */
@Serializable
enum class TempUrlMethod {
    /** Download (read blob content) */
    GET,
    /** Metadata only (stat without body) */
    HEAD,
    /** Upload (presigned upload URL — write access) */
    PUT,
}

/**
 * Options for creating a temporary URL.
 *
 * IDK provides the base options. EDK's [TempUrlPolicy] can reject or adjust these
 * based on tenant policies, authorization rules, and document classification.
 *
 * @param expiresIn How long the URL is valid. Subject to policy-enforced maximums.
 * @param method HTTP method the URL grants access to.
 * @param contentDisposition Override Content-Disposition header in the response (e.g., force download filename).
 * @param contentType Override Content-Type header in the response.
 */
@Serializable
data class TempUrlOptions(
    val expiresIn: Duration = Duration.parse("1h"),
    val method: TempUrlMethod = TempUrlMethod.GET,
    val contentDisposition: String? = null,
    val contentType: String? = null,
) {
    companion object {
        val DEFAULT = TempUrlOptions()
        val SHORT = TempUrlOptions(expiresIn = Duration.parse("5m"))
        val LONG = TempUrlOptions(expiresIn = Duration.parse("24h"))
    }
}

/**
 * Result of creating a temporary URL.
 *
 * @param url The temporary URL. May be a signed URL (cloud backends), a token-bearing URL,
 *            or a proxy URL depending on the backend and policy.
 * @param expiresAt When the URL becomes invalid.
 * @param method The HTTP method this URL grants access to.
 * @param isPublic `true` if anyone with the URL can access the blob (no additional auth required).
 *                 `false` if the URL must be combined with a bearer token or other credential.
 *                 IDK backends always return `true`. EDK policies may enforce `false`.
 */
@Serializable
data class TempUrlResult(
    val url: String,
    val expiresAt: Instant,
    val method: TempUrlMethod = TempUrlMethod.GET,
    val isPublic: Boolean = true,
)

/**
 * Policy interface for controlling temporary URL creation.
 *
 * IDK provides [DefaultTempUrlPolicy] which always approves requests unchanged.
 * EDK replaces it via `@ContributesBinding(replaces = [DefaultTempUrlPolicy::class])` with:
 * - Tenant-specific maximum duration enforcement
 * - Authorization checks (can this principal create a temp URL for this blob?)
 * - Audience restrictions (URL valid only for specific services)
 * - IP range restrictions
 * - Rate limiting
 * - Audit logging of temp URL creation
 * - Document classification-based rules (e.g., confidential docs get shorter TTLs)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("TempUrlPolicy", exact = true)
interface TempUrlPolicy {

    /**
     * Evaluate whether a temporary URL can be created with the given options.
     *
     * Implementations may:
     * - Return the options unchanged (approved as-is)
     * - Return modified options (e.g., clamped duration to tenant max)
     * - Return an error (request denied by policy)
     *
     * @param ref The blob being accessed
     * @param options The requested options
     * @return Approved (possibly adjusted) options, or an error
     */
    suspend fun evaluate(
        info: BlobInfo,
        options: TempUrlOptions,
    ): IdkResult<TempUrlOptions, IdkError>
}

/**
 * Default policy that approves all temp URL requests unchanged.
 * EDK replaces this with a policy-aware implementation.
 */
class DefaultTempUrlPolicy : TempUrlPolicy {
    override suspend fun evaluate(
        info: BlobInfo,
        options: TempUrlOptions,
    ): IdkResult<TempUrlOptions, IdkError> = Ok(options)
}
