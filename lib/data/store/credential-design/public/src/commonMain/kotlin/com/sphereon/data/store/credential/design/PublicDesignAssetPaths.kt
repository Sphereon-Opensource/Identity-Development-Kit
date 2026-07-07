/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design

/**
 * Canonical public-asset path constants shared between the IDK impl layer (which builds the
 * absolute URI returned from [com.sphereon.data.store.credential.design.CredentialDesignService.uploadDesignAsset])
 * and the EDK rest hosting adapter (which mounts the public GET endpoints under the same root).
 *
 * IDK sits below the EDK rest module, so the canonical constant must live here — EDK imports IDK,
 * not the other way around.
 *
 * Asset URLs are embedded in issued credentials (VCT `rendering.simple.logo.uri` and `.well-known`
 * `display[].logo.uri`) and must remain stable across platform upgrades, hence the deliberately
 * UNVERSIONED `/public/` prefix.
 */
object PublicDesignAssetPaths {
    /**
     * Root mount for the PUBLIC, unauthenticated design-asset hosting surface.
     * Mirrors `/public/statuslists` and `/public/schema/vct`.
     */
    const val BASE_PATH = "/public/assets/design"

    /**
     * Returns the CONTENT-ADDRESSED relative path for an asset, identified solely by the
     * SHA-256 hash of its bytes, with an optional file extension derived from the content-type.
     *
     * Content addressing collapses byte-identical assets (the same logo reused across many
     * locales/designs) onto a SINGLE stable URL: a pre-fetching wallet downloads the bytes
     * exactly once and caches them, instead of re-fetching the same image under a distinct
     * per-locale URL (a bandwidth and privacy/correlation concern). Locale stays in the design
     * DISPLAY metadata and never appears in the image URL.
     *
     * The hash also yields the Subresource-Integrity (`uri#integrity`) value that SD-JWT VC /
     * OID4VCI rendering metadata support for free.
     *
     * The extension is a CLIENT HINT for CDNs/browsers that key off file extensions. It is NOT
     * part of the hash: the server recovers the hash via [hashFromLeaf] by stripping the extension,
     * then looks up `(tenant, hash)` in the blob store. Same bytes + same content-type always
     * produce the same URI.
     *
     * @param hash lowercase hex SHA-256 digest (64 chars) of the asset bytes
     * @param contentType optional MIME type; mapped to a file extension and appended when known
     * @return full relative path, e.g. `"/public/assets/design/<64-hex>.png"`
     */
    fun assetPath(
        hash: String,
        contentType: String? = null
    ): String {
        val ext = extensionForContentType(contentType)
        return if (ext != null) "$BASE_PATH/$hash.$ext" else "$BASE_PATH/$hash"
    }

    /**
     * Maps a MIME content-type string to a well-known file extension, or returns `null` for
     * unrecognised types. The mapping is intentionally small — only image formats and PDF that
     * are expected as design assets.
     *
     * Matching is case-insensitive. Any `; charset=...` (or other) parameters are stripped
     * before matching, so `"image/PNG"` and `"image/png; charset=utf-8"` both map to `"png"`.
     *
     * @param contentType raw Content-Type header value, may be null or include parameters
     * @return lowercase file extension without leading dot, or `null` if unrecognised
     */
    fun extensionForContentType(contentType: String?): String? {
        if (contentType == null) return null
        // Strip parameters (e.g. "; charset=utf-8") and normalize to lowercase.
        val mediaType = contentType.substringBefore(';').trim().lowercase()
        return when (mediaType) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/svg+xml" -> "svg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            "application/pdf" -> "pdf"
            else -> null
        }
    }

    /**
     * Recovers the bare SHA-256 hash from the leaf segment of a content-addressed asset URL.
     *
     * The leaf is the last path segment of the request URL, e.g. `"<64-hex>.png"` or simply
     * `"<64-hex>"` (no extension, backward-compatible). The hash is everything before the first
     * dot; if there is no dot the full leaf is returned unchanged.
     *
     * Used by the EDK hosting route to extract the lookup key from `{asset}` path parameters.
     *
     * @param leaf the last path segment, e.g. `"abc123.png"` or `"abc123"`
     * @return the hash portion, e.g. `"abc123"`
     */
    fun hashFromLeaf(leaf: String): String = leaf.substringBefore('.')

    /**
     * Resolves a stored design-asset [uri] to an ABSOLUTE URL using a per-request/per-tenant
     * [externalBaseUrl], so the host embedded in issued metadata matches the host the request
     * arrived on (multi-tenant gateway: `acme.example.com` vs `bob.example.com`).
     *
     * Asset URIs are stored RELATIVE (content-addressed under [BASE_PATH]) precisely so the
     * host can be supplied at SERVE time from the same per-tenant base the issuer advertises for
     * `credential_issuer` / `vct`. This keeps content-addressing intact — only the host/base
     * differs, the hash/path is untouched.
     *
     * Returns [uri] unchanged (a no-op) when:
     *  - [uri] is null or blank,
     *  - [externalBaseUrl] is null or blank,
     *  - [uri] already looks absolute (contains `"://"`), or
     *  - [uri] is not a design-asset path (does not start with [BASE_PATH]).
     *
     * @param uri the stored (relative) asset URI, e.g. `"/public/assets/design/<hash>.png"`
     * @param externalBaseUrl the per-tenant external origin, e.g. `"https://acme.example.com"`
     * @return the absolute URL, e.g. `"https://acme.example.com/public/assets/design/<hash>.png"`
     */
    fun toAbsolute(
        uri: String?,
        externalBaseUrl: String?
    ): String? {
        if (uri.isNullOrBlank()) return uri
        if (externalBaseUrl.isNullOrBlank()) return uri
        if (uri.contains("://")) return uri
        if (!uri.startsWith(BASE_PATH)) return uri
        return publicOrigin(externalBaseUrl) + uri
    }

    private fun publicOrigin(externalBaseUrl: String): String {
        val base = externalBaseUrl.trimEnd('/')
        val schemeEnd = base.indexOf("://").takeIf { it >= 0 } ?: return base
        val authorityStart = schemeEnd + 3
        val pathStart = base.indexOf('/', authorityStart)
        return if (pathStart < 0) base else base.substring(0, pathStart)
    }
}
